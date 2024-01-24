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

import java.time.Duration;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.huggingface.HuggingFaceChatModel;
import org.apache.camel.component.langchain.LangchainProducer;
import org.apache.camel.component.langchain.service.Langchain4jChatHandler;
import org.apache.camel.util.ObjectHelper;

public class Langchain4jHuggingFaceProducer extends LangchainProducer {
    private Langchain4jHuggingFaceEndpoint endpoint;

    public Langchain4jHuggingFaceProducer(Langchain4jHuggingFaceEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        ChatLanguageModel huggingFaceModel = this.endpoint.getChatModel();

        if (huggingFaceModel == null) {
            // create Chat ModeL . This requires at least an API Key
            ObjectHelper.notNull(this.endpoint.getAccessToken(), "accessToken");

            huggingFaceModel = HuggingFaceChatModel.builder()
                    .timeout(Duration.ofMillis(this.endpoint.getTimeout()))
                    .temperature(this.endpoint.getTemperature())
                    .modelId(this.endpoint.getModelId())
                    .accessToken(this.endpoint.getAccessToken())
                    .maxNewTokens(this.endpoint.getMaxNewRetries())
                    .returnFullText(this.endpoint.getReturnFullText())
                    .build();

        }

        this.setChatLanguageModel(huggingFaceModel);
        this.setLangchain4jChatHandler(new Langchain4jChatHandler(huggingFaceModel));
    }

}
