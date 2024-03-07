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
package org.apache.camel.component.chat;

import java.util.Map;

import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.component.chat.service.Langchain4jChatHandler;
import org.apache.camel.support.DefaultProducer;

public class LangchainChatProducer extends DefaultProducer {

    private final LangchainChatEndpoint endpoint;

    private Langchain4jChatHandler langchain4jChatHandler;

    public LangchainChatProducer(LangchainChatEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        var operation = this.endpoint.getConfiguration().getChatOperation();

        if (LangchainChatOperations.CHAT_SINGLE_MESSAGE.equals(operation)) {
            processSingleMessage(exchange);
        } else if (LangchainChatOperations.CHAT_SINGLE_MESSAGE_WITH_PROMPT.equals(operation)) {
            processSingleMessageWithPrompt(exchange);
        } else if (LangchainChatOperations.CHAT_MULTIPLE_MESSAGES.equals(operation)) {
            processMultipleMessages(exchange);
        }
    }

    private void processMultipleMessages(Exchange exchange) {
        //TODO
    }

    @SuppressWarnings("unchecked")
    private void processSingleMessageWithPrompt(Exchange exchange) throws NoSuchHeaderException, InvalidPayloadException {
        ChatMessageType chatMessageType = this.endpoint.getConfiguration().getChatMessageType();

        final String promptTemplate = exchange.getIn().getHeader(LangchainChat.Headers.PROMPT_TEMPLATE, String.class);

        if (promptTemplate == null) {
            throw new NoSuchHeaderException("The action is a required header", exchange, LangchainChat.Headers.PROMPT_TEMPLATE);
        }

        Map<String, Object> variables = (Map<String, Object>) exchange.getIn().getMandatoryBody(Map.class);

        var response = chatMessageType != null
                ? langchain4jChatHandler.sendWithPromptTemplate(promptTemplate, variables, chatMessageType)
                : langchain4jChatHandler.sendWithPromptTemplate(promptTemplate, variables);

        exchange.getIn().setBody(response);
    }

    private void processSingleMessage(Exchange exchange) throws InvalidPayloadException {
        ChatMessageType chatMessageType = this.endpoint.getConfiguration().getChatMessageType();
        final String message = exchange.getIn().getMandatoryBody(String.class);

        var response = chatMessageType != null
                ? langchain4jChatHandler.sendMessage(message, chatMessageType) : langchain4jChatHandler.sendMessage(message);

        exchange.getIn().setBody(response);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        ChatLanguageModel chatLanguageModel = this.endpoint.getConfiguration().getChatModel();
        langchain4jChatHandler = new Langchain4jChatHandler(chatLanguageModel);
    }

}
