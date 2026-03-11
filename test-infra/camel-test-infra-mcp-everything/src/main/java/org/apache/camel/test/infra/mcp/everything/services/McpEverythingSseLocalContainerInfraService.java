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
package org.apache.camel.test.infra.mcp.everything.services;

import java.time.Duration;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.mcp.everything.common.McpEverythingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Runs the MCP Everything Server as a Docker container in SSE transport mode, exposing port 3001.
 */
@InfraService(service = McpEverythingSseInfraService.class,
              description = "MCP Everything Server (SSE transport)",
              serviceAlias = { "mcp-everything-sse" })
public class McpEverythingSseLocalContainerInfraService
        implements McpEverythingSseInfraService, ContainerService<GenericContainer<?>> {

    private static final Logger LOG = LoggerFactory.getLogger(McpEverythingSseLocalContainerInfraService.class);

    private static final String DEFAULT_CONTAINER = "tzolov/mcp-everything-server:v3";

    private final GenericContainer<?> container;

    public McpEverythingSseLocalContainerInfraService() {
        this(LocalPropertyResolver.getProperty(McpEverythingSseLocalContainerInfraService.class,
                McpEverythingProperties.MCP_EVERYTHING_CONTAINER));
    }

    public McpEverythingSseLocalContainerInfraService(String imageName) {
        container = initContainer(imageName);
        String name = ContainerEnvironmentUtil.containerName(this.getClass());
        if (name != null) {
            container.withCreateContainerCmdModifier(cmd -> cmd.withName(name));
        }
    }

    public McpEverythingSseLocalContainerInfraService(GenericContainer<?> container) {
        this.container = container;
    }

    protected GenericContainer<?> initContainer(String imageName) {
        String image = imageName != null ? imageName : DEFAULT_CONTAINER;

        class TestInfraMcpEverythingSseContainer extends GenericContainer<TestInfraMcpEverythingSseContainer> {
            public TestInfraMcpEverythingSseContainer(boolean fixedPort) {
                super(DockerImageName.parse(image));

                withCommand("node", "dist/index.js", "sse");
                waitingFor(Wait.forLogMessage(".*(?:listening on port|Server is running on port).*\\n", 1))
                        .withStartupTimeout(Duration.ofMinutes(2L));

                ContainerEnvironmentUtil.configurePort(this, fixedPort, McpEverythingProperties.DEFAULT_PORT);
            }
        }

        return new TestInfraMcpEverythingSseContainer(ContainerEnvironmentUtil.isFixedPort(this.getClass()));
    }

    @Override
    public void registerProperties() {
        System.setProperty(McpEverythingProperties.MCP_EVERYTHING_URL, sseUrl());
        System.setProperty(McpEverythingProperties.MCP_EVERYTHING_HOST, host());
        System.setProperty(McpEverythingProperties.MCP_EVERYTHING_PORT, String.valueOf(port()));
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the MCP Everything Server container (SSE mode)");
        container.start();

        registerProperties();
        LOG.info("MCP Everything Server (SSE) running at {}", sseUrl());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the MCP Everything Server container (SSE mode)");
        container.stop();
    }

    @Override
    public GenericContainer<?> getContainer() {
        return container;
    }

    @Override
    public String host() {
        return container.getHost();
    }

    @Override
    public int port() {
        return container.getMappedPort(McpEverythingProperties.DEFAULT_PORT);
    }
}
