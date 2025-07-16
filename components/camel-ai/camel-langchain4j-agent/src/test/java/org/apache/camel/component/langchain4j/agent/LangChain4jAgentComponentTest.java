package org.apache.camel.component.langchain4j.agent;

import static java.time.Duration.ofSeconds;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class LangChain4jAgentComponentTest extends CamelTestSupport {

    protected ChatModel chatModel;

    private String openAiApiKey;

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        openAiApiKey = System.getenv("OPENAI_API_KEY");
        chatModel = createModel();
    }

    protected ChatModel createModel() {
        return chatModel = OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName("o4-mini")
                .temperature(1.0)
                .timeout(ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();
    }



    @Test
    void testSimpleAiService() throws InterruptedException {

        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        final String userMessage = "What is Apache Camel?";

        String response = template.requestBody("direct:send-simple-user-message", userMessage, String.class);
        mockEndpoint.assertIsSatisfied();
        assertNotNull(response);
        assertNotEquals(userMessage, response);
        assertTrue(response.contains("Apache Camel"));

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        this.context.getRegistry().bind("chatModel", chatModel);

        return new RouteBuilder() {
            public void configure() {
                from("direct:send-simple-user-message")
                        .to("langchain4j-agent:test-agent?chatModel=#chatModel")
                        .to("mock:response");

            }
        };
    }

}
