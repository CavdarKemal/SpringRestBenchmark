package de.springrest.benchmark.controller;

import de.springrest.benchmark.AbstractPostgresIT;
import de.springrest.benchmark.service.DataGeneratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
