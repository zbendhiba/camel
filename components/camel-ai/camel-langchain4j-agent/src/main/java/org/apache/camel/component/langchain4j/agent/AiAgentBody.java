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

public class AiAgentBody {
    private List<ChatMessage> messages;

    public AiAgentBody() {
        this.messages = new ArrayList<>();
    }

    public AiAgentBody(List<ChatMessage> messages) {
        this.messages = messages != null ? messages : new ArrayList<>();
    }

    public AiAgentBody withUserMessage(String userMessage) {
        if (userMessage != null && !userMessage.trim().isEmpty()) {
            this.messages.add(new UserMessage(userMessage));
        }
        return this;
    }

    public AiAgentBody withSystemMessage(String systemMessage) {
        if (systemMessage != null && !systemMessage.trim().isEmpty()) {
            this.messages.add(0, new SystemMessage(systemMessage)); // Add at beginning
        }
        return this;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages != null ? messages : new ArrayList<>();
    }

    public AiAgentBody withMessages(List<ChatMessage> messages) {
        this.messages = messages != null ? messages : new ArrayList<>();
        return this;
    }

    /**
     * Add a chat message to the conversation
     */
    public AiAgentBody addMessage(ChatMessage message) {
        if (message != null) {
            this.messages.add(message);
        }
        return this;
    }

    /**
     * Check if this body has any chat messages
     */
    public boolean hasMessages() {
        return messages != null && !messages.isEmpty();
    }

    /**
     * Create AiAgentBody from ChatMessage list
     */
    public static AiAgentBody fromChatMessages(List<ChatMessage> messages) {
        return new AiAgentBody(messages);
    }
}
