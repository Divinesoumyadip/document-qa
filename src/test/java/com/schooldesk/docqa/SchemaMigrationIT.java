package com.schooldesk.docqa;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SchemaMigrationIT extends AbstractPostgresIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void appliesEveryMigrationOnAFreshDatabase() {
        Integer failed = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = false", Integer.class);

        assertThat(failed).isZero();
    }

    @Test
    void enablesThePgvectorExtension() {
        Integer installed = jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'vector'", Integer.class);

        assertThat(installed).isEqualTo(1);
    }

    @Test
    void createsAnHnswIndexWithTheCosineOperatorClass() {
        String definition = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = 'document_chunks_embedding_idx'",
                String.class);

        assertThat(definition)
                .contains("USING hnsw")
                .contains("vector_cosine_ops");
    }

    @Test
    void rejectsASecondDocumentWithTheSameContentHashInOneTenant() {
        insertDocument("tenant-a");

        assertThat(canInsertDocumentFor("tenant-a")).isFalse();

        assertThat(canInsertDocumentFor("tenant-b")).isTrue();
    }

    private void insertDocument(String tenantId) {
        jdbc.update("""
                INSERT INTO documents (id, tenant_id, title, filename, content_hash, size_bytes, status)
                VALUES (gen_random_uuid(), ?, 'Fee Policy', 'fees.pdf', repeat('a', 64), 100, 'READY')
                """, tenantId);
    }

    private boolean canInsertDocumentFor(String tenantId) {
        try {
            insertDocument(tenantId);
            return true;
        }
        catch (DuplicateKeyException expected) {
            return false;
        }
    }
}
