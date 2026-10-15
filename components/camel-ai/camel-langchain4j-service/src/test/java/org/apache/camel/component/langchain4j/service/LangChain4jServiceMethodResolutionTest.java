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

import java.lang.reflect.Proxy;

import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LangChain4jServiceMethodResolutionTest extends CamelTestSupport {

    // Mock AI Service interfaces
    public interface SingleMethodService {
        String chat(String userMessage);
    }

    public interface MultiMethodService {
        String chat(String userMessage);

        String analyze(String userMessage);
    }

    public interface OverloadedMethodService {
        String chat(String userMessage);

        String chat(String userMessage, String context);
    }

    @Test
    void testSingleMethodAutoSelection() throws Exception {
        // Register mock service
        SingleMethodService mockService = createMockService(SingleMethodService.class);
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
        MultiMethodService mockService = createMockService(MultiMethodService.class);
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
        MultiMethodService mockService = createMockService(MultiMethodService.class);
        context.getRegistry().bind("myAssistant", mockService);

        assertThatThrownBy(() -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start")
                            .to("langchain4j-service:myAssistant");
                }
            });
            context.start();
        })
                .as("a multi-method interface without an explicit method parameter is ambiguous, "
                    + "so route startup must fail fast and list the candidates")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("has multiple methods")
                .hasMessageContaining("Specify method parameter")
                .hasMessageContaining("Available methods: analyze, chat");
    }

    @Test
    void testServiceNotFoundFails() {
        // Do NOT register any service as 'nonexistent'

        assertThatThrownBy(() -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start")
                            .to("langchain4j-service:nonexistent");
                }
            });
            context.start();
        })
                .as("a service name that resolves to no registry bean must fail route startup, "
                    + "not the first message")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("No bean could be found in the registry for: nonexistent");
    }

    @Test
    void testInvalidMethodNameFails() {
        // Register mock service with only 'chat' method
        SingleMethodService mockService = createMockService(SingleMethodService.class);
        context.getRegistry().bind("myAssistant", mockService);

        assertThatThrownBy(() -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start")
                            .to("langchain4j-service:myAssistant?method=analyze");
                }
            });
            context.start();
        })
                .as("an explicitly configured method name that does not exist must never be ignored silently, "
                    + "even when the interface has a single method")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("Method 'analyze' not found")
                .hasMessageContaining("Available methods: chat");
    }

    @Test
    void testSingleMethodWithMatchingExplicitMethod() throws Exception {
        // Register mock service
        SingleMethodService mockService = createMockService(SingleMethodService.class);
        context.getRegistry().bind("myAssistant", mockService);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("langchain4j-service:myAssistant?method=chat");
            }
        });
        context.start();

        String result = template.requestBody("direct:start", "Hello", String.class);

        assertThat(result)
                .as("an explicit method parameter that matches the single business method "
                    + "must be accepted, not rejected by the single-method shortcut")
                .isEqualTo("response from chat");
    }

    @Test
    void testInvalidMethodNameOnMultiMethodInterfaceFails() {
        // Register mock service with 'chat' and 'analyze' methods only
        MultiMethodService mockService = createMockService(MultiMethodService.class);
        context.getRegistry().bind("myAssistant", mockService);

        assertThatThrownBy(() -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start")
                            .to("langchain4j-service:myAssistant?method=translate");
                }
            });
            context.start();
        })
                .as("a method name matching none of several business methods must fail route startup "
                    + "exactly (no fuzzy or case-insensitive matching) and list the candidates")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("Method 'translate' not found")
                .hasMessageContaining("Available methods: analyze, chat");
    }

    @Test
    void testOverloadedMethodFails() {
        // Register mock service
        OverloadedMethodService mockService = createMockService(OverloadedMethodService.class);
        context.getRegistry().bind("myAssistant", mockService);

        assertThatThrownBy(() -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start")
                            .to("langchain4j-service:myAssistant?method=chat");
                }
            });
            context.start();
        })
                .as("method resolution is by name only, so an overloaded name is ambiguous "
                    + "and must fail route startup")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("has multiple methods named 'chat'")
                .hasMessageContaining("Method overloading is not supported");
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    /**
     * Creates a mock AI Service using a dynamic proxy that answers "response from &lt;method name&gt;", so each test
     * can tell which method was selected.
     */
    @SuppressWarnings("unchecked")
    private <T> T createMockService(Class<T> serviceInterface) {
        return (T) Proxy.newProxyInstance(
                serviceInterface.getClassLoader(),
                new Class<?>[] { serviceInterface },
                (proxy, method, args) -> {
                    // Answer Object methods sanely so a map or log statement never gets a String for hashCode()
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> "mock " + serviceInterface.getSimpleName();
                        };
                    }
                    return "response from " + method.getName();
                });
    }
}
