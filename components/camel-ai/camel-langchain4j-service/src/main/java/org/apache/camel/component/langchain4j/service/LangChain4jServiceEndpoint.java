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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

import static org.apache.camel.component.langchain4j.service.LangChain4jService.SCHEME;

/**
 * Invoke LangChain4j AI Services and agents from Camel routes.
 */
@UriEndpoint(firstVersion = "4.23.0", scheme = SCHEME,
             title = "LangChain4j Service",
             syntax = "langchain4j-service:serviceName",
             producerOnly = true,
             category = { Category.AI }, headersClass = LangChain4jServiceHeaders.class)
public class LangChain4jServiceEndpoint extends DefaultEndpoint {

    @Metadata(required = true)
    @UriPath(description = "The AI Service name to look up in the Camel registry")
    private final String serviceName;

    @UriParam
    private LangChain4jServiceConfiguration configuration;

    private Object serviceInstance;
    private Class<?> serviceInterface;
    private Method targetMethod;

    /**
     * Creates a new endpoint.
     *
     * @param uri           the endpoint URI
     * @param component     the component
     * @param serviceName   the AI Service name to look up in the registry
     * @param configuration the endpoint configuration
     */
    public LangChain4jServiceEndpoint(String uri, LangChain4jServiceComponent component, String serviceName,
                                      LangChain4jServiceConfiguration configuration) {
        super(uri, component);
        this.serviceName = serviceName;
        this.configuration = configuration;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        // 1. Registry lookup (FR-1)
        serviceInstance = getCamelContext().getRegistry().lookupByName(serviceName);
        if (serviceInstance == null) {
            throw new IllegalArgumentException("AI Service '" + serviceName + "' not found in registry");
        }

        // 2. Service interface resolution
        serviceInterface = resolveServiceInterface();

        // 3. Method resolution (FR-2, FR-3, FR-4)
        targetMethod = resolveTargetMethod();
    }

    /**
     * Resolves the AI Service interface from the instance.
     *
     * @return                          the resolved interface
     * @throws IllegalArgumentException if interface resolution fails
     */
    private Class<?> resolveServiceInterface() {
        List<Class<?>> candidateInterfaces = new ArrayList<>();

        // Collect candidate interfaces
        if (Proxy.isProxyClass(serviceInstance.getClass())) {
            // JDK proxy: use its implemented interfaces
            candidateInterfaces.addAll(Arrays.asList(serviceInstance.getClass().getInterfaces()));
        } else {
            // Plain bean or CDI client proxy: collect all interfaces from class hierarchy
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
                    "AI Service '" + serviceName + "' does not implement any valid business interface");
        } else if (filteredCandidates.size() > 1) {
            String interfaceNames = filteredCandidates.stream()
                    .map(Class::getName)
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(
                    "AI Service '" + serviceName + "' implements multiple business interfaces: " + interfaceNames
                                               + ". Cannot determine which interface to use.");
        }

        return filteredCandidates.get(0);
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
        List<Method> businessMethods = Arrays.stream(serviceInterface.getMethods())
                .filter(this::isBusinessMethod)
                .collect(Collectors.toList());

        if (businessMethods.isEmpty()) {
            throw new IllegalArgumentException(
                    "AI Service '" + serviceName + "' interface has no business methods");
        }

        String methodName = configuration.getMethod();

        if (businessMethods.size() == 1) {
            Method single = businessMethods.get(0);
            // An explicitly configured method name must still match (never ignore it silently)
            if (methodName != null && !methodName.isEmpty() && !single.getName().equals(methodName)) {
                throw new IllegalArgumentException(
                        "Method '" + methodName + "' not found on AI Service '" + serviceName
                                                   + "'. Available methods: " + single.getName());
            }
            return single;
        }

        // Multiple business methods: require explicit method parameter
        if (methodName == null || methodName.isEmpty()) {
            throw new IllegalArgumentException(
                    "AI Service '" + serviceName + "' has multiple methods. Specify method parameter. Available methods: "
                                               + listMethodNames(businessMethods));
        }

        // Find method by name
        List<Method> matchingMethods = businessMethods.stream()
                .filter(m -> m.getName().equals(methodName))
                .collect(Collectors.toList());

        if (matchingMethods.isEmpty()) {
            throw new IllegalArgumentException(
                    "Method '" + methodName + "' not found on AI Service '" + serviceName
                                               + "'. Available methods: " + listMethodNames(businessMethods));
        }

        if (matchingMethods.size() > 1) {
            throw new IllegalArgumentException(
                    "AI Service '" + serviceName + "' (endpoint: " + getEndpointUri()
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

        // Exclude Object methods
        String methodName = method.getName();
        if (methodName.equals("toString") || methodName.equals("equals") || methodName.equals("hashCode")) {
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

    @Override
    public Producer createProducer() throws Exception {
        return new LangChain4jServiceProducer(this, serviceInstance, serviceInterface, targetMethod);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported");
    }

    /**
     * Returns the AI Service name.
     *
     * @return the service name
     */
    public String getServiceName() {
        return serviceName;
    }

    /**
     * Returns the endpoint configuration.
     *
     * @return the configuration
     */
    public LangChain4jServiceConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Sets the endpoint configuration (for generated configurer support).
     *
     * @param configuration the configuration
     */
    public void setConfiguration(LangChain4jServiceConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Returns the resolved AI Service instance.
     *
     * @return the AI Service instance
     */
    public Object getServiceInstance() {
        return serviceInstance;
    }

    /**
     * Returns the resolved AI Service interface.
     *
     * @return the AI Service interface
     */
    public Class<?> getServiceInterface() {
        return serviceInterface;
    }

    /**
     * Returns the resolved target method.
     *
     * @return the target method
     */
    public Method getTargetMethod() {
        return targetMethod;
    }
}
