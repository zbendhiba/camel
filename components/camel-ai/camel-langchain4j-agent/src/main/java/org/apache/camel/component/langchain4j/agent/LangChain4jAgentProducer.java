package org.apache.camel.component.langchain4j.agent;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;

public class LangChain4jAgentProducer extends DefaultProducer {

    private final LangChain4jAgentEndpoint endpoint;

    public LangChain4jAgentProducer(LangChain4jAgentEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {


    }

}
