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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import org.apache.camel.Exchange;
import org.apache.camel.component.langchain4j.tools.spec.CamelToolSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating individual tool wrapper objects that represent Camel routes.
 * Each wrapper object will have its own @Tool annotated method with the correct name.
 * <p>
 * This class is package-private and intended for internal use within the Camel LangChain4j Agent component.
 */
final class CamelToolWrapper {
    private static final Logger LOG = LoggerFactory.getLogger(CamelToolWrapper.class);

    /**
     * Creates individual tool objects for each Camel route tool specification.
     * Each object will have a @Tool annotated method using the exact tool name.
     *
     * @param toolsByName Map of tool names to their specifications  
     * @param exchange The Camel exchange context
     * @param objectMapper JSON object mapper for parsing arguments
     * @return List of tool objects, each representing one Camel route
     */
    static List<Object> createToolObjects(Map<String, CamelToolSpecification> toolsByName, Exchange exchange, ObjectMapper objectMapper) {
        List<Object> toolObjects = new ArrayList<>();
        
        LOG.info("Creating {} individual tool objects", toolsByName.size());
        
        for (Map.Entry<String, CamelToolSpecification> entry : toolsByName.entrySet()) {
            String toolName = entry.getKey();
            CamelToolSpecification toolSpec = entry.getValue();
            
            // Create a dynamic tool object for this specific Camel route
            Object toolObject = createDynamicToolObject(toolName, toolSpec, exchange, objectMapper);
            toolObjects.add(toolObject);
            
            LOG.info("Created tool object for: '{}' - {}", toolName, toolSpec.getToolSpecification().description());
        }
        
        return toolObjects;
    }

    /**
     * Creates a dynamic proxy object with a @Tool annotated method for the specific tool.
     */
    private static Object createDynamicToolObject(String toolName, CamelToolSpecification toolSpec, Exchange exchange, ObjectMapper objectMapper) {
        // Define the interface for the tool
        Class<?> toolInterface = createToolInterface(toolName, toolSpec);
        
        // Create invocation handler
        InvocationHandler handler = new ToolInvocationHandler(toolName, toolSpec, exchange, objectMapper);
        
        // Create the proxy object
        return Proxy.newProxyInstance(
            CamelToolWrapper.class.getClassLoader(),
            new Class<?>[] { toolInterface },
            handler
        );
    }

    /**
     * Creates a dynamic interface for the tool with the @Tool annotation.
     * Unfortunately, we can't add annotations dynamically in pure Java.
     * This approach won't work without bytecode generation.
     */
    private static Class<?> createToolInterface(String toolName, CamelToolSpecification toolSpec) {
        // This is where we hit the Java limitation - we can't create @Tool annotations dynamically
        // without bytecode generation or reflection hacks that you don't want
        throw new UnsupportedOperationException("Cannot create @Tool annotations dynamically without bytecode generation");
    }

    /**
     * Invocation handler for tool method calls
     */
    private static class ToolInvocationHandler implements InvocationHandler {
        private final String toolName;
        private final CamelToolSpecification toolSpec;
        private final Exchange exchange;
        private final ObjectMapper objectMapper;

        ToolInvocationHandler(String toolName, CamelToolSpecification toolSpec, Exchange exchange, ObjectMapper objectMapper) {
            this.toolName = toolName;
            this.toolSpec = toolSpec;
            this.exchange = exchange;
            this.objectMapper = objectMapper;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            LOG.info("Invoking tool: '{}' with method: {}", toolName, method.getName());
            
            // Handle the tool execution
            if (args != null && args.length > 0) {
                String arguments = args[0].toString();
                return executeToolRoute(arguments);
            }
            
            return "No arguments provided";
        }

        private String executeToolRoute(String arguments) {
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
                toolSpec.getConsumer().getProcessor().process(exchange);

                // Return the result
                String result = exchange.getIn().getBody(String.class);
                LOG.info("Tool '{}' execution completed successfully", toolName);
                return result != null ? result : "No result";

            } catch (Exception e) {
                LOG.error("Error executing tool '{}': {}", toolName, e.getMessage(), e);
                return String.format("Error executing tool '%s': %s", toolName, e.getMessage());
            }
        }
    }
} 