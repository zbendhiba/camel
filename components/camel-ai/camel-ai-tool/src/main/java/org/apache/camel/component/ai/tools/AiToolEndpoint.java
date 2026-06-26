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

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.ai.tools.AiTool.SCHEME;

/**
 * Framework-agnostic consumer endpoint that registers a Camel route as an LLM tool in the shared
 * {@link AiToolRegistry}.
 *
 * @since 4.21
 */
@UriEndpoint(
             firstVersion = "4.21.0",
             scheme = SCHEME,
             title = "AI Tool",
             syntax = "ai-tool:toolId",
             category = { Category.AI })
public class AiToolEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(AiToolEndpoint.class);

    @Metadata(required = true)
    @UriPath(description = "The tool id — a unique identifier for this tool definition")
    private final String toolId;

    @UriParam(description = "The component configuration")
    private AiToolConfiguration configuration;

    private AiToolSpec registeredSpec;

    public AiToolEndpoint(String uri, AiToolComponent component, String toolId,
                          AiToolConfiguration configuration) {
        super(uri, component);
        this.toolId = toolId;
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() {
        throw new UnsupportedOperationException(
                "ai-tool does not support producer mode. "
                                                + "Use a framework-specific component (langchain4j-tools, spring-ai-chat, openai) "
                                                + "with a matching tags parameter to invoke tools.");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        if (configuration.getDescription() == null) {
            throw new IllegalArgumentException(
                    "The 'description' parameter is required when using ai-tool as a consumer. "
                                               + "Example: ai-tool:myTool?tags=myTag&description=Does+something+useful");
        }

        Map<String, String> params = configuration.getParameters();
        Map<String, AiToolParameterHelper.ParameterDef> parameterDefs
                = (params != null && !params.isEmpty())
                        ? AiToolParameterHelper.parseParameterMetadata(params)
                        : Map.of();

        String jsonSchema = (params != null && !params.isEmpty())
                ? AiToolParameterHelper.buildJsonSchema(params)
                : null;

        AiToolConsumer consumer = new AiToolConsumer(this, processor);
        configureConsumer(consumer);

        registeredSpec = new AiToolSpec(
                toolId, configuration.getDescription(), parameterDefs, jsonSchema, consumer,
                configuration.isExposed());

        final AiToolRegistry registry = AiToolRegistry.getInstance();
        String tags = configuration.getTags();
        if (tags != null) {
            for (String tag : AiToolParameterHelper.splitTags(tags)) {
                String trimmed = tag.trim();
                if (configuration.isExposed()) {
                    LOG.debug("Registering tool '{}' with tag '{}'", toolId, trimmed);
                    registry.put(trimmed, registeredSpec);
                } else {
                    LOG.debug("Registering searchable tool '{}' with tag '{}'", toolId, trimmed);
                    registry.putSearchable(trimmed, registeredSpec);
                }
            }
        } else {
            if (configuration.isExposed()) {
                LOG.debug("Registering tool '{}' in default pool (no tags)", toolId);
                registry.putDefault(registeredSpec);
            } else {
                LOG.debug("Registering searchable tool '{}' in default pool (no tags)", toolId);
                registry.putDefaultSearchable(registeredSpec);
            }
        }

        return consumer;
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (registeredSpec != null) {
            final AiToolRegistry registry = AiToolRegistry.getInstance();
            String tags = configuration.getTags();
            if (tags != null) {
                for (String tag : AiToolParameterHelper.splitTags(tags)) {
                    String trimmed = tag.trim();
                    if (configuration.isExposed()) {
                        LOG.debug("Removing tool '{}' from tag '{}'", registeredSpec.getName(), trimmed);
                        registry.remove(trimmed, registeredSpec);
                    } else {
                        LOG.debug("Removing searchable tool '{}' from tag '{}'", registeredSpec.getName(), trimmed);
                        registry.removeSearchable(trimmed, registeredSpec);
                    }
                }
            } else {
                if (configuration.isExposed()) {
                    LOG.debug("Removing tool '{}' from default pool", registeredSpec.getName());
                    registry.removeDefault(registeredSpec);
                } else {
                    LOG.debug("Removing searchable tool '{}' from default pool", registeredSpec.getName());
                    registry.removeDefaultSearchable(registeredSpec);
                }
            }
        }
    }

    public String getToolId() {
        return toolId;
    }

    public AiToolConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(AiToolConfiguration configuration) {
        this.configuration = configuration;
    }
}
