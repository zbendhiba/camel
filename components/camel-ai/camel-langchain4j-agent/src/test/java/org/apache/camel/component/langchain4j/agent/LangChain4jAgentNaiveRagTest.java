package org.apache.camel.component.langchain4j.agent;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "OPENAI_API_KEY", matches = ".*", disabledReason = "OpenAI API key required")
public class LangChain4jAgentNaiveRagTest extends CamelTestSupport {
}
