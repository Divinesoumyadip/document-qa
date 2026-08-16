package com.schooldesk.docqa.embedding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
            results.add(bagOfWordsVector(text));
        }
        return results;
    }

    @Override
    public int dimensions() {
        return DIMS;
    }

    private float[] bagOfWordsVector(String text) {
        float[] vector = new float[DIMS];
        for (String token : tokenize(text)) {
            int bucket = Math.floorMod(token.hashCode(), DIMS);
            vector[bucket] += 1.0f;
        }
        return normalize(vector);
    }

    private String[] tokenize(String text) {
        return text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
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
