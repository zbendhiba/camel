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

import java.util.Arrays;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for @Tool bean discovery and toolNames-based tool selection.
 *
 * Tests that Spring @Tool annotated beans can be discovered via ToolCallbackProvider and selected by name using the
 * toolNames parameter.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Disabled unless running in CI")
public class SpringAiChatToolBeanDiscoveryIT extends OllamaTestSupport {

    @Test
    public void testToolCallbackProviderDiscovery() {
        String response = template().requestBody("direct:providerChat",
                "What is the current date and time?", String.class);

        assertThat(response).isNotNull();
        // The LLM should use the tool and include date/time info
        assertThat(response).isNotEmpty();
    }

    @Test
    public void testToolSelectionByName() {
        String response = template().requestBody("direct:namedChat",
                "What is the capital of France?", String.class);

        assertThat(response).isNotNull();
        assertThat(response.toLowerCase()).containsAnyOf("paris", "capital", "france");
    }

    @Test
    public void testToolNamesViaHeader() {
        var exchange = template().request("direct:headerChat", e -> {
            e.getIn().setBody("What is the capital of Germany?");
            e.getIn().setHeader(SpringAiChatConstants.TOOL_NAMES, "getCapital");
        });

        assertThat(exchange).isNotNull();
        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();
        assertThat(response.toLowerCase()).containsAnyOf("berlin", "capital", "germany");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                bindChatModel(getCamelContext());

                // Resolve ToolCallbacks from @Tool-annotated bean
                ToolCallbackProvider provider = MethodToolCallbackProvider.builder()
                        .toolObjects(new MyTools())
                        .build();
                getCamelContext().getRegistry().bind("myTools", Arrays.asList(provider.getToolCallbacks()));

                // Chat with ToolCallbacks resolved from @Tool bean
                from("direct:providerChat")
                        .to("spring-ai-chat:providerChat?chatModel=#chatModel&toolCallbacks=#myTools");

                // Chat with toolNames selection
                from("direct:namedChat")
                        .to("spring-ai-chat:namedChat?chatModel=#chatModel&toolCallbacks=#myTools&toolNames=getCapital");

                // Chat without toolNames - selected via header at runtime
                from("direct:headerChat")
                        .to("spring-ai-chat:headerChat?chatModel=#chatModel&toolCallbacks=#myTools");
            }
        };
    }

    /**
     * Spring AI @Tool annotated bean for testing.
     */
    public static class MyTools {

        @Tool(description = "Get the current date and time")
        public String getCurrentDateTime() {
            return "The current date and time is 2026-03-11T10:30:00Z";
        }

        @Tool(description = "Get the capital city of a country")
        public String getCapital(String country) {
            return switch (country.toLowerCase()) {
                case "france" -> "The capital of France is Paris";
                case "germany" -> "The capital of Germany is Berlin";
                case "italy" -> "The capital of Italy is Rome";
                default -> "Capital not found for " + country;
            };
        }
    }
}
