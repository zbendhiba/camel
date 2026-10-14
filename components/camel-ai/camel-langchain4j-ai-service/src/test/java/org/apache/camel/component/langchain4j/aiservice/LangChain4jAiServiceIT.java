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
package org.apache.camel.component.langchain4j.aiservice;

import java.util.concurrent.atomic.AtomicBoolean;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.ollama.services.OllamaService;
import org.apache.camel.test.infra.ollama.services.OllamaServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests invoking real LangChain4j AI Services backed by an Ollama model.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Requires too much network resources")
public class LangChain4jAiServiceIT extends CamelTestSupport {

    @RegisterExtension
    static OllamaService OLLAMA = OllamaServiceFactory.createSingletonService();

    // AI Service interfaces

    interface MyAssistant {
        String chat(@UserMessage String text);
    }

    interface MultiMethodAssistant {
        String chat(@UserMessage String text);

        String analyze(@UserMessage String text);
    }

    interface ContextAssistant {
        @SystemMessage("Use the following context when answering: {{context}}")
        String analyze(@UserMessage String text, @V("context") String context);
    }

    interface MemoryAssistant {
        String chat(@UserMessage String text, @MemoryId String memoryId);
    }

    interface ToolAssistant {
        String chat(@UserMessage String text);
    }

    static class WeatherTool {
        private final AtomicBoolean invoked;

        WeatherTool(AtomicBoolean invoked) {
            this.invoked = invoked;
        }

        @Tool("Gets the current weather for a location")
        String getWeather(String location) {
            invoked.set(true);
            return "Sunny, 22°C in " + location;
        }
    }

    @Test
    void testSingleMethodInvocation() throws Exception {
        MyAssistant assistant = AiServices.builder(MyAssistant.class)
                .chatModel(chatModel())
                .build();
        context.getRegistry().bind("myAssistant", assistant);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("langchain4j-ai-service:myAssistant");
            }
        });
        context.start();

        String response = template.requestBody("direct:start", "Say hello", String.class);

        assertThat(response).isNotBlank();
    }

    @Test
    void testMultiMethodWithExplicitMethod() throws Exception {
        MultiMethodAssistant assistant = AiServices.builder(MultiMethodAssistant.class)
                .chatModel(chatModel())
                .build();
        context.getRegistry().bind("multiAssistant", assistant);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("langchain4j-ai-service:multiAssistant?method=analyze");
            }
        });
        context.start();

        String response = template.requestBody("direct:start", "Analyze this text", String.class);

        assertThat(response).isNotBlank();
    }

    @Test
    void testTemplateVariableBinding() throws Exception {
        ContextAssistant assistant = AiServices.builder(ContextAssistant.class)
                .chatModel(chatModel())
                .build();
        context.getRegistry().bind("contextAssistant", assistant);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("langchain4j-ai-service:contextAssistant");
            }
        });
        context.start();

        String response = template.requestBodyAndHeader("direct:start",
                "What is the secret word? Reply with just the word.",
                "CamelLangChain4jAiServiceContext", "The secret word is pineapple.", String.class);

        assertThat(response).isNotBlank();
        assertThat(response).containsIgnoringCase("pineapple");
    }

    @Test
    void testMemoryIdBinding() throws Exception {
        MemoryAssistant assistant = AiServices.builder(MemoryAssistant.class)
                .chatModel(chatModel())
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .build();
        context.getRegistry().bind("memoryAssistant", assistant);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("langchain4j-ai-service:memoryAssistant");
            }
        });
        context.start();

        template.requestBodyAndHeader("direct:start", "Remember my name is John",
                LangChain4jAiServiceHeaders.MEMORY_ID, "session-123", String.class);

        String response = template.requestBodyAndHeader("direct:start",
                "What is my name? Reply with just the name.",
                LangChain4jAiServiceHeaders.MEMORY_ID, "session-123", String.class);

        assertThat(response).isNotBlank();
        assertThat(response).containsIgnoringCase("John");
    }

    @Test
    void testExceptionPropagation() throws Exception {
        // A model name that does not exist on the Ollama server makes the invocation fail
        ChatModel brokenModel = OllamaChatModel.builder()
                .baseUrl(OLLAMA.baseUrl())
                .modelName("no-such-model")
                .timeout(ofSeconds(120))
                .build();
        MyAssistant assistant = AiServices.builder(MyAssistant.class)
                .chatModel(brokenModel)
                .build();
        context.getRegistry().bind("brokenAssistant", assistant);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("langchain4j-ai-service:brokenAssistant");
            }
        });
        context.start();

        assertThatThrownBy(() -> template.requestBody("direct:start", "Say hello", String.class))
                .isInstanceOf(CamelExecutionException.class)
                .cause().isNotNull();
    }

    @Test
    void testAgenticInterfaceInvocation() throws Exception {
        AtomicBoolean toolInvoked = new AtomicBoolean();
        ToolAssistant assistant = AiServices.builder(ToolAssistant.class)
                .chatModel(chatModel())
                .tools(new WeatherTool(toolInvoked))
                .build();
        context.getRegistry().bind("toolAssistant", assistant);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("langchain4j-ai-service:toolAssistant");
            }
        });
        context.start();

        String response = template.requestBody("direct:start",
                "You MUST use the getWeather tool. What's the weather in Paris?", String.class);

        assertThat(response).isNotBlank();
        assertThat(toolInvoked).isTrue();
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private ChatModel chatModel() {
        return OllamaChatModel.builder()
                .baseUrl(OLLAMA.baseUrl())
                .modelName(OLLAMA.modelName())
                .temperature(0.0)
                .timeout(ofSeconds(120))
                .build();
    }
}
