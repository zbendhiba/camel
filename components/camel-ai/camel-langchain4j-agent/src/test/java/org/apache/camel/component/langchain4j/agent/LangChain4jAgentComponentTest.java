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

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static java.time.Duration.ofSeconds;
import static org.apache.camel.component.langchain4j.agent.LangChain4jAgent.Headers.SYSTEM_MESSAGE;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LangChain4jAgentComponentTest extends CamelTestSupport {

    protected ChatModel chatModel;

    private String openAiApiKey;

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        openAiApiKey = System.getenv("OPENAI_API_KEY");
        chatModel = createModel();
    }

    protected ChatModel createModel() {
        return chatModel = OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName("o4-mini")
                .temperature(1.0)
                .timeout(ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Test
    void testSimpleUserMessage() throws InterruptedException {

        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        final String userMessage = "What is Apache Camel?";

        String response = template.requestBody("direct:send-simple-user-message", userMessage, String.class);
        mockEndpoint.assertIsSatisfied();
        assertNotNull(response);
        assertNotEquals(userMessage, response);
        assertTrue(response.contains("Apache Camel"));

    }

    @Test
    void testSimpleUserMessageWithHeaderPrompt() throws InterruptedException {

        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        final String userMessage = "Write a short story about a lost cat.";
        final String systemMessage
                = "You are a whimsical storyteller. Your responses should be imaginative, descriptive, and always include a touch of magic. Start every story with 'Once upon a starlit night...'";

        String response = template.requestBodyAndHeader("direct:send-simple-user-message", userMessage, SYSTEM_MESSAGE,
                systemMessage, String.class);
        mockEndpoint.assertIsSatisfied();
        assertNotNull(response);
        assertNotEquals(userMessage, response);
        assertTrue(response.contains("Once upon a starlit night"));
        assertTrue(response.contains("cat"));

    }

    @Test
    void testSimpleUserMessageWithBodyBean() throws InterruptedException {

        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        final String userMessage = "Write a short story about a lost cat.";
        final String systemMessage
                = "You are a whimsical storyteller. Your responses should be imaginative, descriptive, and always include a touch of magic. Start every story with 'Once upon a starlit night...'";

        AiAgentBody body = new AiAgentBody()
                .withSystemMessage(systemMessage)
                .withUserMessage(userMessage);

        String response = template.requestBody("direct:send-simple-user-message", body, String.class);
        mockEndpoint.assertIsSatisfied();
        assertNotNull(response);
        assertNotEquals(userMessage, response);
        System.out.println("response: " + response);
        assertTrue(response.contains("Once upon a starlit night"));
        assertTrue(response.contains("cat"));

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        this.context.getRegistry().bind("chatModel", chatModel);

        return new RouteBuilder() {
            public void configure() {
                from("direct:send-simple-user-message")
                        .to("langchain4j-agent:test-agent?chatModel=#chatModel")
                        .to("mock:response");

            }
        };
    }

}
