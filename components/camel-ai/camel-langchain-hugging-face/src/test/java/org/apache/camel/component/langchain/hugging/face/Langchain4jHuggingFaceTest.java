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
package org.apache.camel.component.langchain.hugging.face;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.huggingface.HuggingFaceChatModel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static dev.langchain4j.model.huggingface.HuggingFaceModelName.TII_UAE_FALCON_7B_INSTRUCT;
import static java.time.Duration.ofSeconds;

public class Langchain4jHuggingFaceTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(Langchain4jHuggingFaceTest.class);

    private ChatLanguageModel huggingFaceChatModel;

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        // TODO temporary ?
        var openAiApiKey = System.getenv("HF_API_KEY");

        if (openAiApiKey == null) {
            throw new RuntimeException("Please add an Hugging Face Api Key");
        }

        huggingFaceChatModel = HuggingFaceChatModel.builder()
                .accessToken(System.getenv("HF_API_KEY"))
                .modelId(TII_UAE_FALCON_7B_INSTRUCT)
                .timeout(ofSeconds(300))
                .temperature(0.7)
                .maxNewTokens(20)
                .waitForModel(true)
                .build();

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        this.context.getRegistry().bind("huggingFaceChatModel", huggingFaceChatModel);

        return new RouteBuilder() {
            public void configure() {
                from("direct:send-simple-message")
                        .to("langchain-hugging-face:chat-simple?chatModel=#huggingFaceChatModel&operation=CHAT_SINGLE_MESSAGE")
                        .to("mock:response");

                from("direct:send-message-prompt")
                        .to("langchain-hugging-face:chat-prompt?chatModel=#huggingFaceChatModel&operation=CHAT_SINGLE_MESSAGE_WITH_PROMPT")
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

    /* @Test
    void testSendMessageWithPrompt() throws InterruptedException {
        MockEndpoint mockErrorHandler = this.context.getEndpoint("mock:response", MockEndpoint.class);
        mockErrorHandler.expectedMessageCount(1);

        // Example copied from Langchain4j examples
        var promptTemplate = "Create a recipe for a {{dishType}} with the following ingredients: {{ingredients}}";

        Map<String, Object> variables = new HashMap<>();
        variables.put("dishType", "oven dish");
        variables.put("ingredients", "potato, tomato, feta, olive oil");

        String response = template.requestBodyAndHeader("direct:send-message-prompt", variables, Langchain4jConstants.PROMPT_TEMPLATE, promptTemplate, String.class);
        mockErrorHandler.assertIsSatisfied();
        //TODO drop this line
        System.out.println("Response " + response);

        assertTrue(response.contains("potato"));
        assertTrue(response.contains("tomato"));
        assertTrue(response.contains("feta"));
        assertTrue(response.contains("olive oil"));
    }*/
}
