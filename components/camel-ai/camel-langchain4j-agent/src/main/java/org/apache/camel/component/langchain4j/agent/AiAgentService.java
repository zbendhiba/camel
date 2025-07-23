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
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface AiAgentService {

    String chat(@UserMessage String message);

    @SystemMessage("{{prompt}}")
    String chat(@UserMessage String message, @V("prompt") String prompt);

    /**
     * Chat with tools support using a single user message
     *
     * @param message the user message
     * @return the AI response after potentially calling tools
     */
    String chatWithTools(@UserMessage String message);

    /**
     * Chat with tools support using a single user message and system prompt
     *
     * @param message the user message
     * @param prompt the system prompt
     * @return the AI response after potentially calling tools
     */
    @SystemMessage("{{prompt}}")
    String chatWithTools(@UserMessage String message, @V("prompt") String prompt);
}
