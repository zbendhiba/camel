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

import java.io.IOException;

import dev.langchain4j.service.UserMessage;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests exception propagation from AI Service methods to Camel error handlers.
 */
class LangChain4jAiServiceExceptionTest extends CamelTestSupport {

    interface FailingService {
        String chat(@UserMessage String text);
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        // Mock AI Service that throws RuntimeException
        FailingService runtimeExceptionService = text -> {
            throw new RuntimeException("AI model failure");
        };
        registry.bind("runtimeExceptionService", runtimeExceptionService);

        // Mock AI Service that throws IllegalArgumentException
        FailingService illegalArgumentService = text -> {
            throw new IllegalArgumentException("Invalid input");
        };
        registry.bind("illegalArgumentService", illegalArgumentService);

        // Mock AI Service that throws chained exception
        FailingService chainedExceptionService = text -> {
            throw new RuntimeException("Outer", new IOException("Inner"));
        };
        registry.bind("chainedExceptionService", chainedExceptionService);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Route with error handler for RuntimeException - for testExceptionPropagation
                from("direct:runtimeException")
                        .onException(RuntimeException.class)
                            .handled(true)
                            .to("mock:error")
                        .end()
                        .to("langchain4j-ai-service:runtimeExceptionService");

                // Routes without error handlers - exceptions should propagate to caller
                from("direct:illegalArgument")
                        .to("langchain4j-ai-service:illegalArgumentService");

                from("direct:chainedException")
                        .to("langchain4j-ai-service:chainedExceptionService");
            }
        };
    }

    /**
     * AC-7: Tests that exceptions propagate to Camel error handler. Verifies that RuntimeException from AI Service
     * method triggers onException handler.
     */
    @Test
    void testExceptionPropagation() throws Exception {
        MockEndpoint mockError = getMockEndpoint("mock:error");
        mockError.expectedMessageCount(1);

        template.sendBody("direct:runtimeException", "test message");

        mockError.assertIsSatisfied();

        // Verify the exception was captured by the error handler
        Exchange errorExchange = mockError.getReceivedExchanges().get(0);
        Exception exception = errorExchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);

        assertThat(exception)
                .isNotNull()
                .isInstanceOf(RuntimeException.class)
                .hasMessage("AI model failure");
    }

    /**
     * Tests that exception message is preserved when propagating to exchange. Verifies that the original exception type
     * and message are not wrapped or transformed.
     */
    @Test
    void testExceptionMessagePreserved() {
        assertThatThrownBy(() -> template.sendBody("direct:illegalArgument", "test message"))
                .isInstanceOf(CamelExecutionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("Invalid input");
    }

    /**
     * Tests that exception cause chain is preserved. Verifies that nested exceptions maintain their cause relationship.
     */
    @Test
    void testExceptionCauseChained() {
        assertThatThrownBy(() -> template.sendBody("direct:chainedException", "test message"))
                .isInstanceOf(CamelExecutionException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .cause()
                .hasMessage("Outer")
                .hasCauseInstanceOf(IOException.class)
                .cause()
                .hasMessage("Inner");
    }
}
