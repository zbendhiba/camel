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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LangChain4jAiServiceParameterBindingTest extends CamelTestSupport {

    // Mock AI Service interfaces
    interface UserMessageService {
        String chat(@UserMessage String text);
    }

    interface TemplateVariableService {
        String analyze(@UserMessage String text, @V("context") String context);
    }

    interface MemoryIdService {
        String chat(@UserMessage String text, @MemoryId String memoryId);
    }

    interface VoidReturnService {
        void log(@UserMessage String text);
    }

    interface ZeroParameterService {
        @UserMessage("Give me a tip")
        String tip();
    }

    interface TypeConversionService {
        String process(@UserMessage Integer number);
    }

    @Test
    void testUserMessageFromBody() throws Exception {
        // Track invocation
        AtomicReference<String> capturedText = new AtomicReference<>();

        // Register mock service
        UserMessageService mockService = createMockService(UserMessageService.class, (proxy, method, args) -> {
            capturedText.set((String) args[0]);
            return "response from chat";
        });
        context.getRegistry().bind("myAssistant", mockService);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("langchain4j-ai-service:myAssistant");
            }
        });
        context.start();

        String result = template.requestBody("direct:start", "Hello", String.class);

        assertThat(capturedText.get()).isEqualTo("Hello");
        assertThat(result).isEqualTo("response from chat");
    }

    @Test
    void testTemplateVariableFromHeader() throws Exception {
        // Track invocation
        AtomicReference<String> capturedText = new AtomicReference<>();
        AtomicReference<String> capturedContext = new AtomicReference<>();

        // Register mock service
        TemplateVariableService mockService = createMockService(TemplateVariableService.class, (proxy, method, args) -> {
            capturedText.set((String) args[0]);
            capturedContext.set((String) args[1]);
            return "response from analyze";
        });
        context.getRegistry().bind("myAssistant", mockService);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("langchain4j-ai-service:myAssistant");
            }
        });
        context.start();

        String result = template.requestBodyAndHeader("direct:start", "Hello",
                "CamelLangChain4jAiServiceContext", "background info", String.class);

        assertThat(capturedText.get()).isEqualTo("Hello");
        assertThat(capturedContext.get()).isEqualTo("background info");
        assertThat(result).isEqualTo("response from analyze");
    }

    @Test
    void testMemoryIdFromHeader() throws Exception {
        // Track invocation
        AtomicReference<String> capturedText = new AtomicReference<>();
        AtomicReference<String> capturedMemoryId = new AtomicReference<>();

        // Register mock service
        MemoryIdService mockService = createMockService(MemoryIdService.class, (proxy, method, args) -> {
            capturedText.set((String) args[0]);
            capturedMemoryId.set((String) args[1]);
            return "response from chat";
        });
        context.getRegistry().bind("myAssistant", mockService);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("langchain4j-ai-service:myAssistant");
            }
        });
        context.start();

        String result = template.requestBodyAndHeader("direct:start", "Hello",
                "CamelLangChain4jAiServiceMemoryId", "session-123", String.class);

        assertThat(capturedText.get()).isEqualTo("Hello");
        assertThat(capturedMemoryId.get()).isEqualTo("session-123");
        assertThat(result).isEqualTo("response from chat");
    }

    @Test
    void testMissingRequiredHeaderFails() throws Exception {
        // Register mock service
        TemplateVariableService mockService = createMockService(TemplateVariableService.class, (proxy, method, args) -> {
            return "should not be called";
        });
        context.getRegistry().bind("myAssistant", mockService);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("langchain4j-ai-service:myAssistant");
            }
        });
        context.start();

        assertThatThrownBy(() -> template.requestBody("direct:start", "Hello", String.class))
                .isInstanceOf(CamelExecutionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("Required header 'CamelLangChain4jAiServiceContext' not found for parameter 'context'");
    }

    @Test
    void testVoidReturnLeavesBodyUnchanged() throws Exception {
        // Track invocation
        AtomicReference<String> capturedText = new AtomicReference<>();

        // Register mock service
        VoidReturnService mockService = createMockService(VoidReturnService.class, (proxy, method, args) -> {
            capturedText.set((String) args[0]);
            return null; // void method
        });
        context.getRegistry().bind("myAssistant", mockService);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("langchain4j-ai-service:myAssistant");
            }
        });
        context.start();

        String result = template.requestBody("direct:start", "Hello", String.class);

        assertThat(capturedText.get()).isEqualTo("Hello");
        assertThat(result).isEqualTo("Hello");
    }

    @Test
    void testZeroParameterMethod() throws Exception {
        // Track invocation
        AtomicReference<Boolean> methodCalled = new AtomicReference<>(false);

        // Register mock service
        ZeroParameterService mockService = createMockService(ZeroParameterService.class, (proxy, method, args) -> {
            methodCalled.set(true);
            assertThat(args).isNull(); // No arguments
            return "Here's a tip";
        });
        context.getRegistry().bind("myAssistant", mockService);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("langchain4j-ai-service:myAssistant");
            }
        });
        context.start();

        String result = template.requestBody("direct:start", "ignored", String.class);

        assertThat(methodCalled.get()).isTrue();
        assertThat(result).isEqualTo("Here's a tip");
    }

    @Test
    void testTypeConversion() throws Exception {
        // Track invocation
        AtomicReference<Integer> capturedNumber = new AtomicReference<>();

        // Register mock service
        TypeConversionService mockService = createMockService(TypeConversionService.class, (proxy, method, args) -> {
            capturedNumber.set((Integer) args[0]);
            return "processed: " + args[0];
        });
        context.getRegistry().bind("myAssistant", mockService);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("langchain4j-ai-service:myAssistant");
            }
        });
        context.start();

        String result = template.requestBody("direct:start", "42", String.class);

        assertThat(capturedNumber.get()).isEqualTo(42);
        assertThat(result).isEqualTo("processed: 42");
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    /**
     * Creates a mock AI Service using dynamic proxy with custom invocation handler.
     */
    @SuppressWarnings("unchecked")
    private <T> T createMockService(Class<T> serviceInterface, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(
                serviceInterface.getClassLoader(),
                new Class<?>[] { serviceInterface },
                handler);
    }
}
