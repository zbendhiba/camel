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
package org.apache.camel.component.langchain4j.service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.apache.camel.Exchange;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.NoSuchHeaderException;
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
 * <li>{@code @V("variableName")} - binds from header {@code CamelLangChain4jService<VariableName>}</li>
 * <li>{@code @MemoryId} - binds from header {@code CamelLangChain4jServiceMemoryId}</li>
 * </ul>
 * <p>
 * The AI Service is looked up in the registry and its target method resolved when the producer starts, so
 * {@code lazyStartProducer=true} defers both the resolution and its failures to the first message. Parameter binding
 * strategies are computed once at the same time for thread-safe, efficient invocation.
 */
public class LangChain4jServiceProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(LangChain4jServiceProducer.class);

    private Object serviceInstance;
    private Class<?> serviceInterface;
    private Method targetMethod;
    private List<ParameterBinding> parameterBindings;

    /**
     * Creates a new producer.
     *
     * @param endpoint the endpoint
     */
    public LangChain4jServiceProducer(LangChain4jServiceEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public LangChain4jServiceEndpoint getEndpoint() {
        return (LangChain4jServiceEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // Resolve on producer start (not endpoint init) so lazyStartProducer can defer failures
        serviceInstance = lookupServiceInstance();
        serviceInterface = resolveServiceInterface();
        targetMethod = resolveTargetMethod();
        parameterBindings = computeParameterBindings();

        LOG.debug("Started producer for AI Service '{}' method '{}' with {} parameter bindings",
                getEndpoint().getServiceName(), targetMethod.getName(), parameterBindings.size());
    }

    @Override
    protected void doStop() throws Exception {
        // Release the resolved state so a stopped producer does not pin the AI Service; doStart re-resolves
        serviceInstance = null;
        serviceInterface = null;
        targetMethod = null;
        parameterBindings = null;
        super.doStop();
    }

    /**
     * Looks up the AI Service instance in the Camel registry.
     *
     * @return                     the AI Service instance
     * @throws NoSuchBeanException if no bean is registered under the configured service name
     */
    private Object lookupServiceInstance() {
        String serviceName = getEndpoint().getServiceName();
        Object instance = getEndpoint().getCamelContext().getRegistry().lookupByName(serviceName);
        if (instance == null) {
            throw new NoSuchBeanException(serviceName);
        }
        return instance;
    }

    /**
     * Resolves the AI Service interface from the instance.
     *
     * @return                          the resolved interface
     * @throws IllegalArgumentException if interface resolution fails
     */
    private Class<?> resolveServiceInterface() {
        String serviceName = getEndpoint().getServiceName();
        List<Class<?>> candidateInterfaces = new ArrayList<>();

        // Collect candidate interfaces
        if (Proxy.isProxyClass(serviceInstance.getClass())) {
            // JDK proxy: use its implemented interfaces
            candidateInterfaces.addAll(Arrays.asList(serviceInstance.getClass().getInterfaces()));
        } else {
            // Plain bean: collect all interfaces from the class hierarchy. Framework client proxies
            // that add their own abstract interfaces (CDI/Spring AOP) are not supported, see the
            // component documentation
            Set<Class<?>> allInterfaces = new HashSet<>();
            Class<?> currentClass = serviceInstance.getClass();
            while (currentClass != null && currentClass != Object.class) {
                allInterfaces.addAll(Arrays.asList(currentClass.getInterfaces()));
                currentClass = currentClass.getSuperclass();
            }
            candidateInterfaces.addAll(allInterfaces);
        }

        // Filter out non-candidates
        List<Class<?>> filteredCandidates = candidateInterfaces.stream()
                .filter(iface -> !iface.getName().startsWith("java."))
                .filter(iface -> !iface.getName().startsWith("javax."))
                .filter(iface -> !iface.getName().startsWith("jakarta."))
                .filter(this::hasBusinessMethods)
                .collect(Collectors.toList());

        if (filteredCandidates.isEmpty()) {
            throw new IllegalArgumentException(
                    "AI Service '" + serviceName + "' (endpoint: " + getEndpoint().getEndpointUri()
                                               + ") does not implement any valid business interface");
        } else if (filteredCandidates.size() > 1) {
            String interfaceNames = filteredCandidates.stream()
                    .map(Class::getName)
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(
                    "AI Service '" + serviceName + "' (endpoint: " + getEndpoint().getEndpointUri()
                                               + ") implements multiple business interfaces: " + interfaceNames
                                               + ". Cannot determine which interface to use."
                                               + " Register a bean that exposes a single business interface instead,"
                                               + " for example one built with AiServices.builder(TheInterface.class).");
        }

        Class<?> resolved = filteredCandidates.get(0);
        // The producer invokes the service reflectively, so a non-public interface would fail later
        // with an obscure IllegalAccessException on the first message
        if (!Modifier.isPublic(resolved.getModifiers())) {
            throw new IllegalArgumentException(
                    "AI Service '" + serviceName + "' (endpoint: " + getEndpoint().getEndpointUri()
                                               + ") interface " + resolved.getName()
                                               + " is not public. The AI Service interface must be declared public so the producer can invoke it reflectively.");
        }
        return resolved;
    }

    /**
     * Checks if an interface has at least one business method.
     *
     * @param  iface the interface to check
     * @return       true if the interface has business methods
     */
    private boolean hasBusinessMethods(Class<?> iface) {
        return Arrays.stream(iface.getMethods())
                .anyMatch(this::isBusinessMethod);
    }

    /**
     * Resolves the target method to invoke on the AI Service interface.
     *
     * @return                          the resolved method
     * @throws IllegalArgumentException if method resolution fails
     */
    private Method resolveTargetMethod() {
        String serviceName = getEndpoint().getServiceName();
        List<Method> businessMethods = Arrays.stream(serviceInterface.getMethods())
                .filter(this::isBusinessMethod)
                .collect(Collectors.toList());

        if (businessMethods.isEmpty()) {
            // Defensive only: resolveServiceInterface never returns an interface without business methods
            throw new IllegalArgumentException(
                    "AI Service '" + serviceName + "' interface has no business methods");
        }

        String methodName = getEndpoint().getConfiguration().getMethod();

        if (businessMethods.size() == 1) {
            Method single = businessMethods.get(0);
            // An explicitly configured method name must still match (never ignore it silently)
            if (methodName != null && !methodName.isEmpty() && !single.getName().equals(methodName)) {
                throw new IllegalArgumentException(
                        "Method '" + methodName + "' not found on AI Service '" + serviceName
                                                   + "' (endpoint: " + getEndpoint().getEndpointUri()
                                                   + "). Available methods: " + single.getName());
            }
            return single;
        }

        // Multiple business methods: require explicit method parameter
        if (methodName == null || methodName.isEmpty()) {
            throw new IllegalArgumentException(
                    "AI Service '" + serviceName + "' (endpoint: " + getEndpoint().getEndpointUri()
                                               + ") has multiple methods. Specify method parameter. Available methods: "
                                               + listMethodNames(businessMethods));
        }

        // Find method by name
        List<Method> matchingMethods = businessMethods.stream()
                .filter(m -> m.getName().equals(methodName))
                .collect(Collectors.toList());

        if (matchingMethods.isEmpty()) {
            throw new IllegalArgumentException(
                    "Method '" + methodName + "' not found on AI Service '" + serviceName
                                               + "' (endpoint: " + getEndpoint().getEndpointUri()
                                               + "). Available methods: " + listMethodNames(businessMethods));
        }

        if (matchingMethods.size() > 1) {
            throw new IllegalArgumentException(
                    "AI Service '" + serviceName + "' (endpoint: " + getEndpoint().getEndpointUri()
                                               + ") has multiple methods named '" + methodName
                                               + "'. Method overloading is not supported: method names must be unique.");
        }

        return matchingMethods.get(0);
    }

    /**
     * Checks if a method is a business method (abstract instance method, not default, static, or Object method).
     *
     * @param  method the method to check
     * @return        true if the method is a business method
     */
    private boolean isBusinessMethod(Method method) {
        // Exclude default methods
        if (method.isDefault()) {
            return false;
        }

        // Exclude static methods
        if (Modifier.isStatic(method.getModifiers())) {
            return false;
        }

        // Exclude the Object methods by signature, so a business method that merely shares
        // a name (such as equals(String)) is not filtered out
        String methodName = method.getName();
        int parameterCount = method.getParameterCount();
        if ((methodName.equals("toString") || methodName.equals("hashCode")) && parameterCount == 0) {
            return false;
        }
        if (methodName.equals("equals") && parameterCount == 1 && method.getParameterTypes()[0] == Object.class) {
            return false;
        }

        return true;
    }

    /**
     * Lists the names of business methods for error messages.
     *
     * @param  methods the list of business methods
     * @return         a comma-separated list of method names
     */
    private String listMethodNames(List<Method> methods) {
        return methods.stream()
                .map(Method::getName)
                .sorted()
                .collect(Collectors.joining(", "));
    }

    /**
     * Computes parameter binding strategies by analyzing method parameters and their annotations.
     * <p>
     * The target method comes from the interface (resolveTargetMethod selects it from the interface's methods), so its
     * annotations are read directly without dynamic proxy reflection issues. Validates that parameter bindings are
     * unambiguous and fail-fast on configuration errors.
     *
     * @return                          list of parameter bindings, one per method parameter
     * @throws IllegalArgumentException if parameter binding configuration is invalid
     */
    private List<ParameterBinding> computeParameterBindings() {
        Parameter[] parameters = targetMethod.getParameters();
        List<ParameterBinding> bindings = new ArrayList<>(parameters.length);

        // Track @UserMessage and @MemoryId parameters for validation
        int userMessageCount = 0;
        int memoryIdCount = 0;

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
                bindings.add(new UserMessageBinding(paramType, paramName));
            } else if (vAnnotation != null) {
                String variableName = vAnnotation.value();
                if (variableName.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Parameter '" + paramName + "' on method '" + targetMethod.getName()
                                                       + "' of AI Service '" + getEndpoint().getServiceName()
                                                       + "' (endpoint: " + getEndpoint().getEndpointUri()
                                                       + ") has an empty @V template variable name.");
                }
                String headerName = buildTemplateVariableHeaderName(variableName);

                // The memory ID header is reserved for @MemoryId: a template variable mapping to it
                // would silently feed both parameters from the same header. Camel headers are
                // case-insensitive, so the comparison must be too
                if (LangChain4jServiceHeaders.MEMORY_ID.equalsIgnoreCase(headerName)) {
                    throw new IllegalArgumentException(
                            "Parameter '" + paramName + "' on method '" + targetMethod.getName()
                                                       + "' of AI Service '" + getEndpoint().getServiceName()
                                                       + "' (endpoint: " + getEndpoint().getEndpointUri()
                                                       + ") uses @V(\"" + variableName + "\") which maps to header '"
                                                       + headerName
                                                       + "', reserved for @MemoryId. Rename the template variable.");
                }

                // Check for duplicate header mappings. Camel headers are case-insensitive,
                // so two variables that differ only in case still feed from the same header
                String headerKey = headerName.toLowerCase(Locale.ROOT);
                if (headerToVariableName.containsKey(headerKey)) {
                    throw new IllegalArgumentException(
                            "Method '" + targetMethod.getName() + "' of AI Service '"
                                                       + getEndpoint().getServiceName() + "' (endpoint: "
                                                       + getEndpoint().getEndpointUri()
                                                       + ") has duplicate template variable mappings: parameters annotated with @V(\""
                                                       + headerToVariableName.get(headerKey) + "\") and @V(\"" + variableName
                                                       + "\") both map to header '" + headerName
                                                       + "' (headers are case-insensitive).");
                }
                headerToVariableName.put(headerKey, variableName);

                bindings.add(new TemplateVariableBinding(paramType, paramName, headerName));
            } else if (memoryIdAnnotation != null) {
                memoryIdCount++;
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

        // Validate: at most one @MemoryId parameter (they would all silently feed from the same header)
        if (memoryIdCount > 1) {
            throw new IllegalArgumentException(
                    "Method '" + targetMethod.getName() + "' of AI Service '" + getEndpoint().getServiceName()
                                               + "' (endpoint: " + getEndpoint().getEndpointUri()
                                               + ") has multiple @MemoryId parameters. Only one parameter can be annotated with @MemoryId.");
        }

        return bindings;
    }

    /**
     * Builds the header name for a template variable by uppercasing the first letter.
     *
     * @param  variableName the template variable name from @V annotation
     * @return              the header name (e.g., "context" -> "CamelLangChain4jServiceContext")
     */
    private String buildTemplateVariableHeaderName(String variableName) {
        String capitalized = Character.toUpperCase(variableName.charAt(0)) + variableName.substring(1);
        return LangChain4jServiceHeaders.TEMPLATE_VARIABLE_PREFIX + capitalized;
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
            result = targetMethod.invoke(serviceInstance, args);
        } catch (InvocationTargetException e) {
            // Unwrap and propagate the original cause, so onException clauses match its real type
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            } else if (cause instanceof Error) {
                throw (Error) cause;
            } else {
                throw new RuntimeException("Unexpected throwable from AI Service method", cause);
            }
        }

        // 3. Handle return value
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
    private static class UserMessageBinding implements ParameterBinding {
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
    private static class TemplateVariableBinding implements ParameterBinding {
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
                throw new NoSuchHeaderException(exchange, headerName, parameterType);
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
    private static class MemoryIdBinding implements ParameterBinding {
        private final Class<?> parameterType;
        private final String parameterName;

        MemoryIdBinding(Class<?> parameterType, String parameterName) {
            this.parameterType = parameterType;
            this.parameterName = parameterName;
        }

        @Override
        public Object resolve(Exchange exchange) throws Exception {
            Object headerValue = exchange.getMessage().getHeader(LangChain4jServiceHeaders.MEMORY_ID);
            if (headerValue == null) {
                throw new NoSuchHeaderException(exchange, LangChain4jServiceHeaders.MEMORY_ID, parameterType);
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
