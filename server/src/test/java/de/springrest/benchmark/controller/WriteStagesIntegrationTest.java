package de.springrest.benchmark.controller;

import de.springrest.benchmark.AbstractPostgresIT;
import de.springrest.benchmark.service.DataGeneratorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integrationstest fuer die Bulk-Write-Stufen W1 und W2 gegen ein echtes PostgreSQL.
 *
 * <p>Beide Stufen muessen dieselbe Anzahl Zeilen korrekt persistieren — der Unterschied
 * liegt nur in der Technik (Autocommit pro Zeile vs. eine Transaktion), nicht im
 * Ergebnis. Genau das wird hier abgesichert.</p>
 */
@AutoConfigureMockMvc
class WriteStagesIntegrationTest extends AbstractPostgresIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    DataGeneratorService generator;

    @Autowired
    JdbcTemplate jdbcTemplate;

    /** Baut einen JSON-Array-Body mit {@code n} Zeilen. */
    private static String bulkBody(int n) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(',');
            sb.append("""
                    {"ts":null,"sensorId":%d,"category":"TEMP",
                     "v1":1,"v2":2,"v3":3,"v4":4,"v5":5,"v6":6,"v7":7,"v8":8,"payload":null}
                    """.formatted(i));
        }
        return sb.append(']').toString();
    }

    @Test
    @DisplayName("W1 persistiert alle Zeilen und meldet die Anzahl im Envelope")
    void w1PersistsAllRows() throws Exception {
        mockMvc.perform(post("/api/write/w1").contentType(MediaType.APPLICATION_JSON).content(bulkBody(25)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("w1-bulk-autocommit"))
                .andExpect(jsonPath("$.rowsProcessed").value(25));

        assertThat(generator.count()).isEqualTo(25);
    }

    @Test
    @DisplayName("W2 persistiert alle Zeilen in einer Transaktion")
    void w2PersistsAllRows() throws Exception {
        mockMvc.perform(post("/api/write/w2").contentType(MediaType.APPLICATION_JSON).content(bulkBody(25)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("w2-single-transaction"))
                .andExpect(jsonPath("$.rowsProcessed").value(25));

        assertThat(generator.count()).isEqualTo(25);
    }

    @Test
    @DisplayName("W3 persistiert alle Zeilen per JDBC-Batch")
    void w3PersistsAllRows() throws Exception {
        mockMvc.perform(post("/api/write/w3").contentType(MediaType.APPLICATION_JSON).content(bulkBody(25)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("w3-jdbc-batch"))
                .andExpect(jsonPath("$.rowsProcessed").value(25));

        assertThat(generator.count()).isEqualTo(25);
    }

    @Test
    @DisplayName("W4 persistiert alle Zeilen per Batch mit reWriteBatchedInserts")
    void w4PersistsAllRows() throws Exception {
        // Mehr als BATCH_SIZE (1000) erzwingt mehrere Chunks -> prueft die Chunk-Logik.
        mockMvc.perform(post("/api/write/w4").contentType(MediaType.APPLICATION_JSON).content(bulkBody(1500)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("w4-jdbc-batch-rewrite"))
                .andExpect(jsonPath("$.rowsProcessed").value(1500));

        assertThat(generator.count()).isEqualTo(1500);
    }

    @Test
    @DisplayName("W5 importiert alle Zeilen per Spring-Batch-Job (mehrere Chunks)")
    void w5PersistsAllRows() throws Exception {
        // 1500 > BATCH_SIZE (1000) -> zwei Chunks, prueft den chunk-orientierten Ablauf.
        mockMvc.perform(post("/api/write/w5").contentType(MediaType.APPLICATION_JSON).content(bulkBody(1500)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("w5-spring-batch"))
                .andExpect(jsonPath("$.rowsProcessed").value(1500));

        assertThat(generator.count()).isEqualTo(1500);
    }

    @Test
    @DisplayName("W6 laedt alle Zeilen per Postgres COPY")
    void w6PersistsAllRows() throws Exception {
        mockMvc.perform(post("/api/write/w6").contentType(MediaType.APPLICATION_JSON).content(bulkBody(1500)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("w6-copy"))
                .andExpect(jsonPath("$.rowsProcessed").value(1500));

        assertThat(generator.count()).isEqualTo(1500);
    }

    @Test
    @DisplayName("W6 setzt fehlendes payload korrekt auf NULL (CSV-Leerfeld)")
    void w6NullPayloadBecomesNull() throws Exception {
        String body = """
                [{"ts":null,"sensorId":1,"category":"TEMP",
                  "v1":1,"v2":2,"v3":3,"v4":4,"v5":5,"v6":6,"v7":7,"v8":8,"payload":null}]
                """;
        mockMvc.perform(post("/api/write/w6").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        Long nullCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM measurements WHERE payload IS NULL", Long.class);
        assertThat(nullCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Jede Bulk-Stufe leert die Tabelle vorher (fairer Start)")
    void bulkStagesTruncateFirst() throws Exception {
        generator.generate(100, true, 0);
        assertThat(generator.count()).isEqualTo(100);

        // W2 mit 10 Zeilen -> danach genau 10 (nicht 110), weil vorher truncatet wird.
        mockMvc.perform(post("/api/write/w2").contentType(MediaType.APPLICATION_JSON).content(bulkBody(10)))
                .andExpect(status().isOk());

        assertThat(generator.count()).isEqualTo(10);
    }
}
