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

import java.util.Map;

import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.apache.camel.Exchange;
import org.apache.camel.component.langchain.service.Langchain4jChatHandler;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.langchain.Langchain4jConstants.ENDPOINT_TYPE_CHAT;
import static org.apache.camel.component.langchain.LangchainChatOperations.CHAT_MULTIPLE_MESSAGES;
import static org.apache.camel.component.langchain.LangchainChatOperations.CHAT_SINGLE_MESSAGE;
import static org.apache.camel.component.langchain.LangchainChatOperations.CHAT_SINGLE_MESSAGE_WITH_PROMPT;

public class LangchainProducer extends DefaultProducer {

    private LangchainEndpoint endpoint;

    private ChatLanguageModel chatLanguageModel;

    private Langchain4jChatHandler langchain4jChatHandler;

    public LangchainProducer(LangchainEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        var endpointType = this.endpoint.getType();
        if (ENDPOINT_TYPE_CHAT.equals(endpointType)) {
            processChat(exchange);
        }

    }

    private void processChat(Exchange exchange) {
        var operation = this.endpoint.getChatOperation();
        // make sure operation is not null
        ObjectHelper.notNull(operation, "chatOperation");

        // Processing one single message

        if (CHAT_SINGLE_MESSAGE.equals(operation)) {

            processSingleMessage(exchange);
        } else if (CHAT_SINGLE_MESSAGE_WITH_PROMPT.equals(operation)) {
            processSingleMessageWithPrompt(exchange);
        } else if (CHAT_MULTIPLE_MESSAGES.equals(operation)) {

            processMultipleMessages(exchange);
        }

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
            response = langchain4jChatHandler.sendWithPromptTemplate(promptTemplate, variables, chatMessageType);
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
            response = langchain4jChatHandler.sendMessage(message, chatMessageType);

        } else {
            response = langchain4jChatHandler.sendMessage(message);
        }

        exchange.getIn().setBody(response);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        chatLanguageModel = this.endpoint.getChatModel();

        langchain4jChatHandler = new Langchain4jChatHandler(this.chatLanguageModel);

    }

    public ChatLanguageModel getChatLanguageModel() {
        return chatLanguageModel;
    }

    public void setChatLanguageModel(ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
    }

    public Langchain4jChatHandler getLangchain4jChatHandler() {
        return langchain4jChatHandler;
    }

    public void setLangchain4jChatHandler(Langchain4jChatHandler langchain4jChatHandler) {
        this.langchain4jChatHandler = langchain4jChatHandler;
    }
}
