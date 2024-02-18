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

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class LangchainEmbeddingTest extends CamelTestSupport {

    private EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

    @Override
    protected RouteBuilder createRouteBuilder() {
        this.context.getRegistry().bind("embeddingModel", embeddingModel);

        return new RouteBuilder() {
            public void configure() {
                from("direct:embed-string")
                        .to("langchain:embed?embeddingModel=#embeddingModel")
                        .to("mock:response");
            };
        };
    }

    @Test
    void testSendMessage() throws InterruptedException {
        MockEndpoint mockErrorHandler = this.context.getEndpoint("mock:response", MockEndpoint.class);
        mockErrorHandler.expectedMessageCount(1);

        Embedding response = template.requestBody("direct:embed-string", "Hello my name is Dark Vader!", Embedding.class);
        mockErrorHandler.assertIsSatisfied();
        assertNotNull(response);
    }
}
