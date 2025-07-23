/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.langchain4j.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.component.langchain4j.tools.LangChain4jTools;
import org.apache.camel.component.langchain4j.tools.spec.CamelToolExecutorCache;
import org.apache.camel.component.langchain4j.tools.spec.CamelToolSpecification;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.langchain4j.agent.LangChain4jAgent.Headers.SYSTEM_MESSAGE;

public class LangChain4jAgentProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(LangChain4jAgentProducer.class);

    private final LangChain4jAgentEndpoint endpoint;

    private ChatModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LangChain4jAgentProducer(LangChain4jAgentEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Object messagePayload = exchange.getIn().getBody();
        ObjectHelper.notNull(messagePayload, "body");

        // Check if we should use tools based on endpoint configuration or tags in exchange headers
        String tagsToUse = getTags(exchange);
        
        if (tagsToUse != null && !tagsToUse.trim().isEmpty()) {
            // Handle tool-enabled chat
            processWithTools(exchange, messagePayload, tagsToUse);
        } else {
            // Handle regular chat without tools
            processWithoutTools(exchange, messagePayload);
        }
    }

    private String getTags(Exchange exchange) {
        // Priority: exchange header > component configuration
        String tags = exchange.getIn().getHeader("CamelLangChain4jAgentTags", String.class);
        if (tags == null || tags.trim().isEmpty()) {
            tags = endpoint.getConfiguration().getTags();
        }
        return tags;
    }

    private void processWithTools(Exchange exchange, Object messagePayload, String tags) throws Exception {
        List<ChatMessage> messages = convertToMessages(exchange, messagePayload);
        String response = chatWithTools(messages, tags, exchange);
        exchange.getIn().setBody(response);
    }

    private void processWithoutTools(Exchange exchange, Object messagePayload) throws Exception {
        List<ChatMessage> messages = convertToMessages(exchange, messagePayload);
        String response = chatWithoutTools(messages);
        exchange.getIn().setBody(response);
    }

    /**
     * Convert input payload to ChatMessage list
     */
    private List<ChatMessage> convertToMessages(Exchange exchange, Object messagePayload) throws InvalidPayloadException {
        if (messagePayload instanceof String userMessage) {
            // String payload is treated as user message
            List<ChatMessage> messages = new ArrayList<>();
            // System message can optionally be provided via header
            String systemMessage = exchange.getIn().getHeader(SYSTEM_MESSAGE, String.class);
            if (systemMessage != null && !systemMessage.trim().isEmpty()) {
                messages.add(new SystemMessage(systemMessage));
            }
            messages.add(new UserMessage(userMessage));
            return messages;
        } else if (messagePayload instanceof AiAgentBody aiAgentBody) {
            // Use AiAgentBody's messages
            return aiAgentBody.getMessages();
        } else {
            throw new InvalidPayloadException(exchange, AiAgentBody.class);
        }
    }

    /**
     * Chat without tools using ChatMessages and legacy AiServices interface
     */
    private String chatWithoutTools(List<ChatMessage> messages) {
        // Create AI service proxy for chat interactions
        AiAgentService aiAgentService = AiServices.create(AiAgentService.class, chatModel);

        // Extract messages for legacy AiServices interface
        String extractedUserMessage = null;
        String extractedSystemMessage = null;
        
        for (ChatMessage msg : messages) {
            if (msg instanceof UserMessage userMsg) {
                extractedUserMessage = userMsg.singleText();
            } else if (msg instanceof SystemMessage systemMsg) {
                extractedSystemMessage = systemMsg.text();
            }
        }
        
        if (extractedSystemMessage != null && extractedUserMessage != null) {
            return aiAgentService.chat(extractedUserMessage, extractedSystemMessage);
        } else if (extractedUserMessage != null) {
            return aiAgentService.chat(extractedUserMessage);
        } else {
            throw new IllegalArgumentException("No user message found in ChatMessage list");
        }
    }

    /**
     * Chat with tools using the specified tags to discover available tools
     */
    private String chatWithTools(List<ChatMessage> chatMessages, String tags, Exchange exchange) {
        final CamelToolExecutorCache toolCache = CamelToolExecutorCache.getInstance();
        final ToolPair toolPair = computeCandidates(toolCache, tags, exchange);
        
        if (toolPair == null) {
            LOG.debug("No tools found for tags: {}", tags);
            return chatWithoutToolsDirectly(chatMessages);
        }

        // First talk to the model to get the tools to be called
        int iteration = 0;
        do {
            LOG.debug("Starting tool iteration {}", iteration);
            final Response<AiMessage> response = chatWithLLM(chatMessages, toolPair, exchange);
            if (isDoneExecuting(response)) {
                return extractAiResponse(response);
            }

            // Only invoke the tools ... the response will be computed on the next loop
            invokeTools(chatMessages, exchange, response, toolPair);
            LOG.debug("Finished tool iteration {}", iteration);
            iteration++;
        } while (iteration < 10); // Prevent infinite loops

        // Fallback if we hit the iteration limit
        return extractAiResponse(chatWithLLM(chatMessages, null, exchange));
    }

    /**
     * Direct chat without tools when none are available
     */
    private String chatWithoutToolsDirectly(List<ChatMessage> chatMessages) {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(chatMessages)
                .build();
        
        ChatResponse chatResponse = this.chatModel.chat(chatRequest);
        AiMessage aiMessage = chatResponse.aiMessage();
        return aiMessage.text();
    }

    private boolean isDoneExecuting(Response<AiMessage> response) {
        if (!response.content().hasToolExecutionRequests()) {
            LOG.debug("Finished executing tools because there are no more execution requests");
            return true;
        }

        if (response.finishReason() != null) {
            LOG.debug("Finished executing tools because of {}", response.finishReason());
            if (response.finishReason() == FinishReason.STOP) {
                return true;
            }
        }

        return false;
    }

    private void invokeTools(
            List<ChatMessage> chatMessages, Exchange exchange, Response<AiMessage> response, ToolPair toolPair) {
        List<ToolExecutionRequest> toolExecutionRequests = response.content().toolExecutionRequests();
        for (int i = 0; i < toolExecutionRequests.size(); i++) {
            ToolExecutionRequest toolExecutionRequest = toolExecutionRequests.get(i);
            String toolName = toolExecutionRequest.name();
            LOG.info("Invoking tool {} ({}) of {}", i, toolName, toolExecutionRequests.size());

            final CamelToolSpecification camelToolSpecification = toolPair.callableTools().stream()
                    .filter(c -> c.getToolSpecification().name().equals(toolName))
                    .findFirst()
                    .orElse(null);

            if (camelToolSpecification == null) {
                LOG.warn("Tool {} not found in available tools", toolName);
                continue;
            }

            try {
                // Map Json to Header
                JsonNode jsonNode = objectMapper.readValue(toolExecutionRequest.arguments(), JsonNode.class);
                jsonNode.fieldNames()
                        .forEachRemaining(name -> exchange.getMessage().setHeader(name, jsonNode.get(name)));

                // Execute the consumer route
                camelToolSpecification.getConsumer().getProcessor().process(exchange);
            } catch (Exception e) {
                LOG.error("Error executing tool {}: {}", toolName, e.getMessage(), e);
                exchange.setException(e);
            }

            // Add the tool execution result to the chat messages
            chatMessages.add(new ToolExecutionResultMessage(
                    toolExecutionRequest.id(),
                    toolExecutionRequest.name(),
                    exchange.getIn().getBody(String.class)));
        }
    }

    private Response<AiMessage> chatWithLLM(List<ChatMessage> chatMessages, ToolPair toolPair, Exchange exchange) {
        ChatRequest.Builder requestBuilder = ChatRequest.builder()
                .messages(chatMessages);

        // Add tools if available
        if (toolPair != null && toolPair.toolSpecifications() != null) {
            requestBuilder.toolSpecifications(toolPair.toolSpecifications());
        }

        // build request
        ChatRequest chatRequest = requestBuilder.build();

        // generate response
        ChatResponse chatResponse = this.chatModel.chat(chatRequest);

        // Convert ChatResponse to Response<AiMessage> for compatibility
        AiMessage aiMessage = chatResponse.aiMessage();
        Response<AiMessage> response = Response.from(aiMessage);

        if (!response.content().hasToolExecutionRequests()) {
            exchange.getMessage().setHeader(LangChain4jTools.NO_TOOLS_CALLED_HEADER, Boolean.TRUE);
            return response;
        }

        chatMessages.add(response.content());
        return response;
    }

    private ToolPair computeCandidates(CamelToolExecutorCache toolCache, String tags, Exchange exchange) {
        final List<ToolSpecification> toolSpecifications = new ArrayList<>();
        final List<CamelToolSpecification> callableTools = new ArrayList<>();

        final Map<String, Set<CamelToolSpecification>> tools = toolCache.getTools();
        String[] tagArray = TagsHelper.splitTags(tags);
        
        for (var entry : tools.entrySet()) {
            if (isMatch(tagArray, entry)) {
                final List<CamelToolSpecification> callablesForTag = entry.getValue().stream().toList();
                callableTools.addAll(callablesForTag);

                final List<ToolSpecification> toolsForTag = entry.getValue().stream()
                        .map(spec -> spec.getToolSpecification())
                        .toList();
                toolSpecifications.addAll(toolsForTag);
            }
        }

        if (toolSpecifications.isEmpty()) {
            exchange.getMessage().setHeader(LangChain4jTools.NO_TOOLS_CALLED_HEADER, Boolean.TRUE);
            return null;
        }

        return new ToolPair(toolSpecifications, callableTools);
    }

    private boolean isMatch(String[] tags, Map.Entry<String, Set<CamelToolSpecification>> entry) {
        for (String tag : tags) {
            if (entry.getKey().equals(tag)) {
                return true;
            }
        }
        return false;
    }

    private String extractAiResponse(Response<AiMessage> response) {
        AiMessage message = response.content();
        return message == null ? null : message.text();
    }

    /**
     * Record to hold tool specifications and callable tools
     */
    private record ToolPair(List<ToolSpecification> toolSpecifications, List<CamelToolSpecification> callableTools) {
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.chatModel = this.endpoint.getConfiguration().getChatModel();
        ObjectHelper.notNull(chatModel, "chatModel");
    }

}
