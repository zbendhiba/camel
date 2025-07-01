package org.apache.camel.component.langchain4j.agent;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.UriParams;

@Configurer
@UriParams
public class LangChain4jAgentConfiguration implements Cloneable {

    public LangChain4jAgentConfiguration() {
    }

    public LangChain4jAgentConfiguration copy() {
        try {
            return (LangChain4jAgentConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
