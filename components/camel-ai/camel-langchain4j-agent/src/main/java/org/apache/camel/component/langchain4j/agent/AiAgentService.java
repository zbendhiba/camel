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

import java.util.List;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.service.UserMessage;

/**
 * AI Agent Service interface for LangChain4j integration. Supports both simple chat and tool-enabled chat with a
 * unified interface.
 */
public interface AiAgentService {

    /**
     * Simple chat with a single user message
     *
     * @param  message the user message
     * @return         the AI response
     */
    String chat(@UserMessage String message);

    /**
     * Chat using a list of ChatMessage objects. This method can handle both simple chat and tool-enabled chat depending
     * on the configuration and available tools.
     *
     * @param  messages the list of chat messages (system, user, assistant, tool result messages)
     * @return          the AI response after processing messages and potentially calling tools
     */
    String chat(List<ChatMessage> messages);
}
