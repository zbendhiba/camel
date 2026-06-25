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

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AiToolRegistryTest {

    private AiToolRegistry registry;

    @BeforeEach
    public void setUp() {
        registry = AiToolRegistry.getInstance();
        registry.getTools().clear();
        registry.getSearchableTools().clear();
        registry.getDefaultTools().clear();
        registry.getDefaultSearchableTools().clear();
    }

    @AfterEach
    public void tearDown() {
        registry.getTools().clear();
        registry.getSearchableTools().clear();
        registry.getDefaultTools().clear();
        registry.getDefaultSearchableTools().clear();
    }

    @Test
    public void testSingleton() {
        assertSame(AiToolRegistry.getInstance(), AiToolRegistry.getInstance());
    }

    @Test
    public void testPutAndGetExposedTool() {
        AiToolSpec spec = new AiToolSpec("getTool", "A test tool", Map.of(), null, null, true);

        registry.put("weather", spec);

        Set<AiToolSpec> tools = registry.getTools().get("weather");
        assertNotNull(tools);
        assertEquals(1, tools.size());
        assertTrue(tools.contains(spec));
    }

    @Test
    public void testPutAndGetSearchableTool() {
        AiToolSpec spec = new AiToolSpec("searchTool", "Searchable", Map.of(), null, null, false);

        registry.putSearchable("products", spec);

        Set<AiToolSpec> tools = registry.getSearchableTools().get("products");
        assertNotNull(tools);
        assertEquals(1, tools.size());
        assertTrue(tools.contains(spec));
    }

    @Test
    public void testRemoveExposedTool() {
        AiToolSpec spec = new AiToolSpec("getTool", "A test tool", Map.of(), null, null, true);

        registry.put("weather", spec);
        assertEquals(1, registry.getTools().get("weather").size());

        registry.remove("weather", spec);
        assertNull(registry.getTools().get("weather"));
    }

    @Test
    public void testRemoveSearchableTool() {
        AiToolSpec spec = new AiToolSpec("searchTool", "Searchable", Map.of(), null, null, false);

        registry.putSearchable("products", spec);
        assertEquals(1, registry.getSearchableTools().get("products").size());

        registry.removeSearchable("products", spec);
        assertNull(registry.getSearchableTools().get("products"));
    }

    @Test
    public void testMultipleToolsWithSameTag() {
        AiToolSpec spec1 = new AiToolSpec("tool1", "Tool 1", Map.of(), null, null, true);
        AiToolSpec spec2 = new AiToolSpec("tool2", "Tool 2", Map.of(), null, null, true);

        registry.put("assistant", spec1);
        registry.put("assistant", spec2);

        Set<AiToolSpec> tools = registry.getTools().get("assistant");
        assertNotNull(tools);
        assertEquals(2, tools.size());
    }

    @Test
    public void testRemoveOneOfMultipleTools() {
        AiToolSpec spec1 = new AiToolSpec("tool1", "Tool 1", Map.of(), null, null, true);
        AiToolSpec spec2 = new AiToolSpec("tool2", "Tool 2", Map.of(), null, null, true);

        registry.put("assistant", spec1);
        registry.put("assistant", spec2);

        registry.remove("assistant", spec1);

        Set<AiToolSpec> tools = registry.getTools().get("assistant");
        assertNotNull(tools);
        assertEquals(1, tools.size());
        assertTrue(tools.contains(spec2));
        assertFalse(tools.contains(spec1));
    }

    @Test
    public void testHasSearchableTools() {
        assertFalse(registry.hasSearchableTools());

        AiToolSpec spec = new AiToolSpec("tool", "A tool", Map.of(), null, null, false);
        registry.putSearchable("users", spec);

        assertTrue(registry.hasSearchableTools());
    }

    @Test
    public void testTagIsolation() {
        AiToolSpec weatherSpec = new AiToolSpec("weather", "Weather", Map.of(), null, null, true);
        AiToolSpec emailSpec = new AiToolSpec("email", "Email", Map.of(), null, null, true);

        registry.put("weather", weatherSpec);
        registry.put("email", emailSpec);

        assertEquals(1, registry.getTools().get("weather").size());
        assertEquals(1, registry.getTools().get("email").size());
        assertTrue(registry.getTools().get("weather").contains(weatherSpec));
        assertTrue(registry.getTools().get("email").contains(emailSpec));
    }

    @Test
    public void testRemoveFromNonExistentTag() {
        AiToolSpec spec = new AiToolSpec("tool", "A tool", Map.of(), null, null, true);
        registry.remove("nonexistent", spec);
        registry.removeSearchable("nonexistent", spec);
    }

    @Test
    public void testDefaultPoolPutAndRemove() {
        AiToolSpec spec = new AiToolSpec("defaultTool", "Default", Map.of(), null, null, true);

        registry.putDefault(spec);
        assertEquals(1, registry.getDefaultTools().size());
        assertTrue(registry.getDefaultTools().contains(spec));

        registry.removeDefault(spec);
        assertTrue(registry.getDefaultTools().isEmpty());
    }

    @Test
    public void testDefaultSearchablePoolPutAndRemove() {
        AiToolSpec spec = new AiToolSpec("searchDefault", "Searchable default", Map.of(), null, null, false);

        registry.putDefaultSearchable(spec);
        assertEquals(1, registry.getDefaultSearchableTools().size());

        registry.removeDefaultSearchable(spec);
        assertTrue(registry.getDefaultSearchableTools().isEmpty());
    }

    @Test
    public void testGetToolsByTagIncludesDefaultPool() {
        AiToolSpec taggedTool = new AiToolSpec("tagged", "Tagged", Map.of(), null, null, true);
        AiToolSpec defaultTool = new AiToolSpec("default", "Default", Map.of(), null, null, true);

        registry.put("weather", taggedTool);
        registry.putDefault(defaultTool);

        Set<AiToolSpec> result = registry.getToolsByTag("weather");
        assertEquals(2, result.size());
        assertTrue(result.contains(taggedTool));
        assertTrue(result.contains(defaultTool));
    }

    @Test
    public void testGetToolsByTagWithNoMatchReturnsDefaultOnly() {
        AiToolSpec defaultTool = new AiToolSpec("default", "Default", Map.of(), null, null, true);
        registry.putDefault(defaultTool);

        Set<AiToolSpec> result = registry.getToolsByTag("nonexistent");
        assertEquals(1, result.size());
        assertTrue(result.contains(defaultTool));
    }

    @Test
    public void testGetAllToolsMergesTaggedAndDefault() {
        AiToolSpec tool1 = new AiToolSpec("tool1", "Tool 1", Map.of(), null, null, true);
        AiToolSpec tool2 = new AiToolSpec("tool2", "Tool 2", Map.of(), null, null, true);
        AiToolSpec defaultTool = new AiToolSpec("default", "Default", Map.of(), null, null, true);

        registry.put("weather", tool1);
        registry.put("email", tool2);
        registry.putDefault(defaultTool);

        Set<AiToolSpec> all = registry.getAllTools();
        assertEquals(3, all.size());
        assertTrue(all.contains(tool1));
        assertTrue(all.contains(tool2));
        assertTrue(all.contains(defaultTool));
    }

    @Test
    public void testHasSearchableToolsIncludesDefaultPool() {
        assertFalse(registry.hasSearchableTools());

        AiToolSpec spec = new AiToolSpec("tool", "A tool", Map.of(), null, null, false);
        registry.putDefaultSearchable(spec);

        assertTrue(registry.hasSearchableTools());
    }
}
