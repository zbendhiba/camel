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

import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.langchain4j.agent.LangChain4jAgent.Headers.SYSTEM_MESSAGE;

public class LangChain4jAgentProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(LangChain4jAgentProducer.class);

    private final LangChain4jAgentEndpoint endpoint;
    private ChatModel chatModel;

    public LangChain4jAgentProducer(LangChain4jAgentEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Object messagePayload = exchange.getIn().getBody();
        ObjectHelper.notNull(messagePayload, "body");

        // Convert input to ChatMessage list
        List<ChatMessage> messages = convertToMessages(exchange, messagePayload);
        
        // Create AI Service with tools if tags are available
        AiAgentService agentService = createAiAgentService(exchange);
        
        // Process using AI Services (with or without tools)
        String response = agentService.chat(messages);
        exchange.getIn().setBody(response);
    }

    /**
     * Convert input payload to ChatMessage list
     */
    private List<ChatMessage> convertToMessages(Exchange exchange, Object messagePayload) throws InvalidPayloadException {
        if (messagePayload instanceof String userMessage) {
            // String payload is treated as user message
            List<ChatMessage> messages = new ArrayList<>();
            // System message can optionally be provided via header
            String systemMessage = exchange.getIn().getHeader(SYSTEM_MESSAGE, String.class);
            if (systemMessage != null && !systemMessage.trim().isEmpty()) {
                messages.add(new SystemMessage(systemMessage));
            }
            messages.add(new UserMessage(userMessage));
            return messages;
        } else if (messagePayload instanceof AiAgentBody aiAgentBody) {
            // Use AiAgentBody's messages
            return aiAgentBody.getMessages();
        } else {
            throw new InvalidPayloadException(exchange, AiAgentBody.class);
        }
    }

    /**
     * Create AI Agent Service with tools if tags are configured
     */
    private AiAgentService createAiAgentService(Exchange exchange) {
        String tags = endpoint.getConfiguration().getTags();
        
        if (tags != null && !tags.trim().isEmpty()) {
            // Create tool provider from Camel routes
            CamelToolProvider toolProvider = new CamelToolProvider(tags, exchange);
            
            if (toolProvider.hasTools()) {
                LOG.debug("Creating AI Service with {} tools for tags: {}", 
                    toolProvider.createToolInstances().size(), tags);
                
                // Use AiServices.builder() with auto-generated tools from Camel routes
                return AiServices.builder(AiAgentService.class)
                    .chatModel(chatModel)
                    .tools(toolProvider.createToolInstances())
                    .build();
            } else {
                LOG.debug("No tools found for tags: {}, using simple AI Service", tags);
            }
        }
        
        // Use simple AI Service when no tags or no tools found
        return AiServices.create(AiAgentService.class, chatModel);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.chatModel = this.endpoint.getConfiguration().getChatModel();
        ObjectHelper.notNull(chatModel, "chatModel");
    }
}
