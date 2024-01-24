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

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.apache.camel.component.langchain.LangchainEndpoint;
import org.apache.camel.component.langchain.LangchainProducer;
import org.apache.camel.component.langchain.service.Langchain4jChatHandler;
import org.apache.camel.util.ObjectHelper;

public class Langchain4jOpenAiProducer extends LangchainProducer {

    private Langchain4jOpenAiEndpoint endpoint;

    public Langchain4jOpenAiProducer(Langchain4jOpenAiEndpoint endpoint) {
        super((LangchainEndpoint) endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        ChatLanguageModel openAiModel = this.endpoint.getChatModel();

        if (openAiModel == null) {
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

        this.setChatLanguageModel(openAiModel);
        this.setLangchain4jChatHandler(new Langchain4jChatHandler(openAiModel));

    }

}
