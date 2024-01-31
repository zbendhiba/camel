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

import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.model.huggingface.HuggingFaceChatModel;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.langchain.commons.Langchain4jConstants;
import org.apache.camel.component.langchain.commons.Langchain4jOperations;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

@UriEndpoint(firstVersion = "4.4.0", scheme = "langchain-hugging-face",
             title = "langchain-hugging-face",
             syntax = "langchain-hugging-face:name", producerOnly = true,
             category = { Category.AI })
public class Langchain4jHuggingFaceEndpoint extends DefaultEndpoint {

    @UriPath
    @Metadata(required = true)
    private String name;

    @UriParam
    @Metadata(required = true)
    private Langchain4jOperations operation;

    @UriParam
    private HuggingFaceChatModel chatModel;

    @UriParam
    private ChatMessageType chatMessageType;

    @UriParam (defaultValue = "" + Langchain4jConstants.TIMEOUT)
    private Integer timeout = Langchain4jConstants.TIMEOUT;

    @UriParam
    private String modelId;

    @UriParam
    private Double temperature;
    @UriParam(label = "security", secret = true)
    private String accessToken;
    @UriParam (defaultValue =  "" + Langchain4jConstants.NB_RETRY)
    private Integer maxNewRetries = Langchain4jConstants.NB_RETRY;

    @UriParam (defaultValue =  "" + Langchain4jConstants.RETURN_FULL_TEXT)
    private Boolean returnFullText = Langchain4jConstants.RETURN_FULL_TEXT;


    public Langchain4jHuggingFaceEndpoint(String uri, Langchain4HuggingFaceComponent component) {
        super(uri, component);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new Langchain4jHuggingFaceProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Cannot consume from an Langchain4jHuggingFaceEndpoint: " + getEndpointUri());
    }

    @Override
    public void doInit() throws Exception {
        super.doInit();
        ObjectHelper.notNull(operation, "operation");

    }

    /**
     * TODO
     *
     * @return
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Operation value is one the values of org.apache.camel.component.langchain.commons.Langchain4jChatOperations
     *
     * @return
     */
    public Langchain4jOperations getOperation() {
        return operation;
    }

    public void setOperation(Langchain4jOperations operation) {
        this.operation = operation;
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
     * Timeout
     * @return
     */
    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    /**
     * HuggingFace model ID - check values from dev.langchain4j.model.huggingface.HuggingFaceChatModelIT
     * @return
     */
    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    /**
     * Temperature
     * @return
     */
    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    /**
     * HuggingFace Access Token
     * @return
     */
    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    /**
     * Max retries
     * @return
     */
    public Integer getMaxNewRetries() {
        return maxNewRetries;
    }

    public void setMaxNewRetries(Integer maxNewRetries) {
        this.maxNewRetries = maxNewRetries;
    }

    /**
     * Return full text
     * @return
     */
    public Boolean getReturnFullText() {
        return returnFullText;
    }

    public void setReturnFullText(Boolean returnFullText) {
        this.returnFullText = returnFullText;
    }

    /**
     * Chat Model
     * @return
     */
    public HuggingFaceChatModel getChatModel() {
        return chatModel;
    }

    public void setChatModel(HuggingFaceChatModel chatModel) {
        this.chatModel = chatModel;
    }
}
