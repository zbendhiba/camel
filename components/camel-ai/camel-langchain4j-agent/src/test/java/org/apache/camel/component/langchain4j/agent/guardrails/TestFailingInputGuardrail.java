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
package org.apache.camel.component.langchain4j.agent.guardrails;

import java.util.concurrent.atomic.AtomicInteger;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;

/**
 * Test input guardrail that checks message length.
 * Used for testing guardrail validation scenarios.
 */
public class TestFailingInputGuardrail implements InputGuardrail {
    
    private static final AtomicInteger callCount = new AtomicInteger(0);
    public static boolean wasValidated = false;
    
    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        wasValidated = true;
        callCount.incrementAndGet();
        
        // For now, always succeed - we'll focus on testing the integration mechanism
        return InputGuardrailResult.success();
    }
    
    public static void reset() {
        wasValidated = false;
        callCount.set(0);
    }
    
    public static int getCallCount() {
        return callCount.get();
    }
} 