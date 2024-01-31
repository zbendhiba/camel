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

import java.util.List;
import java.util.Map;

import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiModelName;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.langchain.commons.Langchain4jOperations;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.langchain.openai.Langchain4jOpenAiConstants.LOG_REQUEST;
import static org.apache.camel.component.langchain.openai.Langchain4jOpenAiConstants.LOG_RESPONSE;
import static org.apache.camel.component.langchain.openai.Langchain4jOpenAiConstants.NB_RETRY;
import static org.apache.camel.component.langchain.openai.Langchain4jOpenAiConstants.TIMEOUT;

@UriEndpoint(firstVersion = "4.4.0", scheme = "langchain-openai",
             title = "langchain-openai",
             syntax = "langchain-openai:name", producerOnly = true,
             category = { Category.AI })
public class Langchain4jOpenAiEndpoint extends DefaultEndpoint {

    @UriPath
    @Metadata(required = true)
    private String name;

    @UriParam
    @Metadata(required = true)
    private Langchain4jOperations operation;

    @UriParam(label = "security", secret = true)
    private String openAiKey;

    @UriParam(defaultValue = OpenAiModelName.GPT_3_5_TURBO)
    private String openAiModelName;

    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private ChatLanguageModel chatModel;

    @UriParam
    private ChatMessageType chatMessageType;

    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private String baseUrl;

    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private String organizationId;

    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private Double temperature;
    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private Double topP;
    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private List<String> stop;
    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private Integer maxTokens;
    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private Double presencePenalty;
    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private Double frequencyPenalty;
    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private Map<String, Integer> logitBias;
    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private String responseFormat;
    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private Integer seed;
    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private String user;
    @UriParam(defaultValue = "" + TIMEOUT)
    private Integer timeout = TIMEOUT;

    @UriParam(defaultValue = "" + NB_RETRY)
    private Integer maxRetries = NB_RETRY;

    @UriParam(defaultValue = "" + LOG_REQUEST)
    private Boolean logRequests = LOG_REQUEST;
    @UriParam(defaultValue = "" + LOG_RESPONSE)
    private Boolean logResponses = LOG_RESPONSE;
    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private Tokenizer tokenizer;

    public Langchain4jOpenAiEndpoint(String uri, Langchain4jOpenAiComponent component) {
        super(uri, component);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new Langchain4jOpenAiProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Cannot consume from an Langchain4jOpenAiEndpoint: " + getEndpointUri());
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
     * Any model value from dev.langchain4j.model.openai.OpenAiModelName
     *
     * @return
     */
    public String getOpenAiModelName() {
        return openAiModelName;
    }

    public void setOpenAiModelName(String openAiModelName) {
        this.openAiModelName = openAiModelName;
    }

    /**
     * Chat Model
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
     * OpenAI API key
     *
     * @return
     */
    public String getOpenAiKey() {
        return openAiKey;
    }

    public void setOpenAiKey(String openAiKey) {
        this.openAiKey = openAiKey;
    }

    /**
     * OpenAI base URL
     *
     * @return
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * OpenAI Organization ID
     *
     * @return
     */
    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    /**
     * TODO
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
     * TODO
     *
     * @return
     */
    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    /**
     * TODO
     *
     * @return
     */
    public List<String> getStop() {
        return stop;
    }

    public void setStop(List<String> stop) {
        this.stop = stop;
    }

    /**
     * Max Tokens
     *
     * @return
     */
    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    /**
     * TODO
     *
     * @return
     */
    public Double getPresencePenalty() {
        return presencePenalty;
    }

    public void setPresencePenalty(Double presencePenalty) {
        this.presencePenalty = presencePenalty;
    }

    /**
     * TODO
     *
     * @return
     */
    public Double getFrequencyPenalty() {
        return frequencyPenalty;
    }

    public void setFrequencyPenalty(Double frequencyPenalty) {
        this.frequencyPenalty = frequencyPenalty;
    }

    /**
     * TODO
     *
     * @return
     */
    public Map<String, Integer> getLogitBias() {
        return logitBias;
    }

    public void setLogitBias(Map<String, Integer> logitBias) {
        this.logitBias = logitBias;
    }

    /**
     * TODO
     *
     * @return
     */
    public String getResponseFormat() {
        return responseFormat;
    }

    public void setResponseFormat(String responseFormat) {
        this.responseFormat = responseFormat;
    }

    /**
     * TODO
     *
     * @return
     */
    public Integer getSeed() {
        return seed;
    }

    public void setSeed(Integer seed) {
        this.seed = seed;
    }

    /**
     * TODO
     *
     * @return
     */
    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
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
     * Number of maximum retries
     *
     * @return
     */
    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }

    /**
     * Log Requests
     *
     * @return
     */
    public Boolean getLogRequests() {
        return logRequests;
    }

    public void setLogRequests(Boolean logRequests) {
        this.logRequests = logRequests;
    }

    /**
     * Log Responses
     *
     * @return
     */
    public Boolean getLogResponses() {
        return logResponses;
    }

    public void setLogResponses(Boolean logResponses) {
        this.logResponses = logResponses;
    }

    /**
     * TODO
     *
     * @return
     */
    public Tokenizer getTokenizer() {
        return tokenizer;
    }

    public void setTokenizer(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
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
}
