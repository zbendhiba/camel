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

import java.util.List;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Image;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.utility.DockerImageName;

public class OllamaTestSupport extends CamelTestSupport {

    public static final String CONTAINER_NAME = "ollama/ollama:latest";

    public static String ORCA_MINI_MODEL = "orca-mini";

    public static final String LOCAL_OLLAMA_IMAGE = String.format("tc-%s-%s", CONTAINER_NAME, ORCA_MINI_MODEL);
    public OllamaContainer container;

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();
        container = new OllamaContainer(resolve());
        container.start();
    }

    @Override
    public void cleanupResources() throws Exception {
        if (container != null) {
            container.stop();
        }
    }

    public ChatLanguageModel createModel() {
        return OllamaChatModel.builder()
                .baseUrl(container.getBaseUrl())
                .modelName(ORCA_MINI_MODEL)
                .temperature(0.3)
                .build();
    }

    protected DockerImageName resolve() {
        DockerImageName dockerImageName = DockerImageName.parse(CONTAINER_NAME);
        DockerClient dockerClient = DockerClientFactory.instance().client();
        List<Image> images = dockerClient.listImagesCmd().withReferenceFilter(LOCAL_OLLAMA_IMAGE).exec();
        if (images.isEmpty()) {
            return dockerImageName;
        }
        return DockerImageName.parse(LOCAL_OLLAMA_IMAGE);
    }

}
