package com.schooldesk.docqa.retrieval;

import java.util.List;
import java.util.UUID;

import com.schooldesk.docqa.chunking.ChunkingProperties;
import com.schooldesk.docqa.embedding.EmbeddingClient;
import com.schooldesk.docqa.observability.AiMetrics;
import com.schooldesk.docqa.tenancy.TenantContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private final JdbcTemplate jdbc;
    private final EmbeddingClient embeddingClient;
    private final ChunkingProperties properties;
    private final AiMetrics metrics;

    RetrievalService(JdbcTemplate jdbc, EmbeddingClient embeddingClient,
            ChunkingProperties properties, AiMetrics metrics) {
        this.jdbc = jdbc;
        this.embeddingClient = embeddingClient;
        this.properties = properties;
        this.metrics = metrics;
    }

    public List<RetrievedChunk> retrieve(String question, String category, Integer topK) {
        String tenantId = TenantContext.require();
        int limit = topK != null ? topK : properties.defaultTopK();

        float[] queryVector = embeddingClient.embed(List.of(question)).get(0);
        String literal = toVectorLiteral(queryVector);

        long start = System.currentTimeMillis();
        List<RetrievedChunk> results = search(tenantId, category, literal, limit);
        long elapsed = System.currentTimeMillis() - start;

        List<RetrievedChunk> aboveThreshold = results.stream()
                .filter(c -> c.similarity() >= properties.similarityThreshold())
                .toList();

        metrics.recordRetrieval(elapsed, results.size(), aboveThreshold.size());
        if (aboveThreshold.isEmpty()) {
            metrics.recordRefusal();
        }

        log.info("Retrieval tenantId={} category={} candidates={} aboveThreshold={} latencyMs={}",
                tenantId, category, results.size(), aboveThreshold.size(), elapsed);

        return aboveThreshold;
    }

    private List<RetrievedChunk> search(String tenantId, String category, String vector, int limit) {
        String sql = """
                SELECT c.id, c.document_id, d.title, c.page_number, c.content,
                       1 - (c.embedding <=> CAST(? AS vector)) AS similarity
                FROM document_chunks c
                JOIN documents d ON d.id = c.document_id
                WHERE c.tenant_id = ?
                  AND (?::varchar IS NULL OR c.category = ?)
                ORDER BY c.embedding <=> CAST(? AS vector)
                LIMIT ?
                """;

        return jdbc.query(sql,
                ps -> {
                    ps.setString(1, vector);
                    ps.setString(2, tenantId);
                    ps.setString(3, category);
                    ps.setString(4, category);
                    ps.setString(5, vector);
                    ps.setInt(6, limit);
                },
                (rs, rowNum) -> new RetrievedChunk(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("document_id")),
                        rs.getString("title"),
                        rs.getObject("page_number") == null ? null : rs.getInt("page_number"),
                        rs.getString("content"),
                        rs.getDouble("similarity")));
    }

    private String toVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}
