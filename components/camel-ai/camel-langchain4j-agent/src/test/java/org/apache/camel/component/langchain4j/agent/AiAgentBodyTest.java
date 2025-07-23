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

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AiAgentBodyTest {

    @Test
    void testFluentStringMethods() {
        AiAgentBody body = new AiAgentBody()
                .withUserMessage("Hello")
                .withSystemMessage("You are helpful");

        assertTrue(body.hasMessages());
        assertEquals(2, body.getMessages().size());
        assertTrue(body.getMessages().get(0) instanceof SystemMessage);
        assertTrue(body.getMessages().get(1) instanceof UserMessage);
        assertEquals("You are helpful", ((SystemMessage) body.getMessages().get(0)).text());
        assertEquals("Hello", ((UserMessage) body.getMessages().get(1)).singleText());
    }

    @Test
    void testChatMessagesFormat() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage("You are a helpful assistant"));
        messages.add(new UserMessage("What is 2+2?"));
        messages.add(new AiMessage("2+2 equals 4"));
        messages.add(new UserMessage("What about 3+3?"));

        AiAgentBody body = new AiAgentBody(messages);

        assertTrue(body.hasMessages());
        assertEquals(4, body.getMessages().size());
        assertEquals(messages, body.getMessages());
    }

    @Test
    void testFactoryMethod() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new UserMessage("Hello world"));

        AiAgentBody body = AiAgentBody.fromChatMessages(messages);

        assertTrue(body.hasMessages());
        assertEquals(1, body.getMessages().size());
        assertEquals("Hello world", ((UserMessage) body.getMessages().get(0)).singleText());
    }

    @Test
    void testEmptyBody() {
        AiAgentBody body = new AiAgentBody();

        assertFalse(body.hasMessages());
        assertNotNull(body.getMessages());
        assertTrue(body.getMessages().isEmpty());
    }

    @Test
    void testOnlyUserMessage() {
        AiAgentBody body = new AiAgentBody()
                .withUserMessage("Just a user message");

        assertTrue(body.hasMessages());
        assertEquals(1, body.getMessages().size());
        assertTrue(body.getMessages().get(0) instanceof UserMessage);
        assertEquals("Just a user message", ((UserMessage) body.getMessages().get(0)).singleText());
    }

    @Test
    void testFluentInterface() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new UserMessage("Test message"));

        AiAgentBody body = new AiAgentBody()
                .withUserMessage("Hello")
                .withSystemMessage("Be helpful")
                .withMessages(messages);

        assertTrue(body.hasMessages());
        assertEquals(1, body.getMessages().size());
        assertEquals("Test message", ((UserMessage) body.getMessages().get(0)).singleText());
    }

    @Test
    void testAddMessage() {
        AiAgentBody body = new AiAgentBody()
                .addMessage(new UserMessage("First message"))
                .addMessage(new AiMessage("Response"))
                .addMessage(new UserMessage("Second message"));

        assertTrue(body.hasMessages());
        assertEquals(3, body.getMessages().size());
        assertEquals("First message", ((UserMessage) body.getMessages().get(0)).singleText());
        assertEquals("Response", ((AiMessage) body.getMessages().get(1)).text());
        assertEquals("Second message", ((UserMessage) body.getMessages().get(2)).singleText());
    }
} 