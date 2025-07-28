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
        
        // Log available tools for debugging
        LOG.info("=== UniversalCamelToolExecutor Created ===");
        LOG.info("Available tools count: {}", toolsByName.size());
        LOG.info("Available toolName values: {}", String.join(", ", toolsByName.keySet()));
        LOG.info("==========================================");
    }

    /**
     * Build a description of all available tools for the LLM
     */
    private String buildToolsDescription(Map<String, CamelToolSpecification> toolsByName) {
        StringBuilder description = new StringBuilder("Execute Camel route tools. Available tools:\n");
        for (Map.Entry<String, CamelToolSpecification> entry : toolsByName.entrySet()) {
            ToolSpecification spec = entry.getValue().getToolSpecification();
            description.append("toolName: '").append(spec.name()).append("' - ").append(spec.description());
            if (spec.parameters() != null && !spec.parameters().properties().isEmpty()) {
                description.append(" (Parameters: ").append(String.join(", ", spec.parameters().properties().keySet())).append(")");
            }
            description.append("\n");
        }
        description.append("Use the exact toolName parameter values shown above to specify which tool to execute.");
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
    @Tool("Execute a Camel route tool by name. IMPORTANT: The 'toolName' parameter must be the EXACT string from available tools. DO NOT abbreviate or modify. Examples: use 'QueryUserDatabaseByUserID' NOT 'GetUserById', use 'GetWeatherInformationForParis' NOT 'GetWeather'. First parameter 'toolName' = exact tool name, second parameter 'arguments' = JSON string.")
    public String executeTool(String toolName, String arguments) {
        LOG.info("=== TOOL EXECUTION REQUEST ===");
        LOG.info("LLM requested toolName: '{}'", toolName);
        LOG.info("Available toolName values: {}", String.join(", ", toolsByName.keySet()));
        LOG.info("Arguments: {}", arguments);
        LOG.info("=============================");
        
        // Validate tool name
        if (toolName == null || toolName.trim().isEmpty()) {
            StringBuilder errorMsg = new StringBuilder();
            errorMsg.append("ERROR: toolName parameter is required. Available toolName values:\n");
            for (String validToolName : toolsByName.keySet()) {
                errorMsg.append("✓ '").append(validToolName).append("'\n");
            }
            LOG.error("Missing toolName parameter");
            return errorMsg.toString();
        }
        
        // Map tool name to Camel route
        CamelToolSpecification camelToolSpec = toolsByName.get(toolName.trim());
        if (camelToolSpec == null) {
            StringBuilder errorMsg = new StringBuilder();
            errorMsg.append(String.format("TOOL ERROR: '%s' is not valid. ", toolName));
            errorMsg.append("You must use EXACT toolName values. ");
            errorMsg.append("Available options:\n");
            for (String validToolName : toolsByName.keySet()) {
                errorMsg.append("✓ toolName: '").append(validToolName).append("'\n");
            }
            errorMsg.append("Please retry with one of these exact strings as toolName parameter.");
            
            LOG.error("Tool name mismatch - LLM used: '{}', Available toolName values: {}", toolName, String.join(", ", toolsByName.keySet()));
            return errorMsg.toString();
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