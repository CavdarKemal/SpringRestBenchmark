package de.springrest.benchmark.controller;

import de.springrest.benchmark.AbstractPostgresIT;
import de.springrest.benchmark.dto.CategoryStat;
import de.springrest.benchmark.service.DataGeneratorService;
import de.springrest.benchmark.service.ReadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integrationstest fuer die Read-Stufen R1 (Projektion) und R4 (Kompression).
 */
@AutoConfigureMockMvc
class ReadStagesIntegrationTest extends AbstractPostgresIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    DataGeneratorService generator;

    @Autowired
    ReadService readService;

    @BeforeEach
    void seed() {
        // Mit Payload seeden, damit der Unterschied R0/R1 und die Kompression sichtbar werden.
        generator.generate(200, true, 64);
    }

    @Test
    @DisplayName("R1 liefert alle Zeilen als schlanke Projektion (id, ts, sensorId, category, v1)")
    void r1ReturnsProjection() throws Exception {
        mockMvc.perform(get("/api/read/r1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(200))
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].category").exists())
                .andExpect(jsonPath("$[0].v1").exists())
                // Projektion: v2 und payload sind NICHT enthalten.
                .andExpect(jsonPath("$[0].v2").doesNotExist())
                .andExpect(jsonPath("$[0].payload").doesNotExist());
    }

    @Test
    @DisplayName("R2 Offset und Keyset liefern konsistente Seiten")
    void r2Pagination() throws Exception {
        // Erste Seite (Offset): 10 Zeilen, aufsteigend nach id.
        mockMvc.perform(get("/api/read/r2/offset").param("offset", "0").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10));

        // Letzte Seite (Offset 195, limit 10) bei 200 Zeilen -> 5 Zeilen.
        mockMvc.perform(get("/api/read/r2/offset").param("offset", "195").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));

        // Keyset ab id 0: 10 Zeilen.
        mockMvc.perform(get("/api/read/r2/keyset").param("afterId", "0").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10));
    }

    @Test
    @DisplayName("R3 streamt alle Zeilen als NDJSON (eine Zeile pro Datensatz)")
    void r3StreamsNdjson() throws Exception {
        MvcResult started = mockMvc.perform(get("/api/read/r3"))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        long lines = body.lines().filter(line -> !line.isBlank()).count();
        assertThat(lines).isEqualTo(200);
    }

    @Test
    @DisplayName("R5 cacht das Aggregat: bleibt trotz neuer Daten stabil, bis der Cache geleert wird")
    void r5CachesUntilEvict() {
        readService.evictCategoryStats();
        generator.generate(200, true, 0);
        assertThat(totalCount(readService.categoryStats())).isEqualTo(200);

        // Datenbestand aendern OHNE Evict -> R5 liefert weiter den gecachten (alten) Stand.
        generator.generate(50, true, 0);
        assertThat(totalCount(readService.categoryStats())).isEqualTo(200);

        // Nach Evict wird wieder frisch aus der DB gelesen.
        readService.evictCategoryStats();
        assertThat(totalCount(readService.categoryStats())).isEqualTo(50);
    }

    @Test
    @DisplayName("R6 fuehrt seriell und parallel jeweils 8 Abfragen aus")
    void r6RunsQueries() throws Exception {
        mockMvc.perform(get("/api/read/r6").param("parallel", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("r6-sequential"))
                .andExpect(jsonPath("$.rowsProcessed").value(8));

        mockMvc.perform(get("/api/read/r6").param("parallel", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("r6-parallel"))
                .andExpect(jsonPath("$.rowsProcessed").value(8));
    }

    @Test
    @DisplayName("R7 (CBOR) liefert alle Zeilen und weniger Bytes als R1 (JSON)")
    void r7CborIsSmallerThanJson() throws Exception {
        MvcResult r7 = mockMvc.perform(get("/api/read/r7"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Rows", "200"))
                .andReturn();
        int cborBytes = r7.getResponse().getContentAsByteArray().length;

        int jsonBytes = mockMvc.perform(get("/api/read/r1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray().length;

        assertThat(cborBytes).isLessThan(jsonBytes);
    }

    @Test
    @DisplayName("R8 streamt alle Zeilen reaktiv (R2DBC) als NDJSON")
    void r8StreamsReactive() throws Exception {
        MvcResult started = mockMvc.perform(get("/api/read/r8"))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        long lines = body.lines().filter(line -> !line.isBlank()).count();
        assertThat(lines).isEqualTo(200);
    }

    private static long totalCount(java.util.List<CategoryStat> stats) {
        return stats.stream().mapToLong(CategoryStat::count).sum();
    }

    @Test
    @DisplayName("R4 liefert gzip-komprimiert und deutlich weniger Bytes als R1")
    void r4IsCompressedAndSmaller() throws Exception {
        int r1Bytes = mockMvc.perform(get("/api/read/r1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray().length;

        MvcResult r4 = mockMvc.perform(get("/api/read/r4"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Encoding", "gzip"))
                .andExpect(header().exists("X-Wire-Bytes"))
                .andReturn();

        int r4WireBytes = Integer.parseInt(r4.getResponse().getHeader("X-Wire-Bytes"));
        // Der komprimierte Body muss klar kleiner sein als das unkomprimierte R1-JSON.
        assertThat(r4WireBytes).isLessThan(r1Bytes / 2);
    }
}
