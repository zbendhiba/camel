package org.apache.camel.component.elasticsearch.rest.client;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticsearchRestClientProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchRestClientProducer.class);
    private ElasticsearchRestClientEndpoint endpoint;

    public ElasticsearchRestClientProducer(ElasticsearchRestClientEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public void process(Exchange exchange) throws Exception {
        System.out.println(exchange.getIn().getBody());
    }

}
