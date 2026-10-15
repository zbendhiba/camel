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

import java.io.Serializable;
import java.lang.reflect.Proxy;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the fail-fast validations performed when the producer starts: annotation conflicts, interface resolution and
 * the lazyStartProducer deferral of those failures to the first message.
 */
class LangChain4jServiceValidationTest extends CamelTestSupport {

    LangChain4jServiceValidationTest() {
        testConfiguration().withUseRouteBuilder(false);
    }

    // Mock AI Service interfaces exercising each validation branch

    public interface ConflictingAnnotationsService {
        String chat(@UserMessage @V("context") String text);
    }

    public interface MultipleUserMessageService {
        String chat(@UserMessage String first, @UserMessage String second);
    }

    public interface UnannotatedMultiParamService {
        String chat(String first, String second);
    }

    public interface DuplicateVariableService {
        String chat(@UserMessage String text, @V("context") String first, @V("Context") String second);
    }

    public interface MemoryIdCollisionService {
        String chat(@UserMessage String text, @V("memoryId") String memoryId);
    }

    public interface MemoryIdCaseCollisionService {
        String chat(@UserMessage String text, @V("memoryID") String memoryId);
    }

    public interface DuplicateVariableDifferentCaseService {
        String chat(@UserMessage String text, @V("userName") String first, @V("username") String second);
    }

    public interface EmptyVariableNameService {
        String chat(@UserMessage String text, @V("") String context);
    }

    public interface DuplicateMemoryIdService {
        String chat(@UserMessage String text, @MemoryId String first, @MemoryId String second);
    }

    public interface GreetingService {
        String greet(String text);
    }

    // Methodless marker: interface resolution must filter it out as a candidate
    public interface MarkerInterface {
    }

    static class BaseGreetingBean implements GreetingService {
        @Override
        public String greet(String text) {
            return "greetings " + text;
        }
    }

    // The business interface comes from the superclass; Serializable and the marker must be filtered out
    static class ConcreteGreetingBean extends BaseGreetingBean implements Serializable, MarkerInterface {
    }

    // Intentionally package-private: the producer must reject it instead of failing later with
    // an obscure IllegalAccessException on the first message
    interface NonPublicService {
        String chat(@UserMessage String text);
    }

    public interface FirstBusinessInterface {
        String chat(String text);
    }

    public interface SecondBusinessInterface {
        String analyze(String text);
    }

    static class MultiInterfaceBean implements FirstBusinessInterface, SecondBusinessInterface {
        @Override
        public String chat(String text) {
            return "chat";
        }

        @Override
        public String analyze(String text) {
            return "analyze";
        }
    }

    public interface DefaultAndStaticMethodService {
        String chat(String text);

        default String helper() {
            return "helper";
        }

        static String util() {
            return "util";
        }
    }

    @Test
    void testConflictingAnnotationsFails() {
        context.getRegistry().bind("myAssistant", createMockService(ConflictingAnnotationsService.class));

        assertThatThrownBy(() -> {
            addRouteTo("langchain4j-service:myAssistant");
            context.start();
        })
                .as("a parameter carrying both @UserMessage and @V is ambiguous, "
                    + "so route startup must fail and name the conflicting annotations")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("has conflicting annotations")
                .hasMessageContaining("@UserMessage and @V");
    }

    @Test
    void testMultipleUserMessageParametersFails() {
        context.getRegistry().bind("myAssistant", createMockService(MultipleUserMessageService.class));

        assertThatThrownBy(() -> {
            addRouteTo("langchain4j-service:myAssistant");
            context.start();
        })
                .as("only one parameter can be fed from the message body, "
                    + "so two @UserMessage parameters must fail route startup")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("has multiple @UserMessage parameters");
    }

    @Test
    void testUnannotatedMultiParameterFails() {
        context.getRegistry().bind("myAssistant", createMockService(UnannotatedMultiParamService.class));

        assertThatThrownBy(() -> {
            addRouteTo("langchain4j-service:myAssistant");
            context.start();
        })
                .as("the body fallback only applies to single-parameter methods, "
                    + "so an un-annotated parameter in a multi-parameter method must fail route startup")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("has no binding annotation")
                .hasMessageContaining("each parameter must be annotated with @UserMessage, @V, or @MemoryId");
    }

    @Test
    void testDuplicateTemplateVariableFails() {
        context.getRegistry().bind("myAssistant", createMockService(DuplicateVariableService.class));

        assertThatThrownBy(() -> {
            addRouteTo("langchain4j-service:myAssistant");
            context.start();
        })
                .as("@V('context') and @V('Context') capitalize to the same header, so the same header would "
                    + "silently feed two parameters - route startup must fail instead")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("duplicate template variable mappings")
                .hasMessageContaining("both map to header 'CamelLangChain4jServiceContext'");
    }

    @Test
    void testTemplateVariableCollidingWithMemoryIdHeaderFails() {
        context.getRegistry().bind("myAssistant", createMockService(MemoryIdCollisionService.class));

        assertThatThrownBy(() -> {
            addRouteTo("langchain4j-service:myAssistant");
            context.start();
        })
                .as("@V(\"memoryId\") maps to the header reserved for @MemoryId, so the same header would "
                    + "silently feed both bindings - route startup must fail instead")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("reserved for @MemoryId")
                .hasMessageContaining("Rename the template variable");
    }

    @Test
    void testTemplateVariableCollidingCaseInsensitivelyWithMemoryIdHeaderFails() {
        context.getRegistry().bind("myAssistant", createMockService(MemoryIdCaseCollisionService.class));

        assertThatThrownBy(() -> {
            addRouteTo("langchain4j-service:myAssistant");
            context.start();
        })
                .as("Camel headers are case-insensitive, so @V(\"memoryID\") would still read the header "
                    + "reserved for @MemoryId at runtime - route startup must fail like the exact-case collision")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("reserved for @MemoryId");
    }

    @Test
    void testDuplicateTemplateVariableDifferingOnlyInCaseFails() {
        context.getRegistry().bind("myAssistant", createMockService(DuplicateVariableDifferentCaseService.class));

        assertThatThrownBy(() -> {
            addRouteTo("langchain4j-service:myAssistant");
            context.start();
        })
                .as("Camel headers are case-insensitive, so @V('userName') and @V('username') feed from "
                    + "the same header entry at runtime - route startup must fail like the exact-case duplicate")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("duplicate template variable mappings")
                .hasMessageContaining("headers are case-insensitive");
    }

    @Test
    void testMultipleMemoryIdParametersFails() {
        context.getRegistry().bind("myAssistant", createMockService(DuplicateMemoryIdService.class));

        assertThatThrownBy(() -> {
            addRouteTo("langchain4j-service:myAssistant");
            context.start();
        })
                .as("two @MemoryId parameters would silently feed from the same header, "
                    + "so route startup must fail like the @UserMessage duplicate")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("has multiple @MemoryId parameters");
    }

    @Test
    void testEmptyTemplateVariableNameFails() {
        context.getRegistry().bind("myAssistant", createMockService(EmptyVariableNameService.class));

        assertThatThrownBy(() -> {
            addRouteTo("langchain4j-service:myAssistant");
            context.start();
        })
                .as("an empty @V name cannot map to any header, and the error must name the faulty parameter "
                    + "so the user can find it among several services")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("Parameter 'context'")
                .hasMessageContaining("has an empty @V template variable name");
    }

    @Test
    void testMissingServiceNameFails() {
        assertThatThrownBy(() -> {
            addRouteTo("langchain4j-service:?method=chat");
            context.start();
        })
                .as("an endpoint URI without a service name must be rejected with a clear message "
                    + "at endpoint creation, not fail later with a NullPointerException")
                .isInstanceOf(RuntimeCamelException.class)
                .hasMessageContaining("serviceName must be specified");
    }

    @Test
    void testPlainBeanInterfaceResolvedThroughClassHierarchy() throws Exception {
        // A plain bean (not a JDK proxy): resolution must walk the superclass to find the business
        // interface and filter out java.* interfaces and interfaces without business methods
        context.getRegistry().bind("myAssistant", new ConcreteGreetingBean());

        addRouteTo("langchain4j-service:myAssistant");
        context.start();

        String result = template.requestBody("direct:start", "Hello", String.class);

        assertThat(result)
                .as("the business interface inherited from the superclass must be resolved, with "
                    + "Serializable and the methodless marker interface discarded as candidates")
                .isEqualTo("greetings Hello");
    }

    @Test
    void testNonPublicInterfaceFails() {
        context.getRegistry().bind("myAssistant", createMockService(NonPublicService.class));

        assertThatThrownBy(() -> {
            addRouteTo("langchain4j-service:myAssistant");
            context.start();
        })
                .as("the producer invokes the interface reflectively, so a non-public interface must be "
                    + "rejected at startup instead of failing with IllegalAccessException on the first message")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("is not public")
                .hasMessageContaining("must be declared public");
    }

    @Test
    void testMultipleBusinessInterfacesFails() {
        context.getRegistry().bind("myAssistant", new MultiInterfaceBean());

        assertThatThrownBy(() -> {
            addRouteTo("langchain4j-service:myAssistant");
            context.start();
        })
                .as("a bean implementing two business interfaces is ambiguous, "
                    + "so route startup must fail and list the candidates")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("implements multiple business interfaces")
                .hasMessageContaining(FirstBusinessInterface.class.getName())
                .hasMessageContaining(SecondBusinessInterface.class.getName());
    }

    @Test
    void testNoBusinessInterfaceFails() {
        context.getRegistry().bind("myAssistant", new Object());

        assertThatThrownBy(() -> {
            addRouteTo("langchain4j-service:myAssistant");
            context.start();
        })
                .as("a bean implementing no interface cannot be an AI Service, "
                    + "so route startup must fail with a clear message")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("does not implement any valid business interface");
    }

    @Test
    void testDefaultAndStaticMethodsExcludedFromResolution() throws Exception {
        context.getRegistry().bind("myAssistant", createMockService(DefaultAndStaticMethodService.class));

        addRouteTo("langchain4j-service:myAssistant");
        context.start();

        String result = template.requestBody("direct:start", "Hello", String.class);

        assertThat(result)
                .as("default and static methods are not business methods: with them excluded, 'chat' is the "
                    + "single candidate and must be auto-selected without a method parameter")
                .isEqualTo("response from chat");
    }

    @Test
    void testLazyStartProducerDefersResolutionFailureToFirstMessage() throws Exception {
        // Do NOT register any service as 'missing'

        addRouteTo("langchain4j-service:missing?lazyStartProducer=true");
        context.start();

        assertThat(context.getRouteController().getRouteStatus(context.getRoutes().get(0).getRouteId()).isStarted())
                .as("with lazyStartProducer=true the route must start even though the AI Service "
                    + "cannot be resolved yet")
                .isTrue();

        assertThatThrownBy(() -> template.requestBody("direct:start", "Hello", String.class))
                .as("the deferred resolution failure must then surface on the first message, "
                    + "where routing error handlers can deal with it")
                .isInstanceOf(CamelExecutionException.class)
                .rootCause()
                .isInstanceOf(NoSuchBeanException.class)
                .hasMessageContaining("No bean could be found in the registry for: missing");
    }

    private void addRouteTo(String endpointUri) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to(endpointUri);
            }
        });
    }

    /**
     * Creates a mock AI Service using a dynamic proxy that answers "response from &lt;method name&gt;".
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
