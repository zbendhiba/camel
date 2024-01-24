package org.apache.camel.component.langchain;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

public class LangchainEmbeddingTest  extends CamelTestSupport {

    private EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

    @Override
    protected RouteBuilder createRouteBuilder() {
        this.context.getRegistry().bind("embeddingModel", embeddingModel);

        return new RouteBuilder() {
            public void configure() {
                from("direct:embed-string")
                        .to("langchain:embed?embeddingModel=#embeddingModel")
                        .to("mock:response");
            };
        };
    }

    @Test
    void testSendMessage() throws InterruptedException {
        MockEndpoint mockErrorHandler = this.context.getEndpoint("mock:response", MockEndpoint.class);
        mockErrorHandler.expectedMessageCount(1);

        Embedding response = template.requestBody("direct:embed-string", "Hello my name is Dark Vader!", Embedding.class);
        mockErrorHandler.assertIsSatisfied();
        assertNotNull(response);
    }
}
