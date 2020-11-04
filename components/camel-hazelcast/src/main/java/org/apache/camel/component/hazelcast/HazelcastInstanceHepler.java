package org.apache.camel.component.hazelcast;

import java.io.InputStream;
import java.util.Map;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.CamelContext;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.hazelcast.HazelcastConstants.HAZELCAST_CONFIGU_PARAM;
import static org.apache.camel.component.hazelcast.HazelcastConstants.HAZELCAST_CONFIGU_URI_PARAM;
import static org.apache.camel.component.hazelcast.HazelcastConstants.HAZELCAST_INSTANCE_NAME_PARAM;
import static org.apache.camel.component.hazelcast.HazelcastConstants.HAZELCAST_INSTANCE_PARAM;

public class HazelcastInstanceHepler {
    private static final Logger LOGGER = LoggerFactory.getLogger(HazelcastInstanceHepler.class);

    protected static HazelcastInstance getOrCreateHzInstance(
            CamelContext context, Map<String, Object> parameters, HazelcastDefaultComponent component,
            HazelcastInstance componentInstance)
            throws Exception {
        HazelcastInstance hzInstance = null;
        Config config = null;

        // Query param named 'hazelcastInstance' (if exists) overrides the instance that was set
        hzInstance
                = component.resolveAndRemoveReferenceParameter(parameters, HAZELCAST_INSTANCE_PARAM, HazelcastInstance.class);

        // Check if an already created instance is given then just get instance by its name.
        if (hzInstance == null && parameters.get(HAZELCAST_INSTANCE_NAME_PARAM) != null) {
            hzInstance = Hazelcast.getHazelcastInstanceByName((String) parameters.get(HAZELCAST_INSTANCE_NAME_PARAM));
        }

        // If instance neither supplied nor found by name, try to lookup its config
        // as reference or as xml configuration file.
        if (hzInstance == null) {
            config = component.resolveAndRemoveReferenceParameter(parameters, HAZELCAST_CONFIGU_PARAM, Config.class);
            if (config == null) {
                String configUri = component.getAndRemoveParameter(parameters, HAZELCAST_CONFIGU_URI_PARAM, String.class);
                if (configUri != null) {
                    configUri = component.getCamelContext().resolvePropertyPlaceholders(configUri);
                }
                if (configUri != null) {
                    InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(context, configUri);
                    config = new XmlConfigBuilder(is).build();
                }
            }

            if (componentInstance == null && config == null) {
                config = new XmlConfigBuilder().build();
                // Disable the version check
                config.getProperties().setProperty("hazelcast.version.check.enabled", "false");
                config.getProperties().setProperty("hazelcast.phone.home.enabled", "false");

                hzInstance = Hazelcast.newHazelcastInstance(config);
            } else if (config != null) {
                if (ObjectHelper.isNotEmpty(config.getInstanceName())) {
                    hzInstance = Hazelcast.getOrCreateHazelcastInstance(config);
                } else {
                    hzInstance = Hazelcast.newHazelcastInstance(config);
                }
            }

            if (hzInstance != null) {
                if (component.addCustomHazelcastInstance(hzInstance)) {
                    LOGGER.debug("Add managed HZ instance {}", hzInstance.getName());
                }
            }
        }

        return hzInstance == null ? componentInstance : hzInstance;
    }

    protected static HazelcastInstance getOrCreateHzClientInstance(
            CamelContext context, Map<String, Object> parameters, HazelcastDefaultComponent component,
            HazelcastInstance componentInstance)
            throws Exception {
        HazelcastInstance hzInstance = null;
        ClientConfig config = null;

        // Query param named 'hazelcastInstance' (if exists) overrides the instance that was set
        hzInstance
                = component.resolveAndRemoveReferenceParameter(parameters, HAZELCAST_INSTANCE_PARAM, HazelcastInstance.class);

        // Check if an already created instance is given then just get instance by its name.
        if (hzInstance == null && parameters.get(HAZELCAST_INSTANCE_NAME_PARAM) != null) {
            hzInstance = Hazelcast.getHazelcastInstanceByName((String) parameters.get(HAZELCAST_INSTANCE_NAME_PARAM));
        }

        // If instance neither supplied nor found by name, try to lookup its config
        // as reference or as xml configuration file.
        if (hzInstance == null) {
            config = component.resolveAndRemoveReferenceParameter(parameters, HAZELCAST_CONFIGU_PARAM, ClientConfig.class);
            if (config == null) {
                String configUri = component.getAndRemoveParameter(parameters, HAZELCAST_CONFIGU_URI_PARAM, String.class);
                if (configUri != null) {
                    configUri = context.resolvePropertyPlaceholders(configUri);
                }
                if (configUri != null) {
                    InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(context, configUri);
                    config = new XmlClientConfigBuilder(is).build();
                }
            }

            if (componentInstance == null && config == null) {
                config = new XmlClientConfigBuilder().build();
                // Disable the version check
                config.getProperties().setProperty("hazelcast.version.check.enabled", "false");
                config.getProperties().setProperty("hazelcast.phone.home.enabled", "false");

                hzInstance = HazelcastClient.newHazelcastClient(config);
            } else if (config != null) {
                hzInstance = HazelcastClient.newHazelcastClient(config);
            }

            if (hzInstance != null) {
                if (component.addCustomHazelcastInstance(hzInstance)) {
                    LOGGER.debug("Add managed HZ instance {}", hzInstance.getName());
                }
            }
        }

        return hzInstance == null ? componentInstance : hzInstance;
    }
}
