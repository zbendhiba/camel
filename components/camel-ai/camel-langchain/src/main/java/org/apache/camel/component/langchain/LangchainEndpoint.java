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

import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.model.chat.ChatLanguageModel;
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

import static org.apache.camel.component.langchain.Langchain4jConstants.ENDPOINT_TYPE_CHAT;

@UriEndpoint(firstVersion = "4.5.0", scheme = "langchain",
             title = "langchain",
             syntax = "langchain:type", producerOnly = true,
             category = { Category.AI })
public class LangchainEndpoint extends DefaultEndpoint {

    @UriPath(label = "Langchain Endpoint type. There's only one type available today which is the chat",
             enums = "" + ENDPOINT_TYPE_CHAT)
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
}
