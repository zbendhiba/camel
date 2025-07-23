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

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.tools.LangChain4jTools;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class LangChain4jAgentToolsIntegrationTest extends CamelTestSupport {

    private static final String TOOL_RESPONSE = "{\"result\": \"User found: John Doe\"}";
    private static final String FINAL_AI_RESPONSE = "The user name is John Doe.";
    
    private ChatModel mockChatModel;

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();
        mockChatModel = createMockChatModel();
    }

    private ChatModel createMockChatModel() {
        ChatModel chatModel = Mockito.mock(ChatModel.class);
        
        // First call: AI decides to call a tool
        List<ToolExecutionRequest> toolRequests = new ArrayList<>();
        toolRequests.add(ToolExecutionRequest.builder()
                .id("tool-call-1")
                .name("query-database-by-user-id")
                .arguments("{\"userId\": \"123\"}")
                .build());
        
        AiMessage aiMessageWithTool = AiMessage.builder()
                .text("I'll look up the user information for you.")
                .toolExecutionRequests(toolRequests)
                .build();
        
        ChatResponse firstResponse = ChatResponse.builder()
                .aiMessage(aiMessageWithTool)
                .build();

        // Second call: AI provides final response after tool execution
        AiMessage finalAiMessage = AiMessage.builder()
                .text(FINAL_AI_RESPONSE)
                .build();
        
        ChatResponse finalResponse = ChatResponse.builder()
                .aiMessage(finalAiMessage)
                .build();

        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(firstResponse)
                .thenReturn(finalResponse);
        
        return chatModel;
    }

    @Test
    void testToolsIntegrationWithTags() throws InterruptedException {
        MockEndpoint mockEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        // Test with tags in header
        String response = template.requestBodyAndHeader(
                "direct:agent-with-tools",
                "What is the name of user 123?",
                "CamelLangChain4jAgentTags", "users",
                String.class
        );

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response);
        assertEquals(FINAL_AI_RESPONSE, response);
    }

    @Test
    void testToolsIntegrationWithConfiguredTags() throws InterruptedException {
        MockEndpoint mockEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        // Test with tags configured on endpoint
        String response = template.requestBody(
                "direct:agent-with-configured-tags",
                "What is the name of user 123?",
                String.class
        );

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response);
        assertEquals(FINAL_AI_RESPONSE, response);
    }

    @Test
    void testNoToolsCalledWhenTagsDontMatch() throws InterruptedException {
        // Mock for the case when no tools are found
        ChatModel noToolsChatModel = Mockito.mock(ChatModel.class);
        AiMessage simpleResponse = AiMessage.builder()
                .text("I don't have access to user information.")
                .build();
        ChatResponse response = ChatResponse.builder()
                .aiMessage(simpleResponse)
                .build();
        when(noToolsChatModel.chat(any(ChatRequest.class))).thenReturn(response);
        
        context.getRegistry().bind("noToolsChatModel", noToolsChatModel);

        MockEndpoint mockEndpoint = context.getEndpoint("mock:no-tools-result", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);
        mockEndpoint.expectedHeaderReceived(LangChain4jTools.NO_TOOLS_CALLED_HEADER, Boolean.TRUE);

        template.requestBodyAndHeader(
                "direct:agent-no-tools",
                "What is the name of user 123?",
                "CamelLangChain4jAgentTags", "nonexistent",
                String.class
        );

        mockEndpoint.assertIsSatisfied();
    }

    @Test
    void testRegularChatWithoutTools() throws InterruptedException {
        // Mock for regular chat without tools
        ChatModel regularChatModel = Mockito.mock(ChatModel.class);
        AiMessage regularResponse = AiMessage.builder()
                .text("Apache Camel is an integration framework.")
                .build();
        ChatResponse response = ChatResponse.builder()
                .aiMessage(regularResponse)
                .build();
        when(regularChatModel.chat(any(ChatRequest.class))).thenReturn(response);
        
        context.getRegistry().bind("regularChatModel", regularChatModel);

        MockEndpoint mockEndpoint = context.getEndpoint("mock:regular-result", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String result = template.requestBody(
                "direct:agent-regular",
                "What is Apache Camel?",
                String.class
        );

        mockEndpoint.assertIsSatisfied();
        assertNotNull(result);
        assertTrue(result.contains("Apache Camel"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        context.getRegistry().bind("mockChatModel", mockChatModel);
        
        // Create agent configuration with users tags for the configured tags test
        LangChain4jAgentConfiguration usersConfig = new LangChain4jAgentConfiguration();
        usersConfig.setChatModel(mockChatModel);
        usersConfig.setTags("users");
        context.getRegistry().bind("usersAgentConfig", usersConfig);

        return new RouteBuilder() {
            public void configure() {
                // Routes for testing tools integration
                from("direct:agent-with-tools")
                        .to("langchain4j-agent:test-agent?chatModel=#mockChatModel")
                        .to("mock:result");

                from("direct:agent-with-configured-tags")
                        .to("langchain4j-agent:test-agent?configuration=#usersAgentConfig")
                        .to("mock:result");

                from("direct:agent-no-tools")
                        .to("langchain4j-agent:test-agent?chatModel=#noToolsChatModel")
                        .to("mock:no-tools-result");

                from("direct:agent-regular")
                        .to("langchain4j-agent:test-agent?chatModel=#regularChatModel")
                        .to("mock:regular-result");

                // Tool consumer routes
                from("langchain4j-tools:userDatabase?tags=users&description=Query database by user ID&parameter.userId=integer")
                        .setBody(constant(TOOL_RESPONSE))
                        .log("Tool called with headers: ${headers}");
            }
        };
    }
} 