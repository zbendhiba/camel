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
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
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

        // Convert input to ChatMessage list
        List<ChatMessage> messages = convertToMessages(exchange, messagePayload);
        
        // Create AI Service with discovered tools for this exchange
        AiAgentService agentService = createAiAgentServiceWithTools(exchange);
        
        // Let AI Service handle everything (chat + tools)
        String response = agentService.chat(messages);
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
     * Create AI Agent Service with tools discovered from Camel routes
     */
    private AiAgentService createAiAgentServiceWithTools(Exchange exchange) {
        String tags = endpoint.getConfiguration().getTags();
        
        if (tags != null && !tags.trim().isEmpty()) {
            // Discover tools from Camel routes
            List<Object> toolInstances = discoverAndCreateToolInstances(tags, exchange);
            
            if (!toolInstances.isEmpty()) {
                LOG.debug("Creating AI Service with {} tools for tags: {}", toolInstances.size(), tags);
                
                // Use AiServices.builder() with discovered tool instances
                return AiServices.builder(AiAgentService.class)
                    .chatModel(chatModel)
                    .tools(toolInstances)
                    .build();
            } else {
                LOG.debug("No tools found for tags: {}, using simple AI Service", tags);
            }
        }
        
        // Use simple AI Service when no tags or no tools found
        return AiServices.create(AiAgentService.class, chatModel);
    }

    /**
     * Discover Camel routes by tags and create tool instances for AI Services
     */
    private List<Object> discoverAndCreateToolInstances(String tags, Exchange exchange) {
        List<Object> toolInstances = new ArrayList<>();
        
        final CamelToolExecutorCache toolCache = CamelToolExecutorCache.getInstance();
        final Map<String, Set<CamelToolSpecification>> tools = toolCache.getTools();
        String[] tagArray = ToolsTagsHelper.splitTags(tags);
        
        for (var entry : tools.entrySet()) {
            for (String tag : tagArray) {
                if (entry.getKey().equals(tag)) {
                    for (CamelToolSpecification camelToolSpec : entry.getValue()) {
                        // Create tool wrapper for this Camel route
                        CamelRouteToolWrapper toolWrapper = new CamelRouteToolWrapper(
                            camelToolSpec, exchange, objectMapper);
                        toolInstances.add(toolWrapper);
                        
                        LOG.debug("Created tool wrapper for: {} - {}", 
                            camelToolSpec.getToolSpecification().name(),
                            camelToolSpec.getToolSpecification().description());
                    }
                }
            }
        }
        
        LOG.info("Discovered {} tool instances for tags: {}", toolInstances.size(), tags);
        return toolInstances;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.chatModel = this.endpoint.getConfiguration().getChatModel();
        ObjectHelper.notNull(chatModel, "chatModel");
    }

    /**
     * Tool wrapper that converts a discovered Camel route into a tool that AI Services can use.
     * Each instance represents one Camel route with its actual description and parameters.
     */
    public static class CamelRouteToolWrapper {
        private final CamelToolSpecification camelToolSpec;
        private final Exchange exchange;
        private final ObjectMapper objectMapper;
        private static final Logger LOG = LoggerFactory.getLogger(CamelRouteToolWrapper.class);

        public CamelRouteToolWrapper(CamelToolSpecification camelToolSpec, Exchange exchange, ObjectMapper objectMapper) {
            this.camelToolSpec = camelToolSpec;
            this.exchange = exchange;
            this.objectMapper = objectMapper;
        }

        /**
         * This @Tool method will be discovered by AI Services.
         * The description is generic but the tool name and behavior come from the actual Camel route.
         */
        @Tool("Execute Camel route tool with parameters from route configuration")
        public String executeCamelRoute(String arguments) {
            ToolSpecification toolSpec = camelToolSpec.getToolSpecification();
            String toolName = toolSpec.name();
            
            LOG.info("Executing Camel tool: {} with arguments: {}", toolName, arguments);
            
            try {
                // Parse arguments and set as headers
                if (arguments != null && !arguments.trim().isEmpty()) {
                    JsonNode jsonNode = objectMapper.readValue(arguments, JsonNode.class);
                    jsonNode.fieldNames()
                            .forEachRemaining(name -> exchange.getMessage().setHeader(name, jsonNode.get(name)));
                }
                
                // Execute the consumer route
                camelToolSpec.getConsumer().getProcessor().process(exchange);
                
                // Return the result
                String result = exchange.getIn().getBody(String.class);
                LOG.debug("Tool {} execution result: {}", toolName, result);
                return result != null ? result : "No result";
                
            } catch (Exception e) {
                LOG.error("Error executing tool {}: {}", toolName, e.getMessage(), e);
                return "Error executing tool " + toolName + ": " + e.getMessage();
            }
        }

        /**
         * Get tool metadata for debugging
         */
        public String getToolName() {
            return camelToolSpec.getToolSpecification().name();
        }

        public String getDescription() {
            return camelToolSpec.getToolSpecification().description();
        }

        @Override
        public String toString() {
            ToolSpecification spec = camelToolSpec.getToolSpecification();
            return String.format("CamelTool[%s: %s]", spec.name(), spec.description());
        }
    }
}
