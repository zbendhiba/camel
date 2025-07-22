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
        // Create AI service proxy for chat interactions
        AiAgentService aiAgentService = AiServices.create(AiAgentService.class, chatModel);

        // Extract and validate the message payload from exchange
        Object messagePayload = exchange.getIn().getBody();
        ObjectHelper.notNull(messagePayload, "body");

        // Construct AI agent body based on payload type
        AiAgentBody body;

        if (messagePayload instanceof String userMessage) {
            // Handle simple string message: create AiAgentBody with user message
            body = new AiAgentBody()
                    .withUserMessage(userMessage)
                    // retrieve system message from header if available
                    .withSystemMessage(exchange.getIn().getHeader(SYSTEM_MESSAGE, String.class));
        } else if (messagePayload instanceof AiAgentBody aiAgentBody) {
            // Handle structured message: use AiAgentBody directly
            body = aiAgentBody;
        } else {
            // Unsupported payload type - throw exception
            throw new InvalidPayloadException(exchange, AiAgentBody.class);
        }

        // Send message to AI model and get response
        String response;
        if (body.getSystemMessage() != null) {
            // Send both user and system messages to AI
            response = aiAgentService.chat(body.getUserMessage(), body.getSystemMessage());
        } else {
            // Send only user message to AI
            response = aiAgentService.chat(body.getUserMessage());
        }

        // Set AI response as the new exchange body
        exchange.getIn().setBody(response);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.chatModel = this.endpoint.getConfiguration().getChatModel();
        ObjectHelper.notNull(chatModel, "chatModel");
    }

}
