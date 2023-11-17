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

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultAsyncProducer;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticsearchRestClientProducer extends DefaultAsyncProducer {
    private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchRestClientProducer.class);

    private ElasticsearchRestClientEndpoint endpoint;


    public ElasticsearchRestClientProducer(ElasticsearchRestClientEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {

        //TODO add possibility to get config from headers !?!

        // getting configuration from Endpoint
        ElasticsearchRestClientOperation operation = this.endpoint.getOperation();
        if (operation == null) {
            throw new IllegalArgumentException(
                    "Operation value is mandatory");
        }

        //TODO to move on from POC mode, make sure we can auto create a RestClient using Camel
        RestClient restClient = this.endpoint.getRestClient();
        if (restClient == null) {
            throw new IllegalArgumentException(
                    "restClient value is mandatory");
        }


        Request request =   switch (operation) {
                case CREATE_INDEX  -> createIndex(exchange, restClient);
                case DELETE_INDEX  -> deleteIndex(exchange, restClient);
                default -> null;
            };
        try {
            Response response = restClient.performRequest(request);
            callback.done(true);
        } catch (IOException e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }

        return false;
    }

    private Request createIndex(Exchange exchange, RestClient restClient) {
        String indexName = exchange.getMessage().getBody(String.class);
        if (indexName == null) {
            throw new IllegalArgumentException(
                    "Index name value is mandatory when performing CREATE_INDEX operation");
        }

        //TODO add settings and mappings basic information + the possibility to set advanced settings with json
       return new Request(
                "PUT",
                "/" + indexName);

    }

    private Request deleteIndex(Exchange exchange, RestClient restClient) {
        String indexName = exchange.getMessage().getBody(String.class);
        if (indexName == null) {
            throw new IllegalArgumentException(
                    "Index name value is mandatory when performing CREATE_INDEX operation");
        }

        return new Request(
                "DELETE",
                "/" + indexName);

    }

}
