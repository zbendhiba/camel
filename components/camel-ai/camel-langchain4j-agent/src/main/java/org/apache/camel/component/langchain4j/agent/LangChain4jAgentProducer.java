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
package org.apache.camel.component.langchain4j.agent;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.NoSuchHeaderOrPropertyException;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.langchain4j.agent.LangChain4jAgent.Headers.SYSTEM_MESSAGE;

public class LangChain4jAgentProducer extends DefaultProducer {

    private final LangChain4jAgentEndpoint endpoint;

    private ChatModel chatModel;

    public LangChain4jAgentProducer(LangChain4jAgentEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        AiAgentService aiAgentService = AiServices.create(AiAgentService.class, chatModel);

        // get the body
        AiAgentBody body;

        // case of simple body userMessage
        Object bodyExtract = exchange.getIn().getBody();
        ObjectHelper.notNull(bodyExtract, "body");

        if (bodyExtract instanceof  String) {
            body = new AiAgentBody()
                    .withUserMessage((String) bodyExtract)
                    // get the system message was passed via a header if it exists
                    .withSystemMessage(exchange.getIn().getHeader(SYSTEM_MESSAGE, String.class));
        } else   if (bodyExtract instanceof  AiAgentBody){
            body = (AiAgentBody) bodyExtract;
        } else throw new InvalidPayloadException(exchange, AiAgentBody.class);

        String response;

        if (body.getSystemMessage() != null) {
            response = aiAgentService.chat(body.getUserMessage(), body.getSystemMessage());
        } else {
            response = aiAgentService.chat(body.getUserMessage());
        }

        exchange.getIn().setBody(response);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.chatModel = this.endpoint.getConfiguration().getChatModel();
        ObjectHelper.notNull(chatModel, "chatModel");
    }

}
