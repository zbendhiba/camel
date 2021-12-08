package org.apache.camel.test.infra.hazelcast.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

public class HazelcastContainer extends GenericContainer {

    public static final Integer PORT_DEFAULT = 5701;
    public static final String HAZELCAST_IMAGE = "hazelcast/hazelcast:5.0";

    private static final Logger LOGGER = LoggerFactory.getLogger(HazelcastContainer.class);
    private static final String CONTAINER_NAME = "hazelcast";

    public HazelcastContainer() {
        this(HAZELCAST_IMAGE);
    }

    public HazelcastContainer(String containerName) {
        super(containerName);

        setWaitStrategy(Wait.forListeningPort());
        addFixedExposedPort(PORT_DEFAULT, PORT_DEFAULT);
        withNetworkAliases(CONTAINER_NAME);
        withLogConsumer(new Slf4jLogConsumer(LOGGER));
        waitingFor(Wait.forLogMessage(".*is STARTED.*", 1));
    }

    public int getServicePort() {
        return getMappedPort(PORT_DEFAULT);
    }
}
