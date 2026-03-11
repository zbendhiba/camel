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
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for ToolContext support.
 *
 * Tests that contextual data (e.g., user ID, session info) is passed to @Tool methods that accept a ToolContext
 * parameter.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Disabled unless running in CI")
public class SpringAiChatToolContextIT extends OllamaTestSupport {

    @Test
    public void testToolContextFromConfig() {
        // The route has toolContext configured with userId=user-42
        String response = template().requestBody("direct:contextChat",
                "Get my user profile", String.class);

        assertThat(response).isNotNull();
        // The tool should receive the context and include user-42 in its response
        assertThat(response.toLowerCase()).containsAnyOf("user-42", "user", "profile");
    }

    @Test
    public void testToolContextFromHeader() {
        var exchange = template().request("direct:headerContextChat", e -> {
            e.getIn().setBody("Get my user profile");
            e.getIn().setHeader(SpringAiChatConstants.TOOL_CONTEXT, Map.of("userId", "user-99", "role", "admin"));
        });

        assertThat(exchange).isNotNull();
        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();
        assertThat(response.toLowerCase()).containsAnyOf("user-99", "admin", "user", "profile");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                bindChatModel(getCamelContext());

                // Resolve ToolCallbacks from @Tool-annotated bean
                var provider = MethodToolCallbackProvider.builder()
                        .toolObjects(new ContextAwareTools())
                        .build();
                getCamelContext().getRegistry().bind("contextTools",
                        Arrays.asList(provider.getToolCallbacks()));

                // Chat with tool context configured on endpoint
                from("direct:contextChat")
                        .to("spring-ai-chat:contextChat?chatModel=#chatModel"
                            + "&toolCallbacks=#contextTools"
                            + "&toolContext.userId=user-42"
                            + "&toolContext.role=viewer");

                // Chat with tool context from header (no endpoint config)
                from("direct:headerContextChat")
                        .to("spring-ai-chat:headerContextChat?chatModel=#chatModel"
                            + "&toolCallbacks=#contextTools");
            }
        };
    }

    public static class ContextAwareTools {

        @Tool(description = "Get user profile information for the current user")
        public String getUserProfile(
                @ToolParam(description = "optional detail level") String detail,
                ToolContext toolContext) {
            String userId = (String) toolContext.getContext().getOrDefault("userId", "unknown");
            String role = (String) toolContext.getContext().getOrDefault("role", "guest");
            return String.format("User profile: userId=%s, role=%s", userId, role);
        }
    }
}
