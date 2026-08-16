package com.schooldesk.docqa.documents;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ChunkWriter {

    private final JdbcTemplate jdbc;

    ChunkWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertAll(List<ChunkInsert> chunks) {
        jdbc.batchUpdate("""
                INSERT INTO document_chunks
                  (id, document_id, tenant_id, chunk_index, page_number,
                   content, embedded_text, category, token_count, embedding)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS vector))
                """,
                chunks,
                chunks.size(),
                (ps, c) -> {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, c.documentId());
                    ps.setString(3, c.tenantId());
                    ps.setInt(4, c.chunkIndex());
                    if (c.pageNumber() == null) ps.setNull(5, java.sql.Types.INTEGER);
                    else ps.setInt(5, c.pageNumber());
                    ps.setString(6, c.content());
                    ps.setString(7, c.embeddedText());
                    ps.setString(8, c.category());
                    ps.setInt(9, c.tokenCount());
                    ps.setString(10, toVectorLiteral(c.embedding()));
                });
    }

    private static String toVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }

    public record ChunkInsert(
            UUID documentId, String tenantId, String category, int chunkIndex,
            Integer pageNumber, String content, String embeddedText,
            int tokenCount, float[] embedding) {
    }
}
