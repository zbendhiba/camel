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
package org.apache.camel.component.langchain.service;

import java.util.List;
import java.util.Map;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.output.Response;

public class Langchain4jChatHandler {

    ChatLanguageModel chatLanguageModel;

    public Langchain4jChatHandler(ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
    }

    /**
     * Send one simple message
     *
     * @param  message
     * @return
     */
    public String sendMessage(String message) {
        return this.chatLanguageModel.generate(message);
    }

    public String sendWithPromptTemplate(String promptTemplate, Map<String, Object> variables) {
        PromptTemplate template = PromptTemplate.from(promptTemplate);
        Prompt prompt = template.apply(variables);
        return this.sendMessage(prompt.text());
    }

    /**
     * Send a simple message, for a specific Chat Message Type
     *
     * @param  message
     * @param  type
     * @return
     */
    public String sendMessage(String message, ChatMessageType type) {
        ChatMessage[] chatMessages = { convertChatMessages(type, message) };
        return this.sendMessage(chatMessages);
    }

    /**
     * Send a List of Messages
     *
     * @param  type
     * @param  messages
     * @return
     */
    public String sendMessages(ChatMessageType type, List<String> messages) {

        var chatMessages = messages.stream()
                .map(message -> (ChatMessage) convertChatMessages(type, message))
                .toArray(ChatMessage[]::new);

        return sendMessage(chatMessages);
    }

    /**
     * Create the messages in the format of Langchain4j ChatMessage
     *
     * @param  type
     * @param  message
     * @return
     */
    private ChatMessage convertChatMessages(ChatMessageType type, String message) {
        return switch (type) {
            case USER -> new UserMessage(message);
            case AI -> new AiMessage(message);
            case SYSTEM -> new SystemMessage(message);
            case TOOL_EXECUTION_RESULT -> throw new IllegalArgumentException("Tools are currently not supported");
        };

    }

    /**
     * Send And Array of ChatMessages
     *
     * @param  messages
     * @return
     */
    public String sendMessage(ChatMessage... messages) {
        Response<AiMessage> response = this.chatLanguageModel.generate(messages);
        AiMessage message = response.content();

        if (message == null) {
            return null;
        }

        return message.text();
    }

    public ChatLanguageModel getChatLanguageModel() {
        return chatLanguageModel;
    }

    public void setChatLanguageModel(ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
    }
}
