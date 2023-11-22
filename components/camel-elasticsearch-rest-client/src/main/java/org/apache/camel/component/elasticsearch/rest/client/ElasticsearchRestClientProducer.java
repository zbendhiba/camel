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
import java.io.UnsupportedEncodingException;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.util.EntityUtils;
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

        String indexName = this.endpoint.getIndexName();

        //TODO to move on from POC mode, make sure we can auto create a RestClient using Camel
        RestClient restClient = this.endpoint.getRestClient();
        if (restClient == null) {
            throw new IllegalArgumentException(
                    "restClient value is mandatory");
        }

        try {
            Request request =   switch (operation) {
                    case CREATE_INDEX  -> createIndex(indexName, restClient);
                    case DELETE_INDEX  -> deleteIndex(indexName, restClient);
                    case INDEX -> index(indexName, exchange, restClient);
                    default -> null;
            };

            Response response = restClient.performRequest(request);

            String responseBody = EntityUtils.toString(response.getEntity());
            switch (operation) {
                case INDEX -> exchange.getMessage().setBody(responseBody);

            };
            callback.done(true);
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }

        return false;
    }

    private Request createIndex(String indexName, RestClient restClient) {
        //TODO add settings and mappings basic information + the possibility to set advanced settings with json
       return new Request(
                "PUT",
                "/" + indexName);

    }

    private Request deleteIndex(String indexName, RestClient restClient) {
        return new Request(
                "DELETE",
                "/" + indexName);

    }

    private Request index(String indexName, Exchange exchange, RestClient restClient) throws UnsupportedEncodingException {
        String body = exchange.getMessage().getBody(String.class);
        if (body == null) {
            throw new IllegalArgumentException(
                    "Index name value is mandatory when performing CREATE_INDEX operation");
        }

        Request request = new Request("POST", String.format("/%s/_doc", indexName));
        request.setEntity(new NStringEntity(body, ContentType.APPLICATION_JSON));

        return request;
    }

}
