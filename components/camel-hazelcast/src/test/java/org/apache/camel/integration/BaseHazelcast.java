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
package org.apache.camel.integration;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.CamelContext;
import org.apache.camel.component.hazelcast.HazelcastCamelTestHelper;
import org.apache.camel.test.infra.hazelcast.services.HazelcastService;
import org.apache.camel.test.infra.hazelcast.services.HazelcastServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

public class BaseHazelcast extends CamelTestSupport {
    protected static HazelcastInstance hazelcastInstance;

    @RegisterExtension
    protected static HazelcastService service = HazelcastServiceFactory.createService();

    @BeforeAll
    void beforeAll() {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getNetworkConfig().addAddress(service.getServiceAddress());
        hazelcastInstance = HazelcastClient.newHazelcastClient(clientConfig);

    }

    @AfterAll
    public static void afterAll() {
        if (hazelcastInstance != null) {
            hazelcastInstance.shutdown();
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        HazelcastCamelTestHelper.registerHazelcastComponents(context, hazelcastInstance);
        return context;
    }
}
