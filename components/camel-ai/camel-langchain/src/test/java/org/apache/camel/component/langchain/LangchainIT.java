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
package org.apache.camel.component.langchain;

import java.util.HashMap;
import java.util.Map;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class LangchainIT extends OllamaTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(LangchainIT.class);

    private ChatLanguageModel chatLanguageModel;

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        chatLanguageModel = createModel();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        this.context.getRegistry().bind("chatModel", chatLanguageModel);

        return new RouteBuilder() {
            public void configure() {
                from("direct:send-simple-message")
                        .to("langchain://chat?chatModel=#chatModel&chatOperation=CHAT_SINGLE_MESSAGE")
                        .to("mock:response");

                from("direct:send-message-prompt")
                        .to("langchain://chat?chatModel=#chatModel&chatOperation=CHAT_SINGLE_MESSAGE_WITH_PROMPT")
                        .to("mock:response");
            };
        };
    }

    @Test
    void testSendMessage() throws InterruptedException {
        MockEndpoint mockErrorHandler = this.context.getEndpoint("mock:response", MockEndpoint.class);
        mockErrorHandler.expectedMessageCount(1);

        String response = template.requestBody("direct:send-simple-message", "Hello my name is Dark Vader!", String.class);
        mockErrorHandler.assertIsSatisfied();
        //TODO drop this line
        System.out.println("Response " + response);
    }

    @Test
    void testSendMessageWithPrompt() throws InterruptedException {
        MockEndpoint mockErrorHandler = this.context.getEndpoint("mock:response", MockEndpoint.class);
        mockErrorHandler.expectedMessageCount(1);

        // Example copied from Langchain4j examples
        var promptTemplate = "Create a recipe for a {{dishType}} with the following ingredients: {{ingredients}}";

        Map<String, Object> variables = new HashMap<>();
        variables.put("dishType", "oven dish");
        variables.put("ingredients", "potato, tomato, feta, olive oil");

        String response = template.requestBodyAndHeader("direct:send-message-prompt", variables,
                Langchain4jConstants.PROMPT_TEMPLATE, promptTemplate, String.class);
        mockErrorHandler.assertIsSatisfied();
        //TODO drop this line
        System.out.println("Response " + response);

        assertTrue(response.contains("potato"));
        assertTrue(response.contains("tomato"));
        assertTrue(response.contains("feta"));
        assertTrue(response.contains("olive oil"));
    }
}
