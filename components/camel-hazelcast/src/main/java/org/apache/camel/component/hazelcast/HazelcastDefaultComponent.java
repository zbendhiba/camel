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
package org.apache.camel.component.hazelcast;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.hazelcast.HazelcastInstanceHepler.getOrCreateHzClientInstance;
import static org.apache.camel.component.hazelcast.HazelcastInstanceHepler.getOrCreateHzInstance;

public abstract class HazelcastDefaultComponent extends DefaultComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(HazelcastDefaultComponent.class);

    private final Set<HazelcastInstance> customHazelcastInstances;
    @Metadata(label = "advanced")
    private HazelcastInstance hazelcastInstance;
    @Metadata(label = "advanced", defaultValue = "" + HazelcastConstants.HAZELCAST_NODE_MODE)
    private String hazelcastMode = HazelcastConstants.HAZELCAST_NODE_MODE;

    public HazelcastDefaultComponent() {
        this.customHazelcastInstances = new LinkedHashSet<>();
    }

    public HazelcastDefaultComponent(final CamelContext context) {
        super(context);
        this.customHazelcastInstances = new LinkedHashSet<>();
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        // use the given hazelcast Instance or create one if not given
        HazelcastInstance hzInstance;
        if (ObjectHelper.equal(getHazelcastMode(), HazelcastConstants.HAZELCAST_NODE_MODE)) {
            hzInstance = getOrCreateHzInstance(getCamelContext(), parameters, this, hazelcastInstance);
        } else {
            hzInstance = getOrCreateHzClientInstance(getCamelContext(), parameters, this, hazelcastInstance);
        }

        String defaultOperation
                = getAndRemoveOrResolveReferenceParameter(parameters, HazelcastConstants.OPERATION_PARAM, String.class);
        if (defaultOperation == null) {
            defaultOperation = getAndRemoveOrResolveReferenceParameter(parameters, "defaultOperation", String.class);
        }

        HazelcastDefaultEndpoint endpoint = doCreateEndpoint(uri, remaining, parameters, hzInstance);
        if (defaultOperation != null) {
            endpoint.setDefaultOperation(HazelcastOperation.getHazelcastOperation(defaultOperation));
        }

        setProperties(endpoint, parameters);

        return endpoint;
    }

    protected abstract HazelcastDefaultEndpoint doCreateEndpoint(
            String uri, String remaining, Map<String, Object> parameters, HazelcastInstance hzInstance)
            throws Exception;

    @Override
    public void doStart() throws Exception {
        super.doStart();
    }

    @Override
    public void doStop() throws Exception {
        for (HazelcastInstance hazelcastInstance : customHazelcastInstances) {
            hazelcastInstance.getLifecycleService().shutdown();
        }

        customHazelcastInstances.clear();

        super.doStop();
    }

    public HazelcastInstance getHazelcastInstance() {
        return hazelcastInstance;
    }

    /**
     * The hazelcast instance reference which can be used for hazelcast endpoint. If you don't specify the instance
     * reference, camel use the default hazelcast instance from the camel-hazelcast instance.
     */
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    public String getHazelcastMode() {
        return hazelcastMode;
    }

    /**
     * The hazelcast mode reference which kind of instance should be used. If you don't specify the mode, then the node
     * mode will be the default.
     */
    public void setHazelcastMode(String hazelcastMode) {
        this.hazelcastMode = hazelcastMode;
    }

    public boolean addCustomHazelcastInstance(HazelcastInstance instance) {
        return this.customHazelcastInstances.add(instance);
    }

}
