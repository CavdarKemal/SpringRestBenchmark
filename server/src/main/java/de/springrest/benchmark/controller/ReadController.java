package de.springrest.benchmark.controller;

import de.springrest.benchmark.dto.MeasurementDto;
import de.springrest.benchmark.entity.Measurement;
import de.springrest.benchmark.service.ReadService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * REST-Endpoints der lesenden Benchmark-Stufen (R0..R8).
 *
 * <p>Jede Stufe bekommt einen eigenen Pfad unterhalb von {@code /api/read}, damit der Client sie einzeln
 * ansteuern und messen kann.</p>
 */
@RestController
@RequestMapping("/api/read")
public class ReadController {

    private final ReadService readService;
    private final ObjectMapper objectMapper;

    public ReadController(ReadService readService, ObjectMapper objectMapper) {
        this.readService = readService;
        this.objectMapper = objectMapper;
    }

    /**
     * <strong>R0 — Baseline.</strong> Komplette Tabelle als JSON-Array vollstaendiger Entities.
     */
    @GetMapping("/r0")
    public List<Measurement> r0FindAll() {
        return readService.findAllNaive();
    }

    /**
     * <strong>R1 — DTO-Projektion.</strong> Nur die benoetigten Spalten als schlankes DTO-Array.
     */
    @GetMapping("/r1")
    public List<MeasurementDto> r1Projection() {
        return readService.projection();
    }

    /**
     * <strong>R4 — HTTP-Kompression.</strong> Gleiche Projektionsdaten wie R1, aber gzip-komprimiert.
     *
     * <p>Wir komprimieren hier bewusst manuell (statt globaler Servlet-Kompression), damit R1 unkomprimiert
     * und R4 komprimiert direkt vergleichbar sind. Der Browser dekomprimiert automatisch (Content-Encoding:
     * gzip); die tatsaechliche Leitungsgroesse liefern wir zusaetzlich im Header {@code X-Wire-Bytes}, damit
     * der Client die Einsparung anzeigen kann.</p>
     */
    @GetMapping("/r4")
    public ResponseEntity<byte[]> r4Compressed() throws IOException {
        byte[] json = objectMapper.writeValueAsBytes(readService.projection());
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(json.length / 4);
        try (GZIPOutputStream gzip = new GZIPOutputStream(buffer)) {
            gzip.write(json);
        }
        byte[] compressed = buffer.toByteArray();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_ENCODING, "gzip")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("X-Wire-Bytes", String.valueOf(compressed.length))
                .body(compressed);
    }
}
