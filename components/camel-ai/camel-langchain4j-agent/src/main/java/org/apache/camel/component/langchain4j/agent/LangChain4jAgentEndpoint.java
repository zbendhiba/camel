package org.apache.camel.component.langchain4j.agent;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import static org.apache.camel.component.langchain4j.agent.LangChain4jAgent.SCHEME;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

@UriEndpoint(firstVersion = "4.13.0", scheme = SCHEME,
        title = "LangChain4j Agent",
        syntax = "langchain4j-agent:agentId",
        category = { Category.AI }, headersClass = LangChain4jAgent.Headers.class)
public class LangChain4jAgentEndpoint extends DefaultEndpoint {
    @Metadata(required = true)
    @UriPath(description = "The Agent id")
    private final String agentId;

    @UriParam
    private LangChain4jAgentConfiguration configuration;

    public LangChain4jAgentEndpoint(String endpointUri, Component component, String agentId, LangChain4jAgentConfiguration configuration) {
        super(endpointUri, component);
        this.agentId = agentId;
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new LangChain4jAgentProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return null;
    }


    /**
     * Return the Agent ID
     *
     * @return
     */
    public String getAgentId() {
        return agentId;
    }
}
