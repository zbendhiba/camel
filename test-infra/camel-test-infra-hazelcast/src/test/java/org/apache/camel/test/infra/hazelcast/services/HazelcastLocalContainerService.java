package org.apache.camel.test.infra.hazelcast.services;

import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.hazelcast.common.HazelcastProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HazelcastLocalContainerService implements HazelcastService, ContainerService<HazelcastContainer> {

    private static final Logger LOG = LoggerFactory.getLogger(HazelcastLocalContainerService.class);

    private final HazelcastContainer container;

    public HazelcastLocalContainerService() {
        this(System.getProperty(HazelcastProperties.HAZELCAST_CONTAINER, HazelcastContainer.HAZELCAST_IMAGE));
    }

    public HazelcastLocalContainerService(String imageName) {
        container = initContainer(imageName);
    }

    protected HazelcastContainer initContainer(String imageName) {
        return new HazelcastContainer(imageName);
    }

    @Override
    public int getPort() {
        return container.getServicePort();
    }

    @Override
    public String getHost() {
        return container.getHost();
    }

    @Override
    public void registerProperties() {
        System.setProperty(HazelcastProperties.HAZELCAST_HOST, container.getHost());
        System.setProperty(HazelcastProperties.HAZELCAST_PORT, String.valueOf(container.getServicePort()));
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the Hazelcast container");
        container.start();

        registerProperties();
        LOG.info("Hazelcast instance running at {}", getServiceAddress());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the Hazelcast container");
        container.stop();
    }

    @Override
    public HazelcastContainer getContainer() {
        return container;
    }
}
