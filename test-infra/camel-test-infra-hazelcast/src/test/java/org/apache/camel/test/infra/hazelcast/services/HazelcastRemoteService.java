package org.apache.camel.test.infra.hazelcast.services;

import org.apache.camel.test.infra.hazelcast.common.HazelcastProperties;

public class HazelcastRemoteService implements HazelcastService {
    @Override
    public void registerProperties() {

    }

    @Override
    public void initialize() {

    }

    @Override
    public void shutdown() {

    }

    @Override
    public String getHost() {
        return System.getProperty(HazelcastProperties.HAZELCAST_HOST);
    }

    @Override
    public int getPort() {
        return Integer.parseInt(System.getProperty(HazelcastProperties.HAZELCAST_PORT));
    }
}
