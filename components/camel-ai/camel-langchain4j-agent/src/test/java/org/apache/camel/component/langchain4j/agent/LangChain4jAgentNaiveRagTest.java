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
package org.apache.camel.component.langchain4j.agent;

import java.util.List;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static java.time.Duration.ofSeconds;
import static org.apache.camel.component.langchain4j.agent.LangChain4jAgent.Headers.SYSTEM_MESSAGE;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "OPENAI_API_KEY", matches = ".*", disabledReason = "OpenAI API key required")
public class LangChain4jAgentNaiveRagTest extends CamelTestSupport {

    private static final String SYSTEM_MESSAGE_CUSTOMER_SERVICE
            = "You are a friendly customer service representative for Miles of Camels Car Rental. Always be helpful and polite.";

    private static final String COMPANY_TERMS_OF_USE = """
            Miles of Camels Car Rental - Terms of Use

            1. RENTAL AGREEMENT
            This agreement is between Miles of Camels Car Rental ("Company") and the customer ("Renter").

            2. CANCELLATION POLICY
            Cancellations made 24 hours before pickup: Full refund
            Cancellations made 12-24 hours before pickup: 50% refund
            Cancellations made less than 12 hours before pickup: No refund

            3. VEHICLE RETURN
            Vehicles must be returned with the same fuel level as at pickup.
            Late returns incur a fee of $25 per hour or fraction thereof.

            4. DAMAGE POLICY
            Minor damages under $200: Covered by insurance
            Major damages over $200: Customer responsibility

            5. INSURANCE
            Basic insurance is included. Premium insurance available for $15/day.

            6. AGE REQUIREMENTS
            Minimum age: 21 years old
            Drivers under 25: Additional surcharge of $20/day
            """;

    protected ChatModel chatModel;
    protected ContentRetriever contentRetriever;
    private String openAiApiKey;

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        openAiApiKey = System.getenv("OPENAI_API_KEY");
        if (openAiApiKey == null || openAiApiKey.trim().isEmpty()) {
            throw new IllegalStateException("OPENAI_API_KEY system property is required for testing");
        }
        chatModel = createModel();
        contentRetriever = createContentRetriever();
    }

    protected ChatModel createModel() {
        return OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(GPT_4_O_MINI)
                .temperature(1.0)
                .timeout(ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    protected ContentRetriever createContentRetriever() {

        // Create a document from the terms of use constant
        Document document = Document.from(COMPANY_TERMS_OF_USE);

        DocumentSplitter splitter = DocumentSplitters.recursive(300, 0);
        List<TextSegment> segments = splitter.split(document);

        EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
        embeddingStore.addAll(embeddings, segments);

        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(2)
                .minScore(0.5)
                .build();

        return contentRetriever;
    }

    @Test
    void testAgentWithRagBasicQuery() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:rag-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBodyAndHeader(
                "direct:agent-with-rag",
                "What is the cancellation policy?",
                SYSTEM_MESSAGE,
                SYSTEM_MESSAGE_CUSTOMER_SERVICE,
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertTrue(response.toLowerCase().contains("cancel") || response.toLowerCase().contains("refund"),
                "Response should contain cancellation information: " + response);
        assertTrue(response.contains("24 hours") || response.contains("12 hours"),
                "Response should mention timeframes: " + response);
    }

    @Test
    void testAgentWithRagInsuranceQuery() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:rag-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBodyAndHeader(
                "direct:agent-with-rag",
                "Tell me about insurance coverage options",
                SYSTEM_MESSAGE,
                SYSTEM_MESSAGE_CUSTOMER_SERVICE,
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertTrue(response.toLowerCase().contains("insurance"),
                "Response should contain insurance information: " + response);
        assertTrue(response.contains("$15") || response.contains("premium") || response.contains("basic"),
                "Response should mention insurance options: " + response);
    }

    @Test
    void testAgentWithRagAgeRequirements() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:rag-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBodyAndHeader(
                "direct:agent-with-rag",
                "How old do I need to be to rent a car?",
                SYSTEM_MESSAGE,
                SYSTEM_MESSAGE_CUSTOMER_SERVICE,
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertTrue(response.contains("21") || response.contains("age"),
                "Response should mention age requirements: " + response);
    }

    @Test
    void testAgentWithRagDamagePolicy() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:rag-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBodyAndHeader(
                "direct:agent-with-rag",
                "What happens if I damage the car?",
                SYSTEM_MESSAGE,
                SYSTEM_MESSAGE_CUSTOMER_SERVICE,
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertTrue(response.toLowerCase().contains("damage") || response.toLowerCase().contains("$200"),
                "Response should contain damage policy information: " + response);
    }

    @Test
    void testAgentWithRagReturnPolicy() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:rag-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBodyAndHeader(
                "direct:agent-with-rag",
                "What are the rules for returning the vehicle?",
                SYSTEM_MESSAGE,
                SYSTEM_MESSAGE_CUSTOMER_SERVICE,
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertTrue(
                response.toLowerCase().contains("fuel") || response.toLowerCase().contains("return")
                        || response.contains("$25"),
                "Response should contain return policy information: " + response);
    }

    @Test
    void testAgentWithRagSystemMessage() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:rag-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBodyAndHeader(
                "direct:agent-with-rag",
                "What's your cancellation policy?",
                SYSTEM_MESSAGE,
                SYSTEM_MESSAGE_CUSTOMER_SERVICE,
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertTrue(response.toLowerCase().contains("cancel") || response.toLowerCase().contains("refund"),
                "Response should contain cancellation information: " + response);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        this.context.getRegistry().bind("chatModel", chatModel);
        this.context.getRegistry().bind("contentRetriever", contentRetriever);

        return new RouteBuilder() {
            public void configure() {
                from("direct:agent-with-rag")
                        .to("langchain4j-agent:test-rag-agent?chatModel=#chatModel&contentRetriever=#contentRetriever")
                        .to("mock:rag-response");
            }
        };
    }
}
