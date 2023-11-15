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

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

public class ElasticsearchRestClientComponentTest extends CamelTestSupport {

    //private CamelContext context;
    private static RestClient restClient;

 /*   @BindToRegistry("restClient")
    RestClient restClient(){
        return RestClient.builder(
                new HttpHost("localhost", 9200, "http")).build();
    }*/

    @BeforeAll
    public static void beforeAll() {
         restClient = RestClient.builder(
                new HttpHost("localhost", 9200, "http")).build();
         System.out.println("Hello I'm connected to Elasticsearch");
    }

    @AfterAll
    public static void afterAll() throws IOException {
        if(restClient != null){
            restClient.close();
        }
        System.out.println("Hello I'm disconnected to Elasticsearch");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        this.context.getRegistry().bind("restClient", restClient);
        return new RouteBuilder() {
            public void configure() {
                from("direct:index")
                        .to("elasticsearch-rest-client:my-cluster?operation=CREATE_INDEX&restClient=#restClient");

            }
        };
    }

    /*@Test
    @Disabled
    public void testElasticsearchRestClient() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(5);

        mock.await();
    }*/

    @Test
    void createCreateIndex(){
        System.out.println("Hello test");

        template.sendBody("direct:index", "zineb_index");

        assertTrue(true);
    }

}
