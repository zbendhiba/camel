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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadRuntimeException;
import org.apache.camel.component.langchain4j.tools.spec.CamelToolExecutorCache;
import org.apache.camel.component.langchain4j.tools.spec.CamelToolSpecification;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.langchain4j.agent.LangChain4jAgent.Headers.MEMORY_ID;
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

        AiAgentBody aiAgentBody = processBody(messagePayload, exchange);

        // get chatMemory if specified
        ChatMemoryProvider chatMemoryProvider = endpoint.getConfiguration().getChatMemoryProvider();
        if (chatMemoryProvider != null) {
            ObjectHelper.notNull(aiAgentBody.getMemoryId(), "memoryId");
        }

        // Content retriever for naive RAG
        ContentRetriever contentRetriever = endpoint.getConfiguration().getContentRetriever();

        // Create AI Service with discovered tools for this exchange
        String tags = endpoint.getConfiguration().getTags();

        // Let AI Service handle everything (chat + tools + memoryId)
        String response = "";
        if (chatMemoryProvider != null) {
            AiAgentWithMemoryService agentService = createAiAgentWithMemoryService(tags, chatMemoryProvider, contentRetriever, exchange);
            response = aiAgentBody.getSystemMessage() != null
                    ? agentService.chat(aiAgentBody.getMemoryId(), aiAgentBody.getUserMessage(), aiAgentBody.getSystemMessage())
                    : agentService.chat(aiAgentBody.getMemoryId(), aiAgentBody.getUserMessage());
        } else {
            AiAgentService agentService = createAiAgentService(tags, contentRetriever, exchange);
            response = aiAgentBody.getSystemMessage() != null
                    ? agentService.chat(aiAgentBody.getUserMessage(), aiAgentBody.getSystemMessage())
                    : agentService.chat(aiAgentBody.getUserMessage());

        }
        exchange.getMessage().setBody(response);
    }

    private AiAgentBody processBody(Object messagePayload, Exchange exchange) {
        if (messagePayload instanceof AiAgentBody) {
            return (AiAgentBody) messagePayload;
        }

        if (!(messagePayload instanceof String)) {
            throw new InvalidPayloadRuntimeException(exchange, AiAgentBody.class);
        }

        String systemMessage = exchange.getIn().getHeader(SYSTEM_MESSAGE, String.class);
        Object memoryId = exchange.getIn().getHeader(MEMORY_ID);

        AiAgentBody aiAgentBody = new AiAgentBody((String) messagePayload, systemMessage, memoryId);
        return aiAgentBody;

    }

    /**
     * Create AI service with a single universal tool that handles multiple Camel routes
     */
    private AiAgentService createAiAgentService(String tags, ContentRetriever contentRetriever, Exchange exchange) {
        ToolProvider toolProvider = getToolProvider(tags, exchange);

        var builder = AiServices.builder(AiAgentService.class)
                .chatModel(chatModel);
        if (toolProvider != null) {
            builder.toolProvider(toolProvider);
        }
        if(contentRetriever != null) {
            builder.contentRetriever(contentRetriever);
        }
        return builder.build();

    }

    /**
     * Create AI service with a single universal tool that handles multiple Camel routes and Memory Provider
     */
    private AiAgentWithMemoryService createAiAgentWithMemoryService(
            String tags, ChatMemoryProvider chatMemoryProvider, ContentRetriever contentRetriever, Exchange exchange) {
        ToolProvider toolProvider = getToolProvider(tags, exchange);

        var builder = AiServices.builder(AiAgentWithMemoryService.class)
                .chatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider);
        if (toolProvider != null) {
            builder.toolProvider(toolProvider);
        }
        if(contentRetriever != null) {
            builder.contentRetriever(contentRetriever);
        }
        return builder.build();

    }

    private ToolProvider getToolProvider(String tags, Exchange exchange) {
        ToolProvider toolProvider = null;
        if (tags != null && !tags.trim().isEmpty()) {
            // Discover tools from Camel routes
            Map<String, CamelToolSpecification> availableTools = discoverToolsByName(tags);

            if (!availableTools.isEmpty()) {
                LOG.debug("Creating AI Service with {} tools for tags: {}", availableTools.size(), tags);

                // Create dynamic tool provider that returns Camel route tools
                toolProvider = createCamelToolProvider(availableTools, exchange);

            } else {
                LOG.debug("No tools found for tags: {}, using simple AI Service", tags);
            }
        }
        return toolProvider;
    }

    /**
     * Create a dynamic tool provider that returns all Camel route tools. This uses LangChain4j's ToolProvider API for
     * dynamic tool registration.
     */
    private ToolProvider createCamelToolProvider(Map<String, CamelToolSpecification> availableTools, Exchange exchange) {
        return (ToolProviderRequest toolProviderRequest) -> {
            // Build the tool provider result with all available Camel tools
            ToolProviderResult.Builder resultBuilder = ToolProviderResult.builder();

            for (Map.Entry<String, CamelToolSpecification> entry : availableTools.entrySet()) {
                String toolName = entry.getKey();
                CamelToolSpecification camelToolSpec = entry.getValue();

                // Get the existing ToolSpecification from CamelToolSpecification
                ToolSpecification toolSpecification = camelToolSpec.getToolSpecification();

                // Create a functional tool executor for this specific Camel route
                ToolExecutor toolExecutor = (toolExecutionRequest, memoryId) -> {
                    LOG.info("Executing Camel route tool: '{}' with arguments: {}", toolName, toolExecutionRequest.arguments());

                    try {
                        // Parse JSON arguments if provided
                        String arguments = toolExecutionRequest.arguments();
                        if (arguments != null && !arguments.trim().isEmpty()) {
                            JsonNode jsonNode = objectMapper.readValue(arguments, JsonNode.class);
                            jsonNode.fieldNames()
                                    .forEachRemaining(name -> exchange.getMessage().setHeader(name, jsonNode.get(name)));
                        }

                        // Set the tool name as a header for route identification
                        exchange.getMessage().setHeader("CamelToolName", toolName);

                        // Execute the consumer route
                        camelToolSpec.getConsumer().getProcessor().process(exchange);

                        // Return the result
                        String result = exchange.getIn().getBody(String.class);
                        LOG.info("Tool '{}' execution completed successfully", toolName);
                        return result != null ? result : "No result";

                    } catch (Exception e) {
                        LOG.error("Error executing tool '{}': {}", toolName, e.getMessage(), e);
                        return String.format("Error executing tool '%s': %s", toolName, e.getMessage());
                    }
                };

                // Add this tool to the result
                resultBuilder.add(toolSpecification, toolExecutor);

                LOG.info("Added dynamic tool: '{}' - {}", toolSpecification.name(), toolSpecification.description());
            }

            return resultBuilder.build();
        };
    }

    /**
     * Discover Camel routes by tags and create a map of tool specifications by name
     */
    private Map<String, CamelToolSpecification> discoverToolsByName(String tags) {
        Map<String, CamelToolSpecification> toolsByName = new HashMap<>();

        final CamelToolExecutorCache toolCache = CamelToolExecutorCache.getInstance();
        final Map<String, Set<CamelToolSpecification>> tools = toolCache.getTools();
        String[] tagArray = ToolsTagsHelper.splitTags(tags);

        for (var entry : tools.entrySet()) {
            for (String tag : tagArray) {
                if (entry.getKey().equals(tag)) {
                    for (CamelToolSpecification camelToolSpec : entry.getValue()) {
                        String toolName = camelToolSpec.getToolSpecification().name();
                        toolsByName.put(toolName, camelToolSpec);
                    }
                }
            }
        }

        LOG.info("Discovered {} unique tools for tags: {}", toolsByName.size(), tags);
        return toolsByName;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.chatModel = this.endpoint.getConfiguration().getChatModel();
        ObjectHelper.notNull(chatModel, "chatModel");
    }
}
