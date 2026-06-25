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

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AiToolParameterHelperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void testSplitTags() {
        assertArrayEquals(new String[] { "a", "b", "c" }, AiToolParameterHelper.splitTags("a,b,c"));
        assertArrayEquals(new String[] { "single" }, AiToolParameterHelper.splitTags("single"));
    }

    @Test
    public void testParseSimpleType() {
        Map<String, String> params = Map.of("city", "string");

        Map<String, AiToolParameterHelper.ParameterDef> defs = AiToolParameterHelper.parseParameterMetadata(params);

        assertEquals(1, defs.size());
        assertEquals("string", defs.get("city").type);
        assertNull(defs.get("city").description);
        assertFalse(defs.get("city").required);
    }

    @Test
    public void testParseWithDescription() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("city", "string");
        params.put("city.description", "The city name");

        Map<String, AiToolParameterHelper.ParameterDef> defs = AiToolParameterHelper.parseParameterMetadata(params);

        assertEquals(1, defs.size());
        assertEquals("string", defs.get("city").type);
        assertEquals("The city name", defs.get("city").description);
    }

    @Test
    public void testParseWithRequired() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("city", "string");
        params.put("city.required", "true");

        Map<String, AiToolParameterHelper.ParameterDef> defs = AiToolParameterHelper.parseParameterMetadata(params);

        assertTrue(defs.get("city").required);
    }

    @Test
    public void testParseWithEnum() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("unit", "string");
        params.put("unit.enum", "celsius,fahrenheit");

        Map<String, AiToolParameterHelper.ParameterDef> defs = AiToolParameterHelper.parseParameterMetadata(params);

        assertNotNull(defs.get("unit").enumValues);
        assertEquals(2, defs.get("unit").enumValues.size());
        assertEquals("celsius", defs.get("unit").enumValues.get(0));
        assertEquals("fahrenheit", defs.get("unit").enumValues.get(1));
    }

    @Test
    public void testParseMultipleParameters() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("city", "string");
        params.put("city.description", "The city name");
        params.put("city.required", "true");
        params.put("unit", "string");
        params.put("unit.enum", "celsius,fahrenheit");
        params.put("days", "integer");

        Map<String, AiToolParameterHelper.ParameterDef> defs = AiToolParameterHelper.parseParameterMetadata(params);

        assertEquals(3, defs.size());
        assertEquals("string", defs.get("city").type);
        assertEquals("integer", defs.get("days").type);
    }

    @Test
    public void testBuildJsonSchemaBasic() throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("city", "string");
        params.put("city.description", "The city name");
        params.put("city.required", "true");

        String schema = AiToolParameterHelper.buildJsonSchema(params);
        JsonNode root = MAPPER.readTree(schema);

        assertEquals("object", root.get("type").asText());
        assertNotNull(root.get("properties").get("city"));
        assertEquals("string", root.get("properties").get("city").get("type").asText());
        assertEquals("The city name", root.get("properties").get("city").get("description").asText());
        assertTrue(root.get("required").toString().contains("city"));
    }

    @Test
    public void testBuildJsonSchemaWithEnum() throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("unit", "string");
        params.put("unit.enum", "celsius,fahrenheit");

        String schema = AiToolParameterHelper.buildJsonSchema(params);
        JsonNode root = MAPPER.readTree(schema);

        JsonNode unitProp = root.get("properties").get("unit");
        assertNotNull(unitProp.get("enum"));
        assertEquals(2, unitProp.get("enum").size());
        assertEquals("celsius", unitProp.get("enum").get(0).asText());
        assertEquals("fahrenheit", unitProp.get("enum").get(1).asText());
    }

    @Test
    public void testBuildJsonSchemaTypeMapping() throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("count", "integer");
        params.put("score", "number");
        params.put("active", "boolean");
        params.put("name", "string");

        String schema = AiToolParameterHelper.buildJsonSchema(params);
        JsonNode root = MAPPER.readTree(schema);

        assertEquals("integer", root.get("properties").get("count").get("type").asText());
        assertEquals("number", root.get("properties").get("score").get("type").asText());
        assertEquals("boolean", root.get("properties").get("active").get("type").asText());
        assertEquals("string", root.get("properties").get("name").get("type").asText());
    }

    @Test
    public void testBuildJsonSchemaNoRequired() throws Exception {
        Map<String, String> params = Map.of("city", "string");

        String schema = AiToolParameterHelper.buildJsonSchema(params);
        JsonNode root = MAPPER.readTree(schema);

        assertNull(root.get("required"));
    }

    @Test
    public void testBuildJsonSchemaTypeMappingAliases() throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("a", "int");
        params.put("b", "long");
        params.put("c", "double");
        params.put("d", "float");
        params.put("e", "bool");

        String schema = AiToolParameterHelper.buildJsonSchema(params);
        JsonNode root = MAPPER.readTree(schema);

        assertEquals("integer", root.get("properties").get("a").get("type").asText());
        assertEquals("integer", root.get("properties").get("b").get("type").asText());
        assertEquals("number", root.get("properties").get("c").get("type").asText());
        assertEquals("number", root.get("properties").get("d").get("type").asText());
        assertEquals("boolean", root.get("properties").get("e").get("type").asText());
    }

    @Test
    public void testParseDefaultType() {
        Map<String, String> params = Map.of("city.description", "The city name");

        Map<String, AiToolParameterHelper.ParameterDef> defs = AiToolParameterHelper.parseParameterMetadata(params);

        assertEquals("string", defs.get("city").type);
    }
}
