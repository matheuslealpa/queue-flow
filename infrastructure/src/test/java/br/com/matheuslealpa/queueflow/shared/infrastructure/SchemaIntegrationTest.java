package br.com.matheuslealpa.queueflow.shared.infrastructure;

import br.com.matheuslealpa.queueflow.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaIntegrationTest extends PostgresIntegrationTest {

    @Autowired JdbcClient jdbc;

    @Test
    void tablesLiveInDedicatedSchemaAndNotInPublic() {
        var schemas = jdbc.sql("""
                        SELECT DISTINCT table_schema FROM information_schema.tables
                        WHERE table_name IN ('queue', 'queue_entry', 'flyway_schema_history')
                        """)
                .query(String.class)
                .list();
        var publicTables = jdbc.sql("SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'")
                .query(Long.class)
                .single();

        assertThat(schemas).containsExactly("queueflow");
        assertThat(publicTables).isZero();
        assertThat(jdbc.sql("SELECT current_schema()").query(String.class).single()).isEqualTo("queueflow");
    }
}
