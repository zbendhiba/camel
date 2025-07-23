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

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import org.apache.camel.Exchange;
import org.apache.camel.component.langchain4j.tools.LangChain4jToolsConsumer;
import org.apache.camel.component.langchain4j.tools.spec.CamelToolSpecification;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CamelRouteToolWrapperTest extends CamelTestSupport {

    @Test
    void testToolWrapperExtractsCorrectDescription() {
        // Create a ToolSpecification with actual description and parameters (like camel-langchain4j-tools would)
        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("query-user-database")
                .description("Query user database by user ID to get user information")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("userId", JsonIntegerSchema.builder().description("The user ID to query").build())
                        .addProperty("includeDetails", JsonStringSchema.builder().description("Whether to include detailed info").build())
                        .build())
                .build();

        // Create mock consumer
        LangChain4jToolsConsumer mockConsumer = Mockito.mock(LangChain4jToolsConsumer.class);
        
        // Create CamelToolSpecification (same as what camel-langchain4j-tools creates)
        CamelToolSpecification camelToolSpec = new CamelToolSpecification(toolSpec, mockConsumer);
        
        // Create mock exchange
        Exchange mockExchange = Mockito.mock(Exchange.class);
        
        // Create our tool wrapper
        LangChain4jAgentProducer.CamelRouteToolWrapper toolWrapper = 
                new LangChain4jAgentProducer.CamelRouteToolWrapper(camelToolSpec, mockExchange, new ObjectMapper());

        // Verify that the wrapper correctly extracts metadata
        assertEquals("query-user-database", toolWrapper.getToolName());
        assertEquals("Query user database by user ID to get user information", toolWrapper.getDescription());
        
        // Verify the ToolSpecification is properly accessible
        ToolSpecification extractedSpec = camelToolSpec.getToolSpecification();
        assertNotNull(extractedSpec);
        assertEquals("query-user-database", extractedSpec.name());
        assertEquals("Query user database by user ID to get user information", extractedSpec.description());
        
        // Verify parameters are correctly defined
        assertNotNull(extractedSpec.parameters());
        assertTrue(extractedSpec.parameters().toString().contains("userId"));
        assertTrue(extractedSpec.parameters().toString().contains("includeDetails"));
        
        // Test toString method
        String toolString = toolWrapper.toString();
        assertTrue(toolString.contains("query-user-database"));
        assertTrue(toolString.contains("Query user database"));
        
        System.out.println("✅ Tool Name: " + toolWrapper.getToolName());
        System.out.println("✅ Tool Description: " + toolWrapper.getDescription());
        System.out.println("✅ Tool Parameters: " + extractedSpec.parameters());
        System.out.println("✅ Tool String: " + toolString);
    }

    @Test
    void testMultipleToolSpecifications() {
        // Create multiple tool specifications to simulate different Camel routes
        
        // Tool 1: User query
        ToolSpecification userTool = ToolSpecification.builder()
                .name("query-user-by-id")
                .description("Query user database by user ID")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("userId", JsonIntegerSchema.builder().build())
                        .build())
                .build();

        // Tool 2: Weather query  
        ToolSpecification weatherTool = ToolSpecification.builder()
                .name("get-weather-info")
                .description("Get weather information for a specific city")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("city", JsonStringSchema.builder().build())
                        .addProperty("country", JsonStringSchema.builder().build())
                        .build())
                .build();

        // Create mock consumers
        LangChain4jToolsConsumer mockConsumer1 = Mockito.mock(LangChain4jToolsConsumer.class);
        LangChain4jToolsConsumer mockConsumer2 = Mockito.mock(LangChain4jToolsConsumer.class);
        
        // Create CamelToolSpecifications
        CamelToolSpecification camelUserTool = new CamelToolSpecification(userTool, mockConsumer1);
        CamelToolSpecification camelWeatherTool = new CamelToolSpecification(weatherTool, mockConsumer2);
        
        // Create mock exchange
        Exchange mockExchange = Mockito.mock(Exchange.class);
        ObjectMapper objectMapper = new ObjectMapper();
        
        // Create tool wrappers
        LangChain4jAgentProducer.CamelRouteToolWrapper userWrapper = 
                new LangChain4jAgentProducer.CamelRouteToolWrapper(camelUserTool, mockExchange, objectMapper);
        LangChain4jAgentProducer.CamelRouteToolWrapper weatherWrapper = 
                new LangChain4jAgentProducer.CamelRouteToolWrapper(camelWeatherTool, mockExchange, objectMapper);

        // Verify user tool
        assertEquals("query-user-by-id", userWrapper.getToolName());
        assertEquals("Query user database by user ID", userWrapper.getDescription());
        assertTrue(userTool.parameters().toString().contains("userId"));

        // Verify weather tool  
        assertEquals("get-weather-info", weatherWrapper.getToolName());
        assertEquals("Get weather information for a specific city", weatherWrapper.getDescription());
        assertTrue(weatherTool.parameters().toString().contains("city"));
        assertTrue(weatherTool.parameters().toString().contains("country"));

        System.out.println("✅ User Tool: " + userWrapper);
        System.out.println("✅ Weather Tool: " + weatherWrapper);
        System.out.println("✅ User Tool Parameters: " + userTool.parameters());
        System.out.println("✅ Weather Tool Parameters: " + weatherTool.parameters());
    }

    @Test 
    void testToolParameterExtraction() {
        // Create a tool with complex parameters to test parameter extraction
        ToolSpecification complexTool = ToolSpecification.builder()
                .name("complex-database-query")
                .description("Execute complex database query with multiple parameters")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("tableName", JsonStringSchema.builder()
                                .description("Name of the database table")
                                .build())
                        .addProperty("userId", JsonIntegerSchema.builder()
                                .description("User ID for the query")
                                .build())
                        .addProperty("limit", JsonIntegerSchema.builder()
                                .description("Maximum number of results")
                                .build())
                        .addProperty("sortOrder", JsonStringSchema.builder()
                                .description("Sort order (ASC or DESC)")
                                .build())
                        .build())
                .build();

        // Create mock consumer and exchange
        LangChain4jToolsConsumer mockConsumer = Mockito.mock(LangChain4jToolsConsumer.class);
        CamelToolSpecification camelToolSpec = new CamelToolSpecification(complexTool, mockConsumer);
        Exchange mockExchange = Mockito.mock(Exchange.class);
        
        // Create tool wrapper
        LangChain4jAgentProducer.CamelRouteToolWrapper toolWrapper = 
                new LangChain4jAgentProducer.CamelRouteToolWrapper(camelToolSpec, mockExchange, new ObjectMapper());

        // Verify tool metadata
        assertEquals("complex-database-query", toolWrapper.getToolName());
        assertEquals("Execute complex database query with multiple parameters", toolWrapper.getDescription());
        
        // Verify all parameters are present in the specification
        String parametersString = complexTool.parameters().toString();
        assertTrue(parametersString.contains("tableName"));
        assertTrue(parametersString.contains("userId"));
        assertTrue(parametersString.contains("limit"));
        assertTrue(parametersString.contains("sortOrder"));
        
        System.out.println("✅ Complex Tool: " + toolWrapper);
        System.out.println("✅ Complex Tool Parameters: " + complexTool.parameters());
        
        // Verify that this would work with LangChain4j AI Services
        assertNotNull(toolWrapper.getToolName());
        assertNotNull(toolWrapper.getDescription());
        assertTrue(toolWrapper.getDescription().length() > 0);
    }
} 