package org.apache.camel.test.infra.hazelcast.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;

public class HazelcastServiceFactory {

    private HazelcastServiceFactory() {

    }

    public static SimpleTestServiceBuilder<HazelcastService> builder() {
        return new SimpleTestServiceBuilder<>("arangodb");
    }

    public static HazelcastService createService() {
        return builder()
                .addLocalMapping(HazelcastLocalContainerService::new)
                .addRemoteMapping(HazelcastRemoteService::new)
                .build();
    }
}
