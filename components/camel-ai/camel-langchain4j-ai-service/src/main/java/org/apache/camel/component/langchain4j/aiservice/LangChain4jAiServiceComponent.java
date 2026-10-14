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

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

import static org.apache.camel.component.langchain4j.aiservice.LangChain4jAiService.SCHEME;

/**
 * Component for invoking LangChain4j AI Services.
 * <p>
 * This component allows Camel routes to interact with AI services defined using LangChain4j's AI Service pattern. The
 * service instance must be registered in the Camel registry and is looked up by name from the endpoint URI.
 */
@Component(SCHEME)
public class LangChain4jAiServiceComponent extends DefaultComponent {

    public LangChain4jAiServiceComponent() {
        this(null);
    }

    public LangChain4jAiServiceComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        LangChain4jAiServiceConfiguration configuration = new LangChain4jAiServiceConfiguration();
        setProperties(configuration, parameters);
        return new LangChain4jAiServiceEndpoint(uri, this, remaining, configuration);
    }
}
