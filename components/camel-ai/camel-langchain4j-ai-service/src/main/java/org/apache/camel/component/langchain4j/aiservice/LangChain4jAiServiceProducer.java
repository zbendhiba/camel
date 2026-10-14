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
package org.apache.camel.component.langchain4j.aiservice;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Producer that invokes LangChain4j AI Service methods with parameter binding from Camel exchanges.
 * <p>
 * This producer performs reflection-based invocation of AI Service dynamic proxies, binding method parameters from
 * exchange body and headers according to LangChain4j annotations:
 * <ul>
 * <li>{@code @UserMessage} - binds from exchange body</li>
 * <li>{@code @V("variableName")} - binds from header {@code CamelLangChain4jAiService<VariableName>}</li>
 * <li>{@code @MemoryId} - binds from header {@code CamelLangChain4jAiServiceMemoryId}</li>
 * </ul>
 * <p>
 * Parameter binding strategies are computed once during initialization for thread-safe, efficient invocation.
 */
public class LangChain4jAiServiceProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(LangChain4jAiServiceProducer.class);

    private final Object aiServiceInstance;
    private final Class<?> aiServiceInterface;
    private final Method targetMethod;
    private List<ParameterBinding> parameterBindings;

    /**
     * Creates a new producer.
     *
     * @param endpoint           the endpoint
     * @param aiServiceInstance  the AI Service instance (dynamic proxy)
     * @param aiServiceInterface the AI Service interface
     * @param targetMethod       the method to invoke
     */
    public LangChain4jAiServiceProducer(
                                        LangChain4jAiServiceEndpoint endpoint,
                                        Object aiServiceInstance,
                                        Class<?> aiServiceInterface,
                                        Method targetMethod) {
        super(endpoint);
        this.aiServiceInstance = aiServiceInstance;
        this.aiServiceInterface = aiServiceInterface;
        this.targetMethod = targetMethod;
    }

    @Override
    public LangChain4jAiServiceEndpoint getEndpoint() {
        return (LangChain4jAiServiceEndpoint) super.getEndpoint();
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        // Compute parameter binding strategies
        parameterBindings = computeParameterBindings();

        LOG.debug("Initialized producer for AI Service '{}' method '{}' with {} parameter bindings",
                getEndpoint().getServiceName(), targetMethod.getName(), parameterBindings.size());
    }

    /**
     * Computes parameter binding strategies by analyzing method parameters and their annotations.
     * <p>
     * Reads annotations from the interface method (not the proxy's method object) to avoid dynamic proxy reflection
     * issues. Validates that parameter bindings are unambiguous and fail-fast on configuration errors.
     *
     * @return                          list of parameter bindings, one per method parameter
     * @throws IllegalArgumentException if parameter binding configuration is invalid
     */
    private List<ParameterBinding> computeParameterBindings() {
        // Get the interface method to read annotations (Technical Risk 1 mitigation)
        Method interfaceMethod;
        try {
            interfaceMethod = aiServiceInterface.getMethod(targetMethod.getName(), targetMethod.getParameterTypes());
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                    "Method '" + targetMethod.getName() + "' not found on interface "
                                               + aiServiceInterface.getName(),
                    e);
        }

        Parameter[] parameters = interfaceMethod.getParameters();
        List<ParameterBinding> bindings = new ArrayList<>(parameters.length);

        // Track @UserMessage parameters for validation
        int userMessageCount = 0;
        Parameter userMessageParameter = null;

        // Track template variable header names for duplicate detection
        Map<String, String> headerToVariableName = new HashMap<>();

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            Class<?> paramType = param.getType();
            String paramName = param.getName();

            // Check for annotations
            UserMessage userMessageAnnotation = param.getAnnotation(UserMessage.class);
            V vAnnotation = param.getAnnotation(V.class);
            MemoryId memoryIdAnnotation = param.getAnnotation(MemoryId.class);

            // Count how many binding annotations are present
            int annotationCount = 0;
            if (userMessageAnnotation != null) {
                annotationCount++;
            }
            if (vAnnotation != null) {
                annotationCount++;
            }
            if (memoryIdAnnotation != null) {
                annotationCount++;
            }

            // Validate: no conflicting annotations
            if (annotationCount > 1) {
                List<String> conflictingAnnotations = new ArrayList<>();
                if (userMessageAnnotation != null) {
                    conflictingAnnotations.add("@UserMessage");
                }
                if (vAnnotation != null) {
                    conflictingAnnotations.add("@V");
                }
                if (memoryIdAnnotation != null) {
                    conflictingAnnotations.add("@MemoryId");
                }
                throw new IllegalArgumentException(
                        "Parameter '" + paramName + "' on method '" + targetMethod.getName()
                                                   + "' of AI Service '" + getEndpoint().getServiceName()
                                                   + "' (endpoint: " + getEndpoint().getEndpointUri()
                                                   + ") has conflicting annotations: "
                                                   + String.join(" and ", conflictingAnnotations)
                                                   + ". A parameter can only have one binding annotation.");
            }

            // Create appropriate binding
            if (userMessageAnnotation != null) {
                userMessageCount++;
                userMessageParameter = param;
                bindings.add(new UserMessageBinding(paramType, paramName));
            } else if (vAnnotation != null) {
                String variableName = vAnnotation.value();
                String headerName = buildTemplateVariableHeaderName(variableName);

                // Check for duplicate header mappings
                if (headerToVariableName.containsKey(headerName)) {
                    throw new IllegalArgumentException(
                            "Method '" + targetMethod.getName() + "' of AI Service '"
                                                       + getEndpoint().getServiceName() + "' (endpoint: "
                                                       + getEndpoint().getEndpointUri()
                                                       + ") has duplicate template variable mappings: parameters annotated with @V('"
                                                       + headerToVariableName.get(headerName) + "') and @V('" + variableName
                                                       + "') both map to header '" + headerName + "'.");
                }
                headerToVariableName.put(headerName, variableName);

                bindings.add(new TemplateVariableBinding(paramType, paramName, headerName));
            } else if (memoryIdAnnotation != null) {
                bindings.add(new MemoryIdBinding(paramType, paramName));
            } else {
                // No annotation: fallback to body binding if it's the only parameter
                if (parameters.length == 1) {
                    bindings.add(new UserMessageBinding(paramType, paramName));
                } else {
                    throw new IllegalArgumentException(
                            "Parameter '" + paramName + "' on method '" + targetMethod.getName()
                                                       + "' of AI Service '" + getEndpoint().getServiceName()
                                                       + "' (endpoint: " + getEndpoint().getEndpointUri()
                                                       + ") has no binding annotation. When a method has multiple parameters, "
                                                       + "each parameter must be annotated with @UserMessage, @V, or @MemoryId.");
                }
            }
        }

        // Validate: at most one @UserMessage parameter
        if (userMessageCount > 1) {
            throw new IllegalArgumentException(
                    "Method '" + targetMethod.getName() + "' of AI Service '" + getEndpoint().getServiceName()
                                               + "' (endpoint: " + getEndpoint().getEndpointUri()
                                               + ") has multiple @UserMessage parameters. Only one parameter can be annotated with @UserMessage.");
        }

        return bindings;
    }

    /**
     * Builds the header name for a template variable by uppercasing the first letter.
     *
     * @param  variableName the template variable name from @V annotation
     * @return              the header name (e.g., "context" -> "CamelLangChain4jAiServiceContext")
     */
    private String buildTemplateVariableHeaderName(String variableName) {
        if (variableName == null || variableName.isEmpty()) {
            throw new IllegalArgumentException("Template variable name cannot be empty");
        }
        String capitalized = Character.toUpperCase(variableName.charAt(0)) + variableName.substring(1);
        return LangChain4jAiServiceHeaders.TEMPLATE_VARIABLE_PREFIX + capitalized;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // 1. Resolve parameter values
        Object[] args = new Object[parameterBindings.size()];
        for (int i = 0; i < parameterBindings.size(); i++) {
            ParameterBinding binding = parameterBindings.get(i);
            try {
                args[i] = binding.resolve(exchange);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Failed to resolve parameter '" + binding.getParameterName() + "' for method '"
                                                   + targetMethod.getName() + "' of AI Service '"
                                                   + getEndpoint().getServiceName() + "' (endpoint: "
                                                   + getEndpoint().getEndpointUri() + "): " + e.getMessage(),
                        e);
            }
        }

        // 2. Invoke method
        Object result;
        try {
            result = targetMethod.invoke(aiServiceInstance, args);
        } catch (InvocationTargetException e) {
            // Unwrap and propagate the cause (FR-9)
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            } else if (cause instanceof Error) {
                throw (Error) cause;
            } else {
                throw new RuntimeException("Unexpected throwable from AI Service method", cause);
            }
        }

        // 3. Handle return value (FR-8)
        if (targetMethod.getReturnType() != void.class && targetMethod.getReturnType() != Void.class) {
            exchange.getMessage().setBody(result);
        }
        // If void, leave body unchanged
    }

    /**
     * Strategy for resolving a method parameter value from an exchange.
     */
    interface ParameterBinding {
        /**
         * Resolves the parameter value from the exchange.
         *
         * @param  exchange  the exchange
         * @return           the resolved parameter value
         * @throws Exception if resolution fails
         */
        Object resolve(Exchange exchange) throws Exception;

        /**
         * Returns the parameter name for error messages.
         *
         * @return the parameter name
         */
        String getParameterName();
    }

    /**
     * Binds a parameter from the exchange body (for @UserMessage or single un-annotated parameter).
     */
    private class UserMessageBinding implements ParameterBinding {
        private final Class<?> parameterType;
        private final String parameterName;

        UserMessageBinding(Class<?> parameterType, String parameterName) {
            this.parameterType = parameterType;
            this.parameterName = parameterName;
        }

        @Override
        public Object resolve(Exchange exchange) throws Exception {
            Object body = exchange.getMessage().getBody();
            if (body == null) {
                throw new IllegalArgumentException(
                        "Message body is null but parameter '" + parameterName + "' expects a "
                                                   + parameterType.getName() + " user message");
            }
            // Use Camel type conversion
            return exchange.getContext().getTypeConverter().mandatoryConvertTo(parameterType, exchange, body);
        }

        @Override
        public String getParameterName() {
            return parameterName;
        }
    }

    /**
     * Binds a parameter from a header (for @V template variables).
     */
    private class TemplateVariableBinding implements ParameterBinding {
        private final Class<?> parameterType;
        private final String parameterName;
        private final String headerName;

        TemplateVariableBinding(Class<?> parameterType, String parameterName, String headerName) {
            this.parameterType = parameterType;
            this.parameterName = parameterName;
            this.headerName = headerName;
        }

        @Override
        public Object resolve(Exchange exchange) throws Exception {
            Object headerValue = exchange.getMessage().getHeader(headerName);
            if (headerValue == null) {
                throw new IllegalArgumentException(
                        "Required header '" + headerName + "' not found for parameter '" + parameterName + "'");
            }
            // Use Camel type conversion
            return exchange.getContext().getTypeConverter().mandatoryConvertTo(parameterType, exchange, headerValue);
        }

        @Override
        public String getParameterName() {
            return parameterName;
        }
    }

    /**
     * Binds a parameter from the memory ID header (for @MemoryId).
     */
    private class MemoryIdBinding implements ParameterBinding {
        private final Class<?> parameterType;
        private final String parameterName;

        MemoryIdBinding(Class<?> parameterType, String parameterName) {
            this.parameterType = parameterType;
            this.parameterName = parameterName;
        }

        @Override
        public Object resolve(Exchange exchange) throws Exception {
            Object headerValue = exchange.getMessage().getHeader(LangChain4jAiServiceHeaders.MEMORY_ID);
            if (headerValue == null) {
                throw new IllegalArgumentException(
                        "Required header '" + LangChain4jAiServiceHeaders.MEMORY_ID + "' not found for parameter '"
                                                   + parameterName + "'");
            }
            // Use Camel type conversion
            return exchange.getContext().getTypeConverter().mandatoryConvertTo(parameterType, exchange, headerValue);
        }

        @Override
        public String getParameterName() {
            return parameterName;
        }
    }
}
