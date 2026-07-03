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
import java.util.Objects;

import org.apache.camel.Consumer;

/**
 * Framework-agnostic description of a Camel route registered as an LLM tool.
 * <p>
 * Stores both a structured {@link AiToolParameterHelper.ParameterDef} map (used by LangChain4j to build native schema
 * types without re-parsing) and a pre-built JSON Schema string (used directly by Spring AI and OpenAI).
 *
 * @since 4.22
 */
public class AiToolSpec {

    private final String name;
    private final String description;
    private final Map<String, AiToolParameterHelper.ParameterDef> parameterDefs;
    private final String parametersJsonSchema;
    private final Consumer consumer;
    private final boolean exposed;

    public AiToolSpec(
                      String name,
                      String description,
                      Map<String, AiToolParameterHelper.ParameterDef> parameterDefs,
                      String parametersJsonSchema,
                      Consumer consumer,
                      boolean exposed) {
        this.name = name;
        this.description = description;
        this.parameterDefs = parameterDefs;
        this.parametersJsonSchema = parametersJsonSchema;
        this.consumer = consumer;
        this.exposed = exposed;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Structured parameter definitions. Used by LangChain4j to build native {@code JsonObjectSchema} without re-parsing
     * the JSON Schema string.
     */
    public Map<String, AiToolParameterHelper.ParameterDef> getParameterDefs() {
        return parameterDefs;
    }

    /**
     * Pre-built JSON Schema string for the tool parameters. Used directly by Spring AI ({@code inputSchema}) and OpenAI
     * ({@code function.parameters}).
     */
    public String getParametersJsonSchema() {
        return parametersJsonSchema;
    }

    /**
     * The Camel consumer that executes this tool's route when the LLM invokes it.
     */
    public Consumer getConsumer() {
        return consumer;
    }

    /**
     * Whether this tool is automatically exposed to the LLM ({@code true}) or only discoverable via
     * {@code ToolSearchTool} ({@code false}).
     */
    public boolean isExposed() {
        return exposed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AiToolSpec that = (AiToolSpec) o;
        return exposed == that.exposed
                && Objects.equals(name, that.name)
                && Objects.equals(description, that.description)
                && Objects.equals(consumer, that.consumer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, consumer, exposed);
    }

    @Override
    public String toString() {
        return "AiToolSpec{name=" + name + ", exposed=" + exposed + '}';
    }
}
