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
package org.apache.camel.component.langchain;

import java.io.IOException;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import static org.apache.camel.component.langchain.OllamaTestSupport.LOCAL_OLLAMA_IMAGE;
import static org.apache.camel.component.langchain.OllamaTestSupport.ORCA_MINI_MODEL;

public class OllamaContainer extends GenericContainer<OllamaContainer> {
    private static final Logger LOGGER = LoggerFactory.getLogger(OllamaContainer.class);

    //ollama default port
    public static final int CLIENT_PORT = 11434;

    private final DockerImageName dockerImageName;

    public OllamaContainer(DockerImageName image) {
        super(image);

        this.dockerImageName = image;
        withExposedPorts(CLIENT_PORT);
        withImagePullPolicy(dockerImageName -> !dockerImageName.getVersionPart().endsWith(ORCA_MINI_MODEL));
        setWaitStrategy(Wait.forListeningPort());
        withLogConsumer(new Slf4jLogConsumer(LOGGER));
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        if (!this.dockerImageName.equals(DockerImageName.parse(LOCAL_OLLAMA_IMAGE))) {
            try {
                LOGGER.info("Start pulling the '{}' model ... would take several minutes ...", ORCA_MINI_MODEL);
                execInContainer("ollama", "pull", ORCA_MINI_MODEL);
                LOGGER.info("Model pulling competed!");
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("Error pulling model", e);
            }
        }
    }

    String getBaseUrl() {
        return "http://" + getHost() + ":" + getMappedPort(CLIENT_PORT);
    }
}
