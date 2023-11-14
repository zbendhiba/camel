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

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * ElasticsearchRestClient component which allows you to interface with Elasticsearch or OpenSearch using the Java Low
 * level Rest Client
 * <p>
 */
@UriEndpoint(firstVersion = "4.3.0", scheme = "elasticsearch-rest-client",
             title = "Elasticsearch Low level Rest Client",
             syntax = "elasticsearch-rest-client:name", producerOnly = true,
             category = { Category.SEARCH })
public class ElasticsearchRestClientEndpoint extends DefaultEndpoint {
    @UriPath
    @Metadata(required = true)
    private String name;
    @Metadata
    private ElasticsearchRestClientConfiguration elasticsearchRestClientConfiguration;

    public ElasticsearchRestClientEndpoint() {
    }

    public ElasticsearchRestClientEndpoint(String uri, ElasticsearchRestClientComponent component) {
        super(uri, component);
    }

    public Producer createProducer() throws Exception {
        return new ElasticsearchRestClientProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Cannot consume from an ElasticsearchEndpoint: " + getEndpointUri());
    }

    public String getName() {
        return name;
    }

    /**
     * Some description of this option, and what it does
     */
    public void setName(String name) {
        this.name = name;
    }

    public ElasticsearchRestClientConfiguration getElasticsearchRestClientConfiguration() {
        return elasticsearchRestClientConfiguration;
    }

    public void setElasticsearchRestClientConfiguration(
            ElasticsearchRestClientConfiguration elasticsearchRestClientConfiguration) {
        this.elasticsearchRestClientConfiguration = elasticsearchRestClientConfiguration;
    }

}
