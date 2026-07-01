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
package org.apache.camel.component.ai.tools;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AiToolExecutorTest extends CamelTestSupport {

    @AfterEach
    public void cleanRegistry() {
        AiToolRegistry registry = AiToolRegistry.getInstance();
        registry.getTools().clear();
        registry.getSearchableTools().clear();
        registry.getDefaultTools().clear();
        registry.getDefaultSearchableTools().clear();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("ai-tool:greetUser"
                     + "?tags=test"
                     + "&description=Greet a user by name"
                     + "&parameter.name=string"
                     + "&parameter.name.description=The user name"
                     + "&parameter.name.required=true"
                     + "&parameter.age=integer"
                     + "&parameter.age.description=The user age")
                        .setBody(simple("Hello ${header.name}, age ${header.age}"));

                from("ai-tool:noParams"
                     + "?tags=test"
                     + "&description=A tool with no parameters")
                        .setBody(constant("no-param result"));

                from("ai-tool:failingTool"
                     + "?tags=test"
                     + "&description=A tool that always fails")
                        .throwException(new RuntimeException("Simulated failure"));
            }
        };
    }

    @Test
    public void testExecuteWithDeclaredArguments() {
        AiToolSpec spec = findSpec("greetUser");
        Exchange exchange = createExchangeWithBody(null);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "Alice");
        arguments.put("age", 30);

        String result = AiToolExecutor.execute(spec, arguments, exchange);

        assertEquals("Hello Alice, age 30", result);
    }

    @Test
    public void testExecuteRejectsUndeclaredArguments() {
        AiToolSpec spec = findSpec("greetUser");
        Exchange exchange = createExchangeWithBody(null);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "Bob");
        arguments.put("age", 25);
        arguments.put("injected", "should-be-rejected");

        String result = AiToolExecutor.execute(spec, arguments, exchange);

        assertTrue(result.contains("Error executing tool 'greetUser'"),
                "Should contain tool name in error but was: " + result);
        assertTrue(result.contains("undeclared argument 'injected'"),
                "Should indicate the undeclared argument but was: " + result);
    }

    @Test
    public void testExecuteWithNullArguments() {
        AiToolSpec spec = findSpec("noParams");
        Exchange exchange = createExchangeWithBody(null);

        String result = AiToolExecutor.execute(spec, null, exchange);

        assertEquals("no-param result", result);
    }

    @Test
    public void testExecuteWithEmptyArguments() {
        AiToolSpec spec = findSpec("noParams");
        Exchange exchange = createExchangeWithBody(null);

        String result = AiToolExecutor.execute(spec, Map.of(), exchange);

        assertEquals("no-param result", result);
    }

    @Test
    public void testExecuteReturnsErrorOnRouteFailure() {
        AiToolSpec spec = findSpec("failingTool");
        Exchange exchange = createExchangeWithBody(null);

        String result = AiToolExecutor.execute(spec, null, exchange);

        assertTrue(result.contains("Error executing tool 'failingTool'"),
                "Should contain tool name in error but was: " + result);
        assertTrue(result.contains("Simulated failure"),
                "Should contain exception message but was: " + result);
    }

    @Test
    public void testExecuteRejectsMissingRequiredArguments() {
        AiToolSpec spec = findSpec("greetUser");
        Exchange exchange = createExchangeWithBody(null);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("age", 30);

        String result = AiToolExecutor.execute(spec, arguments, exchange);

        assertTrue(result.contains("Error executing tool 'greetUser'"),
                "Should contain tool name in error but was: " + result);
        assertTrue(result.contains("missing required argument 'name'"),
                "Should indicate the missing required argument but was: " + result);
    }

    @Test
    public void testExecuteWithNullConsumer() {
        AiToolSpec spec = new AiToolSpec("ghostTool", "A tool with no consumer", Map.of(), null, null, true);
        Exchange exchange = createExchangeWithBody(null);

        String result = AiToolExecutor.execute(spec, null, exchange);

        assertTrue(result.contains("Error executing tool 'ghostTool'"),
                "Should contain tool name in error but was: " + result);
        assertTrue(result.contains("no consumer available"),
                "Should indicate missing consumer but was: " + result);
    }

    private AiToolSpec findSpec(String toolName) {
        Set<AiToolSpec> specs = AiToolRegistry.getInstance().getToolsByTag("test");
        return specs.stream()
                .filter(s -> toolName.equals(s.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool not found: " + toolName));
    }
}
