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
import java.util.Collection;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

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
        String tags = endpoint.getConfiguration().getTags();
        AiAgentService agentService = createAiAgentServiceWithTools(tags, exchange);

        // Let AI Service handle everything (chat + tools)
        String response = agentService.chat(messages);
        exchange.getMessage().setBody(response);
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
     * Create AI service with a single universal tool that handles multiple Camel routes
     */
    private AiAgentService createAiAgentServiceWithTools(String tags, Exchange exchange) {
        if (tags != null && !tags.trim().isEmpty()) {
            // Discover tools from Camel routes
            Map<String, CamelToolSpecification> availableTools = discoverToolsByName(tags);

            if (!availableTools.isEmpty()) {
                LOG.debug("Creating AI Service with {} tools for tags: {}", availableTools.size(), tags);

                // Create single universal executor that handles all tools by name
                UniversalCamelToolExecutor toolExecutor = new UniversalCamelToolExecutor(availableTools, exchange, objectMapper);
                
                // Log available tool specifications for reference
                logAvailableTools(availableTools);
                
                // Create AI agent service with the universal tool executor
                return AiServices.builder(AiAgentService.class)
                        .chatModel(chatModel)
                        .tools(toolExecutor)
                        .build();
            } else {
                LOG.debug("No tools found for tags: {}, using simple AI Service", tags);
            }
        }

        // Use simple AI Service when no tags or no tools found
        return AiServices.create(AiAgentService.class, chatModel);
    }

    /**
     * Universal tool executor that handles all Camel route tools by name.
     * Uses available tools specifications to inform LLM about individual tools.
     */
    public static class UniversalCamelToolExecutor {
        private final Map<String, CamelToolSpecification> toolsByName;
        private final Exchange exchange;
        private final ObjectMapper objectMapper;
        private final String availableToolsDescription;
        private static final Logger LOG = LoggerFactory.getLogger(UniversalCamelToolExecutor.class);

        public UniversalCamelToolExecutor(Map<String, CamelToolSpecification> toolsByName, Exchange exchange, ObjectMapper objectMapper) {
            this.toolsByName = toolsByName;
            this.exchange = exchange;
            this.objectMapper = objectMapper;
            this.availableToolsDescription = buildToolsDescription(toolsByName);
        }

        /**
         * Build a description of all available tools for the LLM
         */
        private String buildToolsDescription(Map<String, CamelToolSpecification> toolsByName) {
            StringBuilder description = new StringBuilder("Execute Camel route tools. Available tools:\n");
            for (Map.Entry<String, CamelToolSpecification> entry : toolsByName.entrySet()) {
                ToolSpecification spec = entry.getValue().getToolSpecification();
                description.append("- ").append(spec.name()).append(": ").append(spec.description());
                if (spec.parameters() != null && !spec.parameters().properties().isEmpty()) {
                    description.append(" (Parameters: ").append(String.join(", ", spec.parameters().properties().keySet())).append(")");
                }
                description.append("\n");
            }
            description.append("Use the toolName parameter to specify which tool to execute.");
            return description.toString();
        }

        @Tool("Execute a Camel route tool by name. First parameter 'toolName' specifies which tool to execute - use the exact name of available tools. Second parameter 'arguments' contains the tool arguments as JSON.")
        public String executeTool(String toolName, String arguments) {
            LOG.info("Executing tool: '{}' with arguments: {}", toolName, arguments);
            
            // Validate tool name
            if (toolName == null || toolName.trim().isEmpty()) {
                return "Error: toolName parameter is required. Available tools: " + String.join(", ", toolsByName.keySet());
            }
            
            // Map tool name to Camel route
            CamelToolSpecification camelToolSpec = toolsByName.get(toolName.trim());
            if (camelToolSpec == null) {
                String errorMsg = String.format("Unknown tool: '%s'. Available tools: %s", 
                    toolName, String.join(", ", toolsByName.keySet()));
                LOG.error(errorMsg);
                return errorMsg;
            }
            
            return executeToolRoute(toolName, arguments, camelToolSpec);
        }

        /**
         * Execute the specific Camel route for the named tool
         */
        private String executeToolRoute(String toolName, String arguments, CamelToolSpecification camelToolSpec) {
            ToolSpecification toolSpec = camelToolSpec.getToolSpecification();
            String toolDescription = toolSpec.description();
            
            LOG.info("Executing Camel route for tool: '{}' ({})", toolName, toolDescription);
            
            try {
                // Parse arguments and set as headers for the Camel route
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
                LOG.debug("Tool '{}' result: {}", toolName, result);
                return result != null ? result : "No result";

            } catch (Exception e) {
                LOG.error("Error executing tool '{}': {}", toolName, e.getMessage(), e);
                return String.format("Error executing tool '%s': %s", toolName, e.getMessage());
            }
        }
    }

    /**
     * Log available tools for debugging and reference
     */
    private void logAvailableTools(Map<String, CamelToolSpecification> availableTools) {
        LOG.info("=== Available Camel Tools ===");
        for (Map.Entry<String, CamelToolSpecification> entry : availableTools.entrySet()) {
            ToolSpecification spec = entry.getValue().getToolSpecification();
            LOG.info("Tool: {} - {}", spec.name(), spec.description());
            
            if (spec.parameters() != null && !spec.parameters().properties().isEmpty()) {
                LOG.info("  Parameters: {}", String.join(", ", spec.parameters().properties().keySet()));
            }
        }
        LOG.info("LangChain4j will see one universal executor with {} tool definitions in description", availableTools.size());
        LOG.info("Tool names: {}", String.join(", ", availableTools.keySet()));
        LOG.info("=============================");
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
                        
                        LOG.debug("Discovered tool: {} -> Camel route tag: {}", toolName, tag);
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

