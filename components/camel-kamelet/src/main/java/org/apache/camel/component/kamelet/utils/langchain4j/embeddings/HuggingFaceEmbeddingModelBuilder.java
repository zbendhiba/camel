package org.apache.camel.component.kamelet.utils.langchain4j.embeddings;


import dev.langchain4j.model.huggingface.HuggingFaceEmbeddingModel;

import static java.time.Duration.ofSeconds;

public final class HuggingFaceEmbeddingModelBuilder {
    private String accessToken;
    private String modelId;
    private boolean waitForModel;
    private int timeout;

    public HuggingFaceEmbeddingModelBuilder accessToken(String accessToken) {
        this.accessToken = accessToken;
        return this;
    }

    public HuggingFaceEmbeddingModelBuilder modelId(String modelId) {
        this.modelId = modelId;
        return this;
    }

    public HuggingFaceEmbeddingModelBuilder timeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    public HuggingFaceEmbeddingModelBuilder waitForModel(boolean waitForModel) {
        this.waitForModel = waitForModel;
        return this;
    }
    public HuggingFaceEmbeddingModel build() {
        return HuggingFaceEmbeddingModel.builder()
                .accessToken(accessToken)
                .modelId(modelId)
                .waitForModel(waitForModel)
                .timeout(ofSeconds(timeout))
                .build();
    }

}
