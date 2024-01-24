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

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.langchain.Langchain4jConstants;
import org.apache.camel.component.langchain.LangchainComponent;
import org.apache.camel.component.langchain.LangchainEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

@UriEndpoint(firstVersion = "4.5.0", scheme = "langchain-hugging-face",
             title = "langchain-hugging-face",
             syntax = "langchain-hugging-face:type", producerOnly = true,
             category = { Category.AI })
public class Langchain4jHuggingFaceEndpoint extends LangchainEndpoint {

    @UriParam(defaultValue = "" + Langchain4jConstants.TIMEOUT)
    private Integer timeout = Langchain4jConstants.TIMEOUT;

    @UriParam
    private String modelId;

    @UriParam
    private Double temperature;
    @UriParam(label = "security", secret = true)
    private String accessToken;
    @UriParam(defaultValue = "" + Langchain4jConstants.NB_RETRY)
    private Integer maxNewRetries = Langchain4jConstants.NB_RETRY;

    @UriParam(defaultValue = "" + Langchain4jConstants.RETURN_FULL_TEXT)
    private Boolean returnFullText = Langchain4jConstants.RETURN_FULL_TEXT;

    public Langchain4jHuggingFaceEndpoint(String uri, Langchain4HuggingFaceComponent component, String type) {
        super(uri, (LangchainComponent) component, type);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new Langchain4jHuggingFaceProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Cannot consume from an Langchain4jHuggingFaceEndpoint: " + getEndpointUri());
    }

    /**
     * Timeout
     *
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
     *
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
     *
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
     *
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
     *
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
     *
     * @return
     */
    public Boolean getReturnFullText() {
        return returnFullText;
    }

    public void setReturnFullText(Boolean returnFullText) {
        this.returnFullText = returnFullText;
    }

}
