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

import java.util.List;
import java.util.Map;

import dev.langchain4j.chain.ConversationalRetrievalChain;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.apache.camel.Exchange;
import org.apache.camel.component.langchain.service.Langchain4jChatHandler;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.langchain.Langchain4jConstants.ENDPOINT_TYPE_CHAIN;
import static org.apache.camel.component.langchain.Langchain4jConstants.ENDPOINT_TYPE_CHAT;
import static org.apache.camel.component.langchain.Langchain4jConstants.ENDPOINT_TYPE_EMBED;
import static org.apache.camel.component.langchain.LangchainChatOperations.CHAT_MULTIPLE_MESSAGES;
import static org.apache.camel.component.langchain.LangchainChatOperations.CHAT_SINGLE_MESSAGE;
import static org.apache.camel.component.langchain.LangchainChatOperations.CHAT_SINGLE_MESSAGE_WITH_PROMPT;

public class LangchainProducer extends DefaultProducer {

    private LangchainEndpoint endpoint;

    private ChatLanguageModel chatLanguageModel;

    private EmbeddingModel embeddingModel;

    private ConversationalRetrievalChain conversationalRetrievalChain;

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

        if (ENDPOINT_TYPE_EMBED.equals(endpointType)) {
            processEmbed(exchange);
        }

        if (ENDPOINT_TYPE_CHAIN.equals(endpointType)) {
            processConversationalRetriever(exchange);
        }

    }

    private void processEmbed(Exchange exchange) {
        // make sure embedding Model is not null
        ObjectHelper.notNull(this.embeddingModel, "embeddingModel");

        var operation = this.endpoint.getEmbedOperation();

        // make sure operation is not null
        ObjectHelper.notNull(operation, "embedOperation");

        Map<String, String> metadataValues = exchange.getIn().getHeader(Langchain4jConstants.EMBEDDING_METADATA, Map.class);
        Metadata metadata = metadataValues != null ? new Metadata(metadataValues) : new Metadata();

        if (LangchainEmbedOperations.EMBED_SINGLE_STRING.equals(operation)) {
            exchange.getMessage().setBody(processEmbedSingleString(metadata, exchange));
        }

        if (LangchainEmbedOperations.EMBED_MULTIPLE_STRING.equals(operation)) {
            exchange.getMessage().setBody(processEmbedMultipleString(metadata, exchange));
        }

        if (LangchainEmbedOperations.EMBED_SINGLE_TEXT_SEGMENT.equals(operation)) {
            exchange.getMessage().setBody(processEmbedSingTextSegment(exchange));
        }

        if (LangchainEmbedOperations.EMBED_MULTIPLE_TEXT_SEGMENT.equals(operation)) {
            exchange.getMessage().setBody(processEmbedMultipleTextSegment(exchange));
        }
    }

    private Embedding processEmbedSingleString(Metadata metadata, Exchange exchange) {
        String data = exchange.getIn().getBody(String.class);
        TextSegment textSegment = new TextSegment(data, metadata);
        return embeddingModel.embed(textSegment).content();
    }

    private List<Embedding> processEmbedMultipleString(Metadata metadata, Exchange exchange) {
        List<String> data = exchange.getIn().getBody(List.class);
        List<TextSegment> textSegments = data.stream().map(d -> new TextSegment(d, metadata))
                .toList();
        return embeddingModel.embedAll(textSegments).content();
    }

    private Embedding processEmbedSingTextSegment(Exchange exchange) {
        TextSegment textSegment = exchange.getIn().getBody(TextSegment.class);
        return embeddingModel.embed(textSegment).content();
    }

    private List<Embedding> processEmbedMultipleTextSegment(Exchange exchange) {
        List<TextSegment> textSegments = exchange.getIn().getBody(List.class);
        return embeddingModel.embedAll(textSegments).content();
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

    private void processConversationalRetriever(Exchange exchange) {
        // make sure the chain is not null
        ObjectHelper.notNull(conversationalRetrievalChain, "chain");
        String question = exchange.getIn().getBody(String.class);
        var response = conversationalRetrievalChain.execute(question);
        exchange.getMessage().setBody(response);
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

        chatLanguageModel = this.endpoint.getChatModel();

        embeddingModel = this.endpoint.getEmbeddingModel();

        conversationalRetrievalChain = this.endpoint.getChain();

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
