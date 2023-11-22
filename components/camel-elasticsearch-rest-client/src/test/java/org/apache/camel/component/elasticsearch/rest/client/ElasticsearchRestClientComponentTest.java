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
package org.apache.camel.component.elasticsearch.rest.client;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;


import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ElasticsearchRestClientComponentTest extends CamelTestSupport {

    private static RestClient restClient;

    @BeforeAll
    public static void beforeAll() {

        restClient = RestClient.builder(
                new HttpHost("localhost", 9200, "http")).build();
    }

    @AfterAll
    public static void afterAll() throws IOException {
        if(restClient != null){
            restClient.close();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        this.context.getRegistry().bind("restClient", restClient);
        return new RouteBuilder() {
            public void configure() {
                from("direct:create-index")
                        .to("elasticsearch-rest-client:my-cluster?operation=CREATE_INDEX&restClient=#restClient&indexName=my_index");

                from("direct:delete-index")
                        .to("elasticsearch-rest-client:my-cluster?operation=DELETE_INDEX&restClient=#restClient&indexName=my_index");

                from("direct:index")
                        .to("elasticsearch-rest-client:my-cluster?operation=INDEX&restClient=#restClient&indexName=my_index");

            }
        };
    }

    @Test
    void testProducer() throws ExecutionException, InterruptedException {
        // create index
        CompletableFuture<Object> future = template.asyncSendBody("direct:create-index", null);
        future.get();

        // index a document
        var document = " {\"title\": \"Elastic is funny\",  \"tag\": [\"lucene\" ]}";
        CompletableFuture<String> response = template.asyncRequestBody("direct:index", document, String.class);
        String body = response.get();
        assertNotNull(body);

        // delete index
        future = template.asyncSendBody("direct:delete-index", null);
        future.get();
    }

}
