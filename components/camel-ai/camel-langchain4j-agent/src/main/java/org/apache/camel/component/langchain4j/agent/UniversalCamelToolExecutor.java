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

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.apache.camel.Exchange;
import org.apache.camel.component.langchain4j.tools.spec.CamelToolSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Universal tool executor that handles all Camel route tools by name.
 * Uses available tools specifications to inform LLM about individual tools.
 * <p>
 * This class is package-private and intended for internal use within the Camel LangChain4j Agent component.
 */
final class UniversalCamelToolExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(UniversalCamelToolExecutor.class);
    
    private final Map<String, CamelToolSpecification> toolsByName;
    private final Exchange exchange;
    private final ObjectMapper objectMapper;
    private final String availableToolsDescription;

    /**
     * Creates a new universal tool executor for the given tools.
     *
     * @param toolsByName Map of tool names to their specifications
     * @param exchange The Camel exchange context
     * @param objectMapper JSON object mapper for parsing arguments
     */
    UniversalCamelToolExecutor(Map<String, CamelToolSpecification> toolsByName, Exchange exchange, ObjectMapper objectMapper) {
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

    /**
     * Execute a Camel route tool by name with the provided arguments.
     * This method is exposed to LangChain4j via the @Tool annotation.
     *
     * @param toolName The exact name of the tool to execute
     * @param arguments JSON string containing the tool arguments
     * @return The result of the tool execution
     */
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