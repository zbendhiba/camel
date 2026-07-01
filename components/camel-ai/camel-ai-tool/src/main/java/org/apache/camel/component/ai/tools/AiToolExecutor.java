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

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Framework-agnostic executor for Camel route tools. Handles the common logic of resolving the route processor from the
 * tool's consumer, populating an {@link Exchange} with tool arguments, invoking the route, and returning the result.
 * <p>
 * AI framework adapters (LangChain4j, Spring AI, OpenAI, MCP) only need to parse their native argument format into a
 * {@code Map<String, Object>} and call this executor — they do not need to know how routes are resolved or invoked.
 * <p>
 * This is an internal support class used by Camel AI framework adapters and is not intended for direct use by end
 * users.
 *
 * @since 4.22
 */
public final class AiToolExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(AiToolExecutor.class);

    private AiToolExecutor() {
    }

    /**
     * Executes a Camel route tool by resolving the route processor from the spec's consumer, populating the exchange
     * with the provided arguments, and invoking the route.
     * <p>
     * Only arguments whose names match the tool's declared parameters are set as exchange headers. Undeclared arguments
     * are skipped to prevent the LLM from injecting unexpected headers.
     *
     * @param  spec      the tool specification containing the consumer and declared parameters
     * @param  arguments the tool arguments as a name-value map; each framework adapter is responsible for parsing its
     *                   native format (JSON string, Map, etc.) into this map before calling
     * @param  exchange  the Camel exchange to populate with arguments and execute
     * @return           the route result as a string, or a descriptive error message if execution fails
     */
    public static String execute(AiToolSpec spec, Map<String, Object> arguments, Exchange exchange) {
        String toolName = spec.getName();

        Consumer consumer = spec.getConsumer();
        if (consumer == null) {
            LOG.error("No consumer available for tool '{}'", toolName);
            return String.format("Error executing tool '%s': no consumer available", toolName);
        }

        Processor routeProcessor = ((DefaultConsumer) consumer).getProcessor();
        if (routeProcessor == null) {
            LOG.error("No route processor available for tool '{}'", toolName);
            return String.format("Error executing tool '%s': no route processor available", toolName);
        }

        LOG.info("Executing Camel route tool: '{}'", toolName);

        try {
            if (arguments != null && !arguments.isEmpty()) {
                Set<String> declaredParams = spec.getParameterDefs() != null
                        ? spec.getParameterDefs().keySet()
                        : Set.of();

                for (Map.Entry<String, Object> entry : arguments.entrySet()) {
                    String name = entry.getKey();
                    if (!declaredParams.contains(name)) {
                        LOG.error("Undeclared tool argument '{}' for tool '{}' — the LLM sent a parameter "
                                  + "that is not declared in the tool specification",
                                name, toolName);
                        return String.format(
                                "Error executing tool '%s': undeclared argument '%s'", toolName, name);
                    }
                    exchange.getMessage().setHeader(name, entry.getValue());
                }
            }

            if (spec.getParameterDefs() != null) {
                for (Map.Entry<String, AiToolParameterHelper.ParameterDef> entry : spec.getParameterDefs().entrySet()) {
                    if (entry.getValue().required
                            && (arguments == null || !arguments.containsKey(entry.getKey()))) {
                        LOG.error("Missing required argument '{}' for tool '{}' — the LLM did not send "
                                  + "a parameter that is declared as required in the tool specification",
                                entry.getKey(), toolName);
                        return String.format(
                                "Error executing tool '%s': missing required argument '%s'", toolName, entry.getKey());
                    }
                }
            }

            routeProcessor.process(exchange);

            if (exchange.getException() != null) {
                Exception routeError = exchange.getException();
                LOG.error("Error executing tool '{}': {}", toolName, routeError.getMessage(), routeError);
                return String.format("Error executing tool '%s': %s", toolName, routeError.getMessage());
            }

            String result = exchange.getIn().getBody(String.class);
            LOG.info("Tool '{}' execution completed successfully", toolName);
            return result != null ? result : "No result";

        } catch (Exception e) {
            LOG.error("Error executing tool '{}': {}", toolName, e.getMessage(), e);
            return String.format("Error executing tool '%s': %s", toolName, e.getMessage());
        }
    }
}
