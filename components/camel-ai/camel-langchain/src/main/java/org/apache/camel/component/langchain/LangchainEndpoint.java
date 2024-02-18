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

import dev.langchain4j.chain.ConversationalRetrievalChain;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.langchain.Langchain4jConstants.ENDPOINT_TYPE_CHAIN;
import static org.apache.camel.component.langchain.Langchain4jConstants.ENDPOINT_TYPE_CHAT;
import static org.apache.camel.component.langchain.Langchain4jConstants.ENDPOINT_TYPE_DATA_INGEST;
import static org.apache.camel.component.langchain.Langchain4jConstants.ENDPOINT_TYPE_EMBED;
import static org.apache.camel.component.langchain.Langchain4jConstants.ENDPOINT_TYPE_STORE;

@UriEndpoint(firstVersion = "4.5.0", scheme = "langchain",
             title = "langchain",
             syntax = "langchain:type", producerOnly = true,
             category = { Category.AI })
public class LangchainEndpoint extends DefaultEndpoint {

    @UriPath(label = "Langchain Endpoint type",
             enums = "" + ENDPOINT_TYPE_DATA_INGEST + "," + ENDPOINT_TYPE_CHAT + " ," + ENDPOINT_TYPE_EMBED + " , "
                     + ENDPOINT_TYPE_STORE + ", " + ENDPOINT_TYPE_CHAIN)
    @Metadata(required = true)
    private String type;

    @UriParam
    @Metadata(required = true, defaultValue = "CHAT_SINGLE_MESSAGE")
    private LangchainChatOperations chatOperation = LangchainChatOperations.CHAT_SINGLE_MESSAGE;

    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private ChatLanguageModel chatModel;

    @UriParam
    private ChatMessageType chatMessageType;

    @UriParam
    @Metadata(required = true, defaultValue = "EMBED_SINGLE_STRING")
    private LangchainEmbedOperations embedOperation = LangchainEmbedOperations.EMBED_SINGLE_STRING;

    @UriParam(label = "advanced")
    private EmbeddingModel embeddingModel;

    @UriParam(label = "advanced")
    private ConversationalRetrievalChain chain;

    public LangchainEndpoint(String uri, LangchainComponent component, String type) {
        super(uri, component);
        this.type = type;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new LangchainProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Cannot consume from an LangchainEndpoint: " + getEndpointUri());
    }

    @Override
    public void doInit() throws Exception {
        super.doInit();
        ObjectHelper.notNull(type, "type");

    }

    /**
     * Type
     *
     * @return
     */
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * Operation in case of Endpoint of type CHAT. value is one the values of
     * org.apache.camel.component.langchain.LangchainChatOperations
     *
     * @return
     */
    public LangchainChatOperations getChatOperation() {
        return chatOperation;
    }

    public void setChatOperation(LangchainChatOperations chatOperation) {
        this.chatOperation = chatOperation;
    }

    /**
     * Operation in case of Endpoint of type EMBED. value is one the values of
     * org.apache.camel.component.langchain.LangchainEmbedOperations
     *
     * @return
     */
    public LangchainEmbedOperations getEmbedOperation() {
        return embedOperation;
    }

    public void setEmbedOperation(LangchainEmbedOperations embedOperation) {
        this.embedOperation = embedOperation;
    }

    /**
     * Values from dev.langchain4j.data.message.ChatMessageType
     *
     * @return
     */
    public ChatMessageType getChatMessageType() {
        return chatMessageType;
    }

    public void setChatMessageType(ChatMessageType chatMessageType) {
        this.chatMessageType = chatMessageType;
    }

    /**
     * Chat Language Model of type dev.langchain4j.model.chat.ChatLanguageModel
     *
     * @return
     */
    public ChatLanguageModel getChatModel() {
        return chatModel;
    }

    public void setChatModel(ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * EmbeddingModel
     *
     * @return
     */
    public EmbeddingModel getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }


    /**
     * Conversational Retrieval chain in case the type of endpoint is chain
     *
     * @return
     */
    public ConversationalRetrievalChain getChain() {
        return chain;
    }

    public void setChain(ConversationalRetrievalChain chain) {
        this.chain = chain;
    }
}
