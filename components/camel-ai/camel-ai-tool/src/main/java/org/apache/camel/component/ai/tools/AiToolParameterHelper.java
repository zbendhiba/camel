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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.camel.RuntimeCamelException;

/**
 * Shared utilities for parsing tool parameter metadata and building JSON Schema. Replaces the duplicated
 * {@code TagsHelper} and {@code parseParameterMetadata()} logic from {@code camel-langchain4j-tools} and
 * {@code camel-spring-ai-tools}.
 *
 * @since 4.22
 */
public final class AiToolParameterHelper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AiToolParameterHelper() {
    }

    /**
     * Splits a comma-separated tag list into individual tags.
     */
    public static String[] splitTags(String tagList) {
        return tagList.split(",");
    }

    /**
     * Parses a flat parameter map (as received from URI or endpoint config) into structured {@link ParameterDef}
     * objects.
     * <p>
     * Handles entries like:
     * <ul>
     * <li>{@code city=string} — defines parameter type</li>
     * <li>{@code city.description=The city name} — adds description</li>
     * <li>{@code city.required=true} — marks as required</li>
     * <li>{@code unit.enum=celsius,fahrenheit} — defines allowed values</li>
     * </ul>
     */
    public static Map<String, ParameterDef> parseParameterMetadata(Map<String, String> parameters) {
        Map<String, ParameterDef> metadata = new HashMap<>();

        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (key.contains(".")) {
                String[] parts = key.split("\\.", 2);
                String paramName = parts[0];
                String propertyName = parts[1];
                ParameterDef def = metadata.computeIfAbsent(paramName, k -> new ParameterDef());

                switch (propertyName) {
                    case "description" -> def.description = value;
                    case "required" -> def.required = Boolean.parseBoolean(value);
                    case "enum" -> def.enumValues = List.of(value.split(","));
                    default -> {
                    }
                }
            } else {
                metadata.computeIfAbsent(key, k -> new ParameterDef()).type = value;
            }
        }

        return metadata;
    }

    /**
     * Builds a JSON Schema object string from the flat parameter map. The result conforms to JSON Schema and is
     * understood by Spring AI ({@code inputSchema}) and OpenAI ({@code function.parameters}).
     */
    public static String buildJsonSchema(Map<String, String> parameters) {
        try {
            Map<String, ParameterDef> defs = parseParameterMetadata(parameters);
            ObjectNode schema = OBJECT_MAPPER.createObjectNode();
            schema.put("type", "object");

            ObjectNode properties = schema.putObject("properties");
            List<String> required = new ArrayList<>();

            for (Map.Entry<String, ParameterDef> entry : defs.entrySet()) {
                String name = entry.getKey();
                ParameterDef def = entry.getValue();

                ObjectNode prop = properties.putObject(name);
                prop.put("type", mapType(def.type));

                if (def.description != null) {
                    prop.put("description", def.description);
                }
                if (def.enumValues != null && !def.enumValues.isEmpty()) {
                    ArrayNode enumArray = prop.putArray("enum");
                    def.enumValues.forEach(v -> enumArray.add(v.trim()));
                }
                if (def.required) {
                    required.add(name);
                }
            }

            if (!required.isEmpty()) {
                ArrayNode requiredArray = schema.putArray("required");
                required.forEach(requiredArray::add);
            }

            return OBJECT_MAPPER.writeValueAsString(schema);
        } catch (Exception e) {
            throw new RuntimeCamelException("Error building JSON schema from parameters", e);
        }
    }

    private static String mapType(String type) {
        return switch (type.toLowerCase()) {
            case "integer", "int", "long" -> "integer";
            case "number", "double", "float" -> "number";
            case "boolean", "bool" -> "boolean";
            default -> "string";
        };
    }

    /**
     * Holds structured metadata for a single tool parameter.
     */
    public static class ParameterDef {
        public String type = "string";
        public String description;
        public boolean required;
        public List<String> enumValues;
    }
}
