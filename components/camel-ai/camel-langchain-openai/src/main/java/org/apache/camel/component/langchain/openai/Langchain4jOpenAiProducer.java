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

import java.time.Duration;
import java.util.Map;

import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.apache.camel.Exchange;
import org.apache.camel.component.langchain.commons.Langchain4jConstants;
import org.apache.camel.component.langchain.commons.service.Langchain4jChatHandler;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

public class Langchain4jOpenAiProducer extends DefaultProducer {
    private Langchain4jOpenAiEndpoint endpoint;

    private ChatLanguageModel openAiModel;

    private Langchain4jChatHandler langchain4jChatHandler;

    public Langchain4jOpenAiProducer(Langchain4jOpenAiEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // Processing one single message
        switch (this.endpoint.getOperation()) {
            case CHAT_SINGLE_MESSAGE:
                processSingleMessage(exchange);
            case CHAT_SINGLE_MESSAGE_WITH_PROMPT:
                processSingleMessageWithPrompt(exchange);
            case CHAT_MULTIPLE_MESSAGES:
                processMultipleMessages(exchange);
            case EMBEDDING:
                processEmbedding(exchange);
                // this one should maybe be removed because we basically can chain ourselves in Camel for the retrieval
            case CONVERSATIONAL_RETRIEVER:
                processConversationalRetriever(exchange);
        }
        ;

    }

    private void processConversationalRetriever(Exchange exchange) {
        //TODO
    }

    private void processEmbedding(Exchange exchange) {
        //TODO

    }

    private void processMultipleMessages(Exchange exchange) {
        //TODO
    }

    private void processSingleMessageWithPrompt(Exchange exchange) {
        ChatMessageType chatMessageType = this.endpoint.getChatMessageType();
        String promptTemplate = exchange.getIn().getHeader(Langchain4jConstants.PROMPT_TEMPLATE, String.class);
        ObjectHelper.notNull(promptTemplate, "Prompt variables");
        var variables = exchange.getIn().getBody(Map.class);
        ObjectHelper.notNull(variables, "Prompt variables");

        var response = "";

        if (chatMessageType != null) {
            //TODO -- check if that is even something normal to do
        } else {
            response = langchain4jChatHandler.sendWithPromptTemplate(promptTemplate, variables);
        }

        exchange.getIn().setBody(response);


    }

    private void processSingleMessage(Exchange exchange) {

        ChatMessageType chatMessageType = this.endpoint.getChatMessageType();

        var response = "";

            var message = exchange.getIn().getBody(String.class);
            ObjectHelper.notNull(message, "Message");
            if (chatMessageType != null) {
                //TODO -- check if that is even something normal to do
            } else {
                response = langchain4jChatHandler.sendMessage(message);
            }

        exchange.getIn().setBody(response);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        openAiModel = this.endpoint.getChatModel();

        if (this.openAiModel == null) {
            // create Chat ModeL . This requires at least an API Key
            ObjectHelper.notNull(this.endpoint.getOpenAiKey(), "openAiKey");

            openAiModel = OpenAiChatModel.builder()
                    .apiKey(this.endpoint.getOpenAiKey())
                    .modelName(this.endpoint.getOpenAiModelName())
                    .baseUrl(this.endpoint.getBaseUrl())
                    .organizationId(this.endpoint.getOrganizationId())
                    .temperature(this.endpoint.getTemperature())
                    .topP(this.endpoint.getTopP())
                    .stop(this.endpoint.getStop())
                    .maxTokens(this.endpoint.getMaxTokens())
                    .presencePenalty(this.endpoint.getPresencePenalty())
                    .frequencyPenalty(this.endpoint.getFrequencyPenalty())
                    .logitBias(this.endpoint.getLogitBias())
                    .responseFormat(this.endpoint.getResponseFormat())
                    .seed(this.endpoint.getSeed())
                    .user(this.endpoint.getUser())
                    .timeout(Duration.ofMillis(this.endpoint.getTimeout()))
                    .maxRetries(this.endpoint.getMaxRetries())
                    .logRequests(this.endpoint.getLogRequests())
                    .logResponses(this.endpoint.getLogResponses())
                    .tokenizer(this.endpoint.getTokenizer())
                    .build();
        }

        langchain4jChatHandler = new Langchain4jChatHandler(this.openAiModel);

    }

}
