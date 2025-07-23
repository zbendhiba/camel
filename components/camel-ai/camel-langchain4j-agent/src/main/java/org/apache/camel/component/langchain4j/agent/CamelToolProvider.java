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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.apache.camel.Exchange;
import org.apache.camel.component.langchain4j.tools.spec.CamelToolExecutorCache;
import org.apache.camel.component.langchain4j.tools.spec.CamelToolSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tool provider that auto-generates LangChain4j tools from Camel routes.
 * Uses a two-step approach: list available tools, then execute specific tools.
 */
public class CamelToolProvider {
    private static final Logger LOG = LoggerFactory.getLogger(CamelToolProvider.class);
    
    private final Map<String, CamelToolSpecification> toolMap = new HashMap<>();
    private final Exchange exchange;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public CamelToolProvider(String tags, Exchange exchange) {
        this.exchange = exchange;
        initializeTools(tags);
    }
    
    private void initializeTools(String tags) {
        if (tags == null || tags.trim().isEmpty()) {
            return;
        }
        
        final CamelToolExecutorCache toolCache = CamelToolExecutorCache.getInstance();
        final Map<String, Set<CamelToolSpecification>> tools = toolCache.getTools();
        String[] tagArray = TagsHelper.splitTags(tags);
        
        for (var entry : tools.entrySet()) {
            for (String tag : tagArray) {
                if (entry.getKey().equals(tag)) {
                    for (CamelToolSpecification spec : entry.getValue()) {
                        toolMap.put(spec.getToolSpecification().name(), spec);
                        LOG.debug("Registered tool: {} with description: {}", 
                            spec.getToolSpecification().name(), 
                            spec.getToolSpecification().description());
                    }
                }
            }
        }
        
        LOG.info("Initialized {} tools for tags: {}", toolMap.size(), tags);
    }
    
    /**
     * Execute a specific Camel tool
     */
    public String executeTool(String toolName, String arguments) {
        LOG.info("Executing Camel tool: {} with arguments: {}", toolName, arguments);
        
        CamelToolSpecification camelToolSpec = toolMap.get(toolName);
        if (camelToolSpec == null) {
            LOG.warn("Tool {} not found in available tools", toolName);
            return "Tool not found: " + toolName;
        }
        
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
     * List all available tools with their descriptions
     */
    public String listAvailableTools() {
        if (toolMap.isEmpty()) {
            return "No Camel route tools are currently available.";
        }
        
        StringBuilder result = new StringBuilder();
        result.append("Available Camel route tools:\n");
        
        for (Map.Entry<String, CamelToolSpecification> entry : toolMap.entrySet()) {
            String toolName = entry.getKey();
            ToolSpecification toolSpec = entry.getValue().getToolSpecification();
            
            result.append("- Tool: ").append(toolName).append("\n");
            result.append("  Description: ").append(toolSpec.description()).append("\n");
            
            // Add parameter information if available
            if (toolSpec.parameters() != null) {
                result.append("  Parameters: ").append(toolSpec.parameters()).append("\n");
            }
            result.append("\n");
        }
        
        result.append("Use executeCamelTool(toolName, arguments) to execute any of these tools.");
        return result.toString();
    }
    
    /**
     * Check if any tools are available
     */
    public boolean hasTools() {
        return !toolMap.isEmpty();
    }
    
    /**
     * Create tool instances for LangChain4j AI Services.
     */
    public List<Object> createToolInstances() {
        List<Object> toolInstances = new ArrayList<>();
        
        if (!toolMap.isEmpty()) {
            CamelToolsWrapper toolWrapper = new CamelToolsWrapper(this);
            toolInstances.add(toolWrapper);
            
            LOG.info("Created tool wrapper with {} Camel routes", toolMap.size());
        }
        
        return toolInstances;
    }
    
    /**
     * Tool wrapper with two @Tool methods: list available tools and execute tools
     */
    public static class CamelToolsWrapper {
        private final CamelToolProvider provider;
        
        public CamelToolsWrapper(CamelToolProvider provider) {
            this.provider = provider;
        }
        
        @Tool("List all available Camel route tools with their descriptions and parameters")
        public String listAvailableTools() {
            return provider.listAvailableTools();
        }
        
        @Tool("Execute a specific Camel route tool by name with JSON arguments")
        public String executeCamelTool(String toolName, String arguments) {
            return provider.executeTool(toolName, arguments);
        }
    }
} 