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
package org.apache.camel.component.langchain.openai;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import static java.time.Duration.ofSeconds;

public class Langchain4jOpenAITest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(Langchain4jOpenAITest.class);

    private ChatLanguageModel openAiChatModel;

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        // TODO temporary ?
        var openAiApiKey = System.getenv("OPEN_API_KEY");

        if (openAiApiKey == null) {
            throw new RuntimeException("Please add an OpenAi Api Key");
        }

        openAiChatModel = OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(GPT_3_5_TURBO)
                .temperature(0.3)
                .timeout(ofSeconds(30000))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        this.context.getRegistry().bind("openAiChatModel", openAiChatModel);

        return new RouteBuilder() {
            public void configure() {
                from("direct:send-simple-message")
                        .to("langchain-openai:chat-simple?chatModel=#openAiChatModel&operation=CHAT_SINGLE_MESSAGE")
                        .log("response is ***** ${body}")
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

}
