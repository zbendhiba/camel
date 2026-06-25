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

import java.util.Set;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AiToolEndpointLifecycleTest extends CamelTestSupport {

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
                from("ai-tool:getWeather"
                     + "?tags=weather"
                     + "&description=Get the current weather for a city"
                     + "&parameter.city=string"
                     + "&parameter.city.description=The city name"
                     + "&parameter.city.required=true"
                     + "&parameter.unit=string"
                     + "&parameter.unit.enum=celsius,fahrenheit")
                        .setBody(simple("Sunny in ${header.city}"));

                from("ai-tool:searchTool"
                     + "?tags=weather"
                     + "&description=Search weather history"
                     + "&exposed=false")
                        .setBody(constant("History result"));
            }
        };
    }

    @Test
    public void testToolRegisteredOnStart() {
        AiToolRegistry registry = AiToolRegistry.getInstance();

        Set<AiToolSpec> tools = registry.getTools().get("weather");
        assertNotNull(tools);
        assertEquals(1, tools.size());

        AiToolSpec spec = tools.iterator().next();
        assertEquals("getWeather", spec.getName());
        assertEquals("Get the current weather for a city", spec.getDescription());
        assertTrue(spec.isExposed());
        assertNotNull(spec.getConsumer());
        assertNotNull(spec.getParametersJsonSchema());
        assertNotNull(spec.getParameterDefs());
        assertEquals(2, spec.getParameterDefs().size());
        assertTrue(spec.getParameterDefs().containsKey("city"));
        assertTrue(spec.getParameterDefs().containsKey("unit"));
        assertTrue(spec.getParameterDefs().get("city").required);
    }

    @Test
    public void testSearchableToolRegistered() {
        AiToolRegistry registry = AiToolRegistry.getInstance();

        assertTrue(registry.hasSearchableTools());
        Set<AiToolSpec> searchable = registry.getSearchableTools().get("weather");
        assertNotNull(searchable);
        assertEquals(1, searchable.size());

        AiToolSpec spec = searchable.iterator().next();
        assertFalse(spec.isExposed());
    }

    @Test
    public void testToolDeregisteredOnStop() throws Exception {
        AiToolRegistry registry = AiToolRegistry.getInstance();

        assertNotNull(registry.getTools().get("weather"));
        assertTrue(registry.hasSearchableTools());

        context.stop();

        assertNull(registry.getTools().get("weather"));
        assertNull(registry.getSearchableTools().get("weather"));
    }

    @Test
    public void testProducerThrowsUnsupported() {
        assertThrows(UnsupportedOperationException.class, () -> {
            context.getEndpoint("ai-tool:test?tags=t&description=d").createProducer();
        });
    }

    @Test
    public void testToolIdUsedAsName() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("ai-tool:getUserProfile"
                     + "?tags=test"
                     + "&description=Get user profile")
                        .setBody(constant("profile"));
            }
        });

        Set<AiToolSpec> tools = AiToolRegistry.getInstance().getTools().get("test");
        assertNotNull(tools);

        AiToolSpec spec = tools.iterator().next();
        assertEquals("getUserProfile", spec.getName());
    }

    @Test
    public void testTaglessToolRegisteredInDefaultPool() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("ai-tool:defaultTool"
                     + "?description=A tool with no tags")
                        .setBody(constant("default"));
            }
        });

        AiToolRegistry registry = AiToolRegistry.getInstance();
        assertFalse(registry.getDefaultTools().isEmpty());
        assertEquals(1, registry.getDefaultTools().size());

        AiToolSpec spec = registry.getDefaultTools().iterator().next();
        assertEquals("defaultTool", spec.getName());
    }

    @Test
    public void testTaglessToolDeregisteredOnStop() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("ai-tool:tempTool"
                     + "?description=Temporary tool")
                        .setBody(constant("temp"));
            }
        });

        AiToolRegistry registry = AiToolRegistry.getInstance();
        assertFalse(registry.getDefaultTools().isEmpty());

        context.stop();

        assertTrue(registry.getDefaultTools().isEmpty());
    }

    @Test
    public void testMultipleTagsRegistration() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("ai-tool:sharedTool"
                     + "?tags=assistant,admin"
                     + "&description=Shared tool")
                        .setBody(constant("shared"));
            }
        });

        AiToolRegistry registry = AiToolRegistry.getInstance();
        assertNotNull(registry.getTools().get("assistant"));
        assertNotNull(registry.getTools().get("admin"));
        assertEquals(1, registry.getTools().get("assistant").size());
        assertEquals(1, registry.getTools().get("admin").size());
    }
}
