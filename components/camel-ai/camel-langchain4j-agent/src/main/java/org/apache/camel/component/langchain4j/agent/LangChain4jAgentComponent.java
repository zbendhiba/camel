package org.apache.camel.component.langchain4j.agent;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import static org.apache.camel.component.langchain4j.agent.LangChain4jAgent.SCHEME;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

@Component(SCHEME)
public class LangChain4jAgentComponent  extends DefaultComponent {
    @Metadata
    LangChain4jAgentConfiguration configuration;

    public LangChain4jAgentComponent() {
        this(null);
    }

    public LangChain4jAgentComponent(CamelContext context) {
        super(context);
        this.configuration = new LangChain4jAgentConfiguration();
    }

    public LangChain4jAgentConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The configuration
     *
     * @param configuration
     */
    public void setConfiguration(LangChain4jAgentConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        LangChain4jAgentConfiguration langchain4jChatConfiguration = this.configuration.copy();

        Endpoint endpoint = new LangChain4jAgentEndpoint(uri, this, remaining, langchain4jChatConfiguration);
        setProperties(endpoint, parameters);
        return endpoint;
    }
}
