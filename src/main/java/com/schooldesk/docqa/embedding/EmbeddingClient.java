package com.schooldesk.docqa.embedding;

import java.util.List;

public interface EmbeddingClient {

    List<float[]> embed(List<String> texts);

    int dimensions();
}
