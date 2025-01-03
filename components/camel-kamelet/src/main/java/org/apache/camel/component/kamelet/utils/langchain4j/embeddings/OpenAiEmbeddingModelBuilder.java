package org.apache.camel.component.kamelet.utils.langchain4j.embeddings;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import static java.time.Duration.ofSeconds;
import static dev.langchain4j.model.openai.OpenAiEmbeddingModelName.TEXT_EMBEDDING_3_SMALL;


public final class OpenAiEmbeddingModelBuilder {
    private String apiKey;
    private String modelName;
    private long timeout;
    private int maxRetries;
    private int dimensions;
    private boolean logRequests;
    private boolean logResponses;

    public OpenAiEmbeddingModelBuilder apiKey(String apiKey){
        this.apiKey = apiKey;
        return this;
    }

    public OpenAiEmbeddingModelBuilder modelName(String modelName){
        this.modelName = modelName;
        return this;
    }

    public OpenAiEmbeddingModelBuilder timeout(long timeout){
        this.timeout = timeout;
        return this;
    }

    public OpenAiEmbeddingModelBuilder maxRetries(int maxRetries){
        this.maxRetries = maxRetries;
        return this;
    }

    public OpenAiEmbeddingModelBuilder dimensions(int dimensions){
        this.dimensions = dimensions;
        return this;
    }

    public OpenAiEmbeddingModelBuilder logRequests(boolean logRequests){
        this.logRequests = logRequests;
        return this;
    }

    public OpenAiEmbeddingModelBuilder logResponses(boolean logResponses){
        this.logResponses = logResponses;
        return this;
    }

    public EmbeddingModel build() {
        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(ofSeconds(timeout))
                .maxRetries(maxRetries)
                .dimensions(dimensions)
                .logRequests(logRequests)
                .logRequests(logResponses)
                .build();
    }
}
