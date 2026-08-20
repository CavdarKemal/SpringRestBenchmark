package de.springrest.benchmark.controller;

import de.springrest.benchmark.dto.BenchmarkResult;
import de.springrest.benchmark.dto.CategoryStat;
import de.springrest.benchmark.dto.MeasurementDto;
import de.springrest.benchmark.entity.Measurement;
import de.springrest.benchmark.service.ReadService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.cbor.CBORMapper;

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
    /** Eigener Mapper fuer das CBOR-Binaerformat (R7); Jackson 3 bringt Java-Time-Support eingebaut mit. */
    private final CBORMapper cborMapper = CBORMapper.builder().build();

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
     * <strong>R2 (Offset-Pagination).</strong> Eine Seite per {@code OFFSET/LIMIT}. Tiefe Seiten werden
     * zunehmend langsamer, weil die DB die uebersprungenen Zeilen erneut durchlaeuft.
     */
    @GetMapping("/r2/offset")
    public List<MeasurementDto> r2Offset(@RequestParam long offset, @RequestParam(defaultValue = "5000") int limit) {
        return readService.pageByOffset(offset, limit);
    }

    /**
     * <strong>R2 (Keyset-Pagination).</strong> Eine Seite per {@code WHERE id > afterId}. Nutzt den Index und
     * bleibt unabhaengig von der Seitentiefe konstant schnell.
     */
    @GetMapping("/r2/keyset")
    public List<MeasurementDto> r2Keyset(@RequestParam(defaultValue = "0") long afterId,
                                         @RequestParam(defaultValue = "5000") int limit) {
        return readService.pageByKeyset(afterId, limit);
    }

    /**
     * <strong>R3 — Server-seitiges Streaming (NDJSON).</strong> Streamt die Projektion zeilenweise ueber
     * einen DB-Cursor. Niedrige TTFB und konstanter Speicher (siehe {@code ReadService.streamNdjson}).
     */
    @GetMapping(value = "/r3", produces = "application/x-ndjson")
    public StreamingResponseBody r3Stream() {
        return readService::streamNdjson;
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

    /**
     * <strong>R5 — Caching.</strong> Teure Aggregat-Abfrage, deren Ergebnis gecacht wird. Erster Aufruf ist
     * „kalt" (DB), weitere sind „warm" (Cache). Mit {@link #r5Evict()} laesst sich der Cache zuruecksetzen.
     */
    @GetMapping("/r5")
    public List<CategoryStat> r5CategoryStats() {
        return readService.categoryStats();
    }

    /** Leert den R5-Cache (fuer einen erneuten „kalten" Lauf). */
    @PostMapping("/r5/evict")
    public void r5Evict() {
        readService.evictCategoryStats();
    }

    /**
     * <strong>R6 — Parallel-Queries.</strong> Fuehrt mehrere unabhaengige Abfragen aus — seriell oder parallel
     * (Virtual Threads). Liefert die reine Serverzeit im Envelope zurueck.
     */
    @GetMapping("/r6")
    public BenchmarkResult r6Dashboard(@RequestParam(defaultValue = "false") boolean parallel) {
        long start = System.nanoTime();
        int queries = readService.dashboard(parallel);
        double millis = (System.nanoTime() - start) / 1_000_000.0;
        String stage = parallel ? "r6-parallel" : "r6-sequential";
        return BenchmarkResult.of(stage, queries, millis, queries + " Abfragen " + (parallel ? "parallel" : "seriell"));
    }

    /**
     * <strong>R7 — Binaerformat (CBOR).</strong> Dieselben Projektionsdaten wie R1, aber als kompaktes
     * CBOR-Binaerformat statt JSON. Kleiner und schneller zu (de)serialisieren. Zeilenzahl kommt im Header
     * {@code X-Rows}, damit der Client das Binaerformat nicht selbst dekodieren muss.
     */
    @GetMapping(value = "/r7", produces = "application/cbor")
    public ResponseEntity<byte[]> r7Cbor() {
        List<MeasurementDto> data = readService.projection();
        byte[] cbor = cborMapper.writeValueAsBytes(data);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/cbor")
                .header("X-Wire-Bytes", String.valueOf(cbor.length))
                .header("X-Rows", String.valueOf(data.size()))
                .body(cbor);
    }

    /**
     * <strong>R8 — Reaktives Streaming (R2DBC).</strong> Gibt einen reaktiven {@code Flux} zurueck, den Spring
     * MVC streamend als NDJSON schreibt. Niedrige TTFB wie R3, aber vollstaendig nicht-blockierend.
     */
    @GetMapping(value = "/r8", produces = "application/x-ndjson")
    public Flux<MeasurementDto> r8Reactive() {
        return readService.streamReactive();
    }
}
