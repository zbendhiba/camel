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
package org.apache.camel.component.langchain4j.service;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LangChain4jServiceMethodResolutionTest extends CamelTestSupport {

    // Mock AI Service interfaces
    interface SingleMethodService {
        String chat(String userMessage);
    }

    interface MultiMethodService {
        String chat(String userMessage);

        String analyze(String userMessage);
    }

    interface OverloadedMethodService {
        String chat(String userMessage);

        String chat(String userMessage, String context);
    }

    @Test
    void testSingleMethodAutoSelection() throws Exception {
        // Register mock service
        SingleMethodService mockService = createMockService(SingleMethodService.class, "response from chat");
        context.getRegistry().bind("myAssistant", mockService);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("langchain4j-service:myAssistant");
            }
        });
        context.start();

        String result = template.requestBody("direct:start", "Hello", String.class);
        assertThat(result).isEqualTo("response from chat");
    }

    @Test
    void testMultiMethodWithExplicitMethod() throws Exception {
        // Register mock service
        MultiMethodService mockService = createMockService(MultiMethodService.class, "response from analyze");
        context.getRegistry().bind("myAssistant", mockService);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("langchain4j-service:myAssistant?method=analyze");
            }
        });
        context.start();

        String result = template.requestBody("direct:start", "Hello", String.class);
        assertThat(result).isEqualTo("response from analyze");
    }

    @Test
    void testMultiMethodWithoutMethodParameterFails() {
        // Register mock service
        MultiMethodService mockService = createMockService(MultiMethodService.class, "response");
        context.getRegistry().bind("myAssistant", mockService);

        FailedToCreateRouteException exception = assertThrows(FailedToCreateRouteException.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start")
                            .to("langchain4j-service:myAssistant");
                }
            });
            context.start();
        });

        String message = exception.getMessage();
        assertThat(message).contains("has multiple methods");
        assertThat(message).contains("Specify method parameter");
        assertThat(message).contains("Available methods: analyze, chat");
    }

    @Test
    void testServiceNotFoundFails() {
        // Do NOT register any service as 'nonexistent'

        FailedToCreateRouteException exception = assertThrows(FailedToCreateRouteException.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start")
                            .to("langchain4j-service:nonexistent");
                }
            });
            context.start();
        });

        String message = exception.getMessage();
        assertThat(message).contains("AI Service 'nonexistent' not found in registry");
    }

    @Test
    void testInvalidMethodNameFails() {
        // Register mock service with only 'chat' method
        SingleMethodService mockService = createMockService(SingleMethodService.class, "response from chat");
        context.getRegistry().bind("myAssistant", mockService);

        FailedToCreateRouteException exception = assertThrows(FailedToCreateRouteException.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start")
                            .to("langchain4j-service:myAssistant?method=analyze");
                }
            });
            context.start();
        });

        String message = exception.getMessage();
        assertThat(message).contains("Method 'analyze' not found");
        assertThat(message).contains("Available methods: chat");
    }

    @Test
    void testOverloadedMethodFails() {
        // Register mock service
        OverloadedMethodService mockService = createMockService(OverloadedMethodService.class, "response");
        context.getRegistry().bind("myAssistant", mockService);

        FailedToCreateRouteException exception = assertThrows(FailedToCreateRouteException.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start")
                            .to("langchain4j-service:myAssistant?method=chat");
                }
            });
            context.start();
        });

        String message = exception.getMessage();
        assertThat(message).contains("has multiple methods named 'chat'");
        assertThat(message).contains("Method overloading is not supported");
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    /**
     * Creates a mock AI Service using dynamic proxy that returns predictable values.
     */
    @SuppressWarnings("unchecked")
    private <T> T createMockService(Class<T> serviceInterface, String returnValue) {
        return (T) Proxy.newProxyInstance(
                serviceInterface.getClassLoader(),
                new Class<?>[] { serviceInterface },
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        // Return the method name-specific response
                        if (method.getName().equals("chat")) {
                            return "response from chat";
                        } else if (method.getName().equals("analyze")) {
                            return "response from analyze";
                        }
                        return returnValue;
                    }
                });
    }
}
