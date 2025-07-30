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

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static java.time.Duration.ofSeconds;
import static org.apache.camel.component.langchain4j.agent.LangChain4jAgent.Headers.MEMORY_ID;
import static org.apache.camel.component.langchain4j.agent.LangChain4jAgent.Headers.SYSTEM_MESSAGE;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "OPENAI_API_KEY", matches = ".*", disabledReason = "OpenAI API key required")
public class LangChain4jAgentWithMemoryTest extends CamelTestSupport {

    private static final int MEMORY_ID_SESSION_1 = 1;
    private static final int MEMORY_ID_SESSION_2 = 2;
    private static final String USER_NAME = "Alice";
    private static final String USER_FAVORITE_COLOR = "blue";

    protected ChatModel chatModel;
    protected ChatMemoryProvider chatMemoryProvider;
    private String openAiApiKey;

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        openAiApiKey = System.getenv("OPENAI_API_KEY");
        if (openAiApiKey == null || openAiApiKey.trim().isEmpty()) {
            throw new IllegalStateException("OPENAI_API_KEY system property is required for testing");
        }

        chatModel = createModel();
        chatMemoryProvider = createMemoryProvider();
    }

    protected ChatModel createModel() {
        return OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(GPT_4_O_MINI)
                .temperature(1.0)
                .timeout(ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    protected ChatMemoryProvider createMemoryProvider() {
        // Create a message window memory that keeps the last 10 messages

        ChatMemoryProvider chatMemoryProvider = memoryId -> MessageWindowChatMemory.withMaxMessages(10);
        return chatMemoryProvider;
    }

    @BeforeEach
    void setup() {
        chatMemoryProvider.get(MEMORY_ID_SESSION_1).clear();
        chatMemoryProvider.get(MEMORY_ID_SESSION_2).clear();
    }

    @Test
    void testBasicMemoryConversation() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:memory-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(2);

        // First message: establish a fact
        String firstResponse = template.requestBodyAndHeader(
                "direct:agent-with-memory",
                "My name is " + USER_NAME,
                MEMORY_ID, MEMORY_ID_SESSION_1,
                String.class);

        // Second message: test if the agent remembers
        String secondResponse = template.requestBodyAndHeader(
                "direct:agent-with-memory",
                "What is my name?",
                MEMORY_ID, MEMORY_ID_SESSION_1,
                String.class);

        mockEndpoint.assertIsSatisfied();

        assertNotNull(firstResponse, "First AI response should not be null");
        assertNotNull(secondResponse, "Second AI response should not be null");
        assertTrue(secondResponse.contains(USER_NAME),
                "Agent should remember the user's name: " + secondResponse);
    }

    @Test
    void testMemoryPersistenceAcrossMultipleExchanges() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:memory-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(3);

        // Exchange 1: Set name
        template.requestBodyAndHeader(
                "direct:agent-with-memory",
                "My name is " + USER_NAME,
                MEMORY_ID, MEMORY_ID_SESSION_1,
                String.class);

        // Exchange 2: Set favorite color
        template.requestBodyAndHeader(
                "direct:agent-with-memory",
                "My favorite color is " + USER_FAVORITE_COLOR,
                MEMORY_ID, MEMORY_ID_SESSION_1,
                String.class);

        // Exchange 3: Ask for both pieces of information
        String finalResponse = template.requestBodyAndHeader(
                "direct:agent-with-memory",
                "Tell me about myself - what's my name and favorite color?",
                MEMORY_ID, MEMORY_ID_SESSION_1,
                String.class);

        mockEndpoint.assertIsSatisfied();

        assertNotNull(finalResponse, "Final AI response should not be null");
        assertTrue(finalResponse.contains(USER_NAME),
                "Agent should remember the user's name: " + finalResponse);
        assertTrue(finalResponse.contains(USER_FAVORITE_COLOR),
                "Agent should remember the user's favorite color: " + finalResponse);
    }

    @Test
    void testMemoryIsolationBetweenSessions() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:memory-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(3);

        // Session 1: Set name
        template.requestBodyAndHeader(
                "direct:agent-with-memory",
                "My name is " + USER_NAME,
                MEMORY_ID, MEMORY_ID_SESSION_1,
                String.class);

        // Session 2: Ask for name (should not know it)
        String session2Response = template.requestBodyAndHeader(
                "direct:agent-with-memory",
                "What is my name?",
                MEMORY_ID, MEMORY_ID_SESSION_2,
                String.class);
        System.out.println("session2Response:: " + session2Response);

        // Session 1: Ask for name (should remember it)
        String session1Response = template.requestBodyAndHeader(
                "direct:agent-with-memory",
                "What is my name?",
                MEMORY_ID, MEMORY_ID_SESSION_1,
                String.class);
        System.out.println("session1Response:: " + session1Response);

        mockEndpoint.assertIsSatisfied();

        assertNotNull(session1Response, "Session 1 response should not be null");
        assertNotNull(session2Response, "Session 2 response should not be null");

        assertTrue(session1Response.contains(USER_NAME),
                "Session 1 should remember the name: " + session1Response);
        assertTrue(!session2Response.contains(USER_NAME) ||
                session2Response.toLowerCase().contains("don't know") ||
                session2Response.toLowerCase().contains("not sure"),
                "Session 2 should not know the name from session 1: " + session2Response);
    }

    @Test
    void testMemoryWithSystemMessage() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:memory-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(2);

        // First message with system message
        String firstResponse = template.requestBodyAndHeaders(
                "direct:agent-with-memory-system",
                "My favorite programming language is Java",
                Map.of(
                        MEMORY_ID, MEMORY_ID_SESSION_1,
                        SYSTEM_MESSAGE, "You are a helpful coding assistant. Always be enthusiastic about programming."),
                String.class);

        // Second message to test memory with system context
        String secondResponse = template.requestBodyAndHeader(
                "direct:agent-with-memory-system",
                "What programming language do I like?",
                MEMORY_ID, MEMORY_ID_SESSION_1,
                String.class);

        mockEndpoint.assertIsSatisfied();

        assertNotNull(firstResponse, "First response should not be null");
        assertNotNull(secondResponse, "Second response should not be null");
        assertTrue(secondResponse.contains("Java"),
                "Agent should remember the programming language preference: " + secondResponse);
    }

    @Test
    void testMemoryWithToolsIntegration() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:memory-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(2);

        // First: Ask for weather information (should use tool)
        String weatherResponse = template.requestBodyAndHeader(
                "direct:agent-with-memory-and-tools",
                "What's the weather like in Paris?",
                MEMORY_ID, MEMORY_ID_SESSION_1,
                String.class);

        // Second: Reference the previous request (should remember and potentially use tool again)
        String memoryResponse = template.requestBodyAndHeader(
                "direct:agent-with-memory-and-tools",
                "What city did I ask about the weather for?",
                MEMORY_ID, MEMORY_ID_SESSION_1,
                String.class);

        mockEndpoint.assertIsSatisfied();

        assertNotNull(weatherResponse, "Weather response should not be null");
        assertNotNull(memoryResponse, "Memory response should not be null");
        assertTrue(memoryResponse.contains("Paris"),
                "Agent should remember the city asked about: " + memoryResponse);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        this.context.getRegistry().bind("chatModel", chatModel);
        this.context.getRegistry().bind("chatMemoryProvider", chatMemoryProvider);

        return new RouteBuilder() {
            public void configure() {
                // Agent routes for memory testing
                from("direct:agent-with-memory")
                        .to("langchain4j-agent:test-memory-agent?chatModel=#chatModel&chatMemoryProvider=#chatMemoryProvider")
                        .to("mock:memory-response");

                from("direct:agent-with-memory-system")
                        .to("langchain4j-agent:test-memory-agent?chatModel=#chatModel&chatMemoryProvider=#chatMemoryProvider")
                        .to("mock:memory-response");

                from("direct:agent-with-memory-and-tools")
                        .to("langchain4j-agent:test-memory-agent?chatModel=#chatModel&chatMemoryProvider=#chatMemoryProvider&tags=weather")
                        .to("mock:memory-response");

                // Tool consumer route for weather information
                from("langchain4j-tools:weatherService?tags=weather&description=Get weather information for a city&parameter.city=string")
                        .setBody(constant("{\"weather\": \"sunny, 22°C\", \"city\": \"Paris\"}"));
            }
        };
    }

    interface Assistant {

        String chat(@MemoryId int memoryId, @UserMessage String userMessage);
    }

    public static void main(String[] args) {

        ChatModel model = OpenAiChatModel.builder()
               
                .modelName(GPT_4_O_MINI)
                .build();

        ChatMemoryProvider memory = memoryId -> MessageWindowChatMemory.withMaxMessages(10);
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemoryProvider(memory)
                .build();

        System.out.println(assistant.chat(1, "Hello, my name is Klaus"));
        // Hi Klaus! How can I assist you today?

        // System.out.println(assistant.chat(2, "Hello, my name is Francine"));
        // Hello Francine! How can I assist you today?

        System.out.println(assistant.chat(1, "What is my name?"));
        // Your name is Klaus.

        System.out.println(assistant.chat(2, "What is my name?"));
        // Your name is Francine.
    }

}
