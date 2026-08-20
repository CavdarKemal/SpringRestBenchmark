package de.springrest.benchmark.controller;

import de.springrest.benchmark.AbstractPostgresIT;
import de.springrest.benchmark.service.DataGeneratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-End-Integrationstest fuer die Baseline-Endpoints W0 (Write) und R0 (Read)
 * ueber die volle HTTP-Schicht (MockMvc) gegen ein echtes PostgreSQL.
 */
@AutoConfigureMockMvc
class WriteReadFlowIntegrationTest extends AbstractPostgresIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    DataGeneratorService generator;

    @BeforeEach
    void cleanSlate() {
        generator.clear();
    }

    @Test
    @DisplayName("W0: ein POST speichert genau eine Zeile und liefert eine id")
    void w0StoresSingleRow() throws Exception {
        String body = """
                {"ts":null,"sensorId":7,"category":"TEMP",
                 "v1":1,"v2":2,"v3":3,"v4":4,"v5":5,"v6":6,"v7":7,"v8":8,"payload":null}
                """;

        mockMvc.perform(post("/api/write/w0").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber());

        assertThat(generator.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("R0: liefert alle Zeilen als JSON-Array")
    void r0ReturnsAllRows() throws Exception {
        generator.generate(50, true, 8);

        mockMvc.perform(get("/api/read/r0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(50));
    }

    @Test
    @DisplayName("Jede Antwort traegt den Server-Timing-Header (Mess-Harness)")
    void responsesCarryServerTimingHeader() throws Exception {
        mockMvc.perform(get("/api/data/count"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Server-Timing"));
    }
}
