package com.schooldesk.docqa.embedding;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "docqa.ai.provider", havingValue = "stub", matchIfMissing = true)
public class StubEmbeddingClient implements EmbeddingClient {

    private static final int DIMS = 1536;

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> results = new ArrayList<>(texts.size());
        for (String text : texts) {
            results.add(deterministicVector(text));
        }
        return results;
    }

    @Override
    public int dimensions() {
        return DIMS;
    }

    private float[] deterministicVector(String text) {
        float[] vector = new float[DIMS];
        int hash = text.hashCode();
        for (int i = 0; i < DIMS; i++) {
            hash = hash * 1664525 + 1013904223;
            vector[i] = ((hash & 0x7FFFFFFF) / (float) Integer.MAX_VALUE) * 2 - 1;
        }
        return normalize(vector);
    }

    private float[] normalize(float[] v) {
        double norm = 0;
        for (float x : v) norm += (double) x * x;
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < v.length; i++) v[i] /= (float) norm;
        }
        return v;
    }
}
