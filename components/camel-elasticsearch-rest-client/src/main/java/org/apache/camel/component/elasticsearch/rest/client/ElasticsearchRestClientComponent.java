package org.apache.camel.component.elasticsearch.rest.client;

import java.util.Map;

import org.apache.camel.Endpoint;

import org.apache.camel.support.DefaultComponent;

@org.apache.camel.spi.annotations.Component("elasticsearchRestClient")
public class ElasticsearchRestClientComponent extends DefaultComponent {

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Endpoint endpoint = new ElasticsearchRestClientEndpoint(uri, this);
        setProperties(endpoint, parameters);
        return endpoint;
    }
}
