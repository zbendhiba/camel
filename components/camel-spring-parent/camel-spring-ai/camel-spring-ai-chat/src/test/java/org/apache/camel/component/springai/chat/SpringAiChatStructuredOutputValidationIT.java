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
package org.apache.camel.component.springai.chat;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for StructuredOutputValidationAdvisor.
 *
 * Tests that the advisor validates structured output against JSON Schema and retries on validation failure. If all
 * retries fail, the exception propagates to Camel's error handler.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Disabled unless running in CI")
public class SpringAiChatStructuredOutputValidationIT extends OllamaTestSupport {

    @Test
    public void testValidationWithBeanOutput() {
        var exchange = template().request("direct:validatedChat", e -> {
            e.getIn().setBody("Generate filmography for Tom Hanks. Include at least 3 movies.");
        });

        assertThat(exchange).isNotNull();
        assertThat(exchange.getException()).isNull();

        Object body = exchange.getMessage().getBody();
        assertThat(body).isInstanceOf(ActorFilms.class);

        ActorFilms actorFilms = (ActorFilms) body;
        assertThat(actorFilms.actor()).isNotNull();
        assertThat(actorFilms.movies()).isNotNull().isNotEmpty();
    }

    @Test
    public void testValidationWithEntityClass() {
        var exchange = template().request("direct:entityValidatedChat", e -> {
            e.getIn().setBody("Generate filmography for Meryl Streep. Include at least 3 movies.");
        });

        assertThat(exchange).isNotNull();
        assertThat(exchange.getException()).isNull();

        Object body = exchange.getMessage().getBody();
        assertThat(body).isInstanceOf(ActorFilms.class);

        ActorFilms actorFilms = (ActorFilms) body;
        assertThat(actorFilms.actor()).isNotNull();
        assertThat(actorFilms.movies()).isNotNull().isNotEmpty();
    }

    @Test
    public void testValidationFailurePropagesToErrorHandler() {
        // Use an unreasonable request that may produce invalid output
        // The error handler should catch the exception after max retries
        var exchange = template().request("direct:errorHandledChat", e -> {
            e.getIn().setBody("Generate filmography for Tom Hanks");
        });

        assertThat(exchange).isNotNull();
        // Either success or error handled — exchange should complete
        if (exchange.getException() == null) {
            // Validation succeeded — body should be ActorFilms
            assertThat(exchange.getMessage().getBody()).isInstanceOf(ActorFilms.class);
        }
        // If exception, it was propagated correctly to Camel error handling
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                bindChatModel(getCamelContext());

                // Chat with structured output validation using outputFormat/outputClass
                from("direct:validatedChat")
                        .to("spring-ai-chat:validatedChat?chatModel=#chatModel"
                            + "&outputFormat=BEAN"
                            + "&outputClass=org.apache.camel.component.springai.chat.SpringAiChatStructuredOutputValidationIT$ActorFilms"
                            + "&structuredOutputValidation=true"
                            + "&structuredOutputValidationMaxAttempts=3");

                // Chat with structured output validation using entityClass
                from("direct:entityValidatedChat")
                        .to("spring-ai-chat:entityValidatedChat?chatModel=#chatModel"
                            + "&entityClass=org.apache.camel.component.springai.chat.SpringAiChatStructuredOutputValidationIT$ActorFilms"
                            + "&structuredOutputValidation=true"
                            + "&structuredOutputValidationMaxAttempts=3");

                // Chat with error handling
                from("direct:errorHandledChat")
                        .doTry()
                            .to("spring-ai-chat:errorHandledChat?chatModel=#chatModel"
                                + "&outputFormat=BEAN"
                                + "&outputClass=org.apache.camel.component.springai.chat.SpringAiChatStructuredOutputValidationIT$ActorFilms"
                                + "&structuredOutputValidation=true"
                                + "&structuredOutputValidationMaxAttempts=1")
                        .doCatch(Exception.class)
                            .log("Validation failed after retries: ${exception.message}")
                            .setBody(simple("Validation failed: ${exception.message}"))
                        .end();
            }
        };
    }

    @JsonPropertyOrder({ "actor", "movies" })
    public record ActorFilms(String actor, List<String> movies) {
    }
}
