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
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.apache.camel.builder.RouteBuilder;
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
    private ChatModel regularChatModel;

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();
        mockChatModel = createMockChatModel();
        regularChatModel = createRegularChatModel();
    }

    private ChatModel createMockChatModel() {
        ChatModel chatModel = Mockito.mock(ChatModel.class);

        // First call: AI decides to call a tool
        List<ToolExecutionRequest> toolRequests = new ArrayList<>();
        toolRequests.add(ToolExecutionRequest.builder()
                .id("tool-call-1")
                .name("executeTool")
                .arguments("{\"toolName\":\"QueryUserDatabaseByUserID\",\"arguments\":\"{\\\"id\\\":\\\"123\\\"}\"}")
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

    private ChatModel createRegularChatModel() {
        ChatModel regularChatModel = Mockito.mock(ChatModel.class);
        AiMessage regularResponse = AiMessage.builder()
                .text("Apache Camel is an integration framework.")
                .build();

        ChatResponse response = ChatResponse.builder()
                .aiMessage(regularResponse)
                .build();
        when(regularChatModel.chat(any(ChatRequest.class))).thenReturn(response);
        return regularChatModel;
    }

    @Test
    void testToolsIntegrationWithTags() throws InterruptedException {
        MockEndpoint mockEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        // Test with tags in configuration
        String response = template.requestBody(
                "direct:agent-with-tools",
                "What is the name of user 123?",
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response);
        assertEquals(FINAL_AI_RESPONSE, response);
    }

    @Test
    void testRegularChatWithoutTools() throws InterruptedException {

        context.getRegistry().bind("regularChatModel", regularChatModel);

        MockEndpoint mockEndpoint = context.getEndpoint("mock:regular-result", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String result = template.requestBody(
                "direct:agent-regular",
                "What is Apache Camel?",
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(result);
        assertTrue(result.contains("Apache Camel"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        context.getRegistry().bind("mockChatModel", mockChatModel);
        context.getRegistry().bind("regularChatModel", regularChatModel);

        return new RouteBuilder() {
            public void configure() {
                // Routes for testing tools integration
                from("direct:agent-with-tools")
                        .to("langchain4j-agent:test-agent?chatModel=#mockChatModel&tags=users")
                        .process(e ->{
                            System.out.println("processor ::: body is " + e.getIn().getBody(String.class));
                        })
                        .to("mock:result");

                from("direct:agent-regular")
                        .to("langchain4j-agent:test-agent?chatModel=#regularChatModel&tags=nonexistent")
                        .to("mock:regular-result");

                // Tool consumer routes
                from("langchain4j-tools:userDb?tags=users&description=Query user database by user ID&parameter.userId=integer")
                        .setBody(constant(TOOL_RESPONSE))
                        .log("Tool called with headers: ${headers}");
            }
        };
    }
}
