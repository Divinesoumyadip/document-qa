package com.schooldesk.docqa.embedding;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "docqa.ai.provider", havingValue = "openai")
public class OpenAiEmbeddingClient implements EmbeddingClient {

    private static final int BATCH_SIZE = 512;
    private static final int DIMS = 1536;
    private static final String MODEL = "text-embedding-3-small";

    private final EmbeddingModel embeddingModel;

    OpenAiEmbeddingClient(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> all = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += BATCH_SIZE) {
            List<String> batch = texts.subList(i, Math.min(i + BATCH_SIZE, texts.size()));
            EmbeddingRequest request = new EmbeddingRequest(batch,
                    OpenAiEmbeddingOptions.builder().model(MODEL).build());
            embeddingModel.call(request).getResults()
                    .forEach(r -> all.add(r.getOutput()));
        }
        return all;
    }

    @Override
    public int dimensions() {
        return DIMS;
    }
}
