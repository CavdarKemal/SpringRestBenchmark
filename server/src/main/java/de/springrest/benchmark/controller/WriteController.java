package de.springrest.benchmark.controller;

import de.springrest.benchmark.dto.BenchmarkResult;
import de.springrest.benchmark.dto.MeasurementRequest;
import de.springrest.benchmark.service.WriteService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST-Endpoints der schreibenden Benchmark-Stufen (W0..W8).
 *
 * <p>Jede Stufe bekommt einen eigenen Pfad unterhalb von {@code /api/write}, damit der Client sie einzeln
 * ansteuern und messen kann. Die Bulk-Stufen (ab W1) leeren die Tabelle vor dem Lauf (nicht Teil der gemessenen
 * Zeit) und liefern die reine Insert-Zeit im {@link BenchmarkResult}-Envelope zurueck.</p>
 */
@RestController
@RequestMapping("/api/write")
public class WriteController {

    private final WriteService writeService;

    public WriteController(WriteService writeService) {
        this.writeService = writeService;
    }

    /**
     * <strong>W0 — Baseline.</strong> Nimmt genau eine Zeile entgegen und speichert sie. Der Client ruft diesen
     * Endpoint in einer Schleife auf — ein HTTP-Request je Zeile.
     */
    @PostMapping("/w0")
    public Map<String, Long> w0SingleRow(@RequestBody MeasurementRequest request) {
        return Map.of("id", writeService.saveOne(request));
    }

    /**
     * <strong>W1 — Bulk-Payload, Einzel-INSERT, Autocommit.</strong> Alle Zeilen in einem Request; der Server
     * speichert sie einzeln mit je eigenem Commit.
     */
    @PostMapping("/w1")
    public BenchmarkResult w1(@RequestBody List<MeasurementRequest> rows) {
        writeService.truncate();
        long start = System.nanoTime();
        int count = writeService.w1BulkAutocommit(rows);
        double millis = elapsedMillis(start);
        return BenchmarkResult.of("w1-bulk-autocommit", count, millis, "1 HTTP-Request, aber " + count + " Einzel-Commits");
    }

    /**
     * <strong>W2 — Alles in EINER Transaktion.</strong> Gleiche Einzel-INSERTs wie W1, aber ein gemeinsamer Commit.
     */
    @PostMapping("/w2")
    public BenchmarkResult w2(@RequestBody List<MeasurementRequest> rows) {
        writeService.truncate();
        long start = System.nanoTime();
        int count = writeService.w2SingleTransaction(rows);
        double millis = elapsedMillis(start);
        return BenchmarkResult.of("w2-single-transaction", count, millis, count + " Zeilen in einer Transaktion (1 Commit)");
    }

    /**
     * <strong>W3 — JDBC-Batch-INSERT.</strong> Zeilen werden in Chunks als JDBC-Batch geschickt (weniger Round-Trips).
     */
    @PostMapping("/w3")
    public BenchmarkResult w3(@RequestBody List<MeasurementRequest> rows) {
        writeService.truncate();
        long start = System.nanoTime();
        int count = writeService.w3JdbcBatch(rows);
        double millis = elapsedMillis(start);
        return BenchmarkResult.of("w3-jdbc-batch", count, millis, count + " Zeilen als JDBC-Batch (Chunk 1000)");
    }

    /**
     * <strong>W4 — JDBC-Batch mit {@code reWriteBatchedInserts=true}.</strong> Wie W3, aber pgjdbc buendelt den
     * Batch in Multi-Row-INSERTs um.
     */
    @PostMapping("/w4")
    public BenchmarkResult w4(@RequestBody List<MeasurementRequest> rows) {
        writeService.truncate();
        long start = System.nanoTime();
        int count = writeService.w4JdbcBatchRewrite(rows);
        double millis = elapsedMillis(start);
        return BenchmarkResult.of("w4-jdbc-batch-rewrite", count, millis, count + " Zeilen, reWriteBatchedInserts=true");
    }

    /**
     * <strong>W6 — Postgres COPY.</strong> Nativer Bulk-Load ueber den pgjdbc-CopyManager — der schnellste Weg.
     */
    @PostMapping("/w6")
    public BenchmarkResult w6(@RequestBody List<MeasurementRequest> rows) {
        writeService.truncate();
        long start = System.nanoTime();
        int count = writeService.w6Copy(rows);
        double millis = elapsedMillis(start);
        return BenchmarkResult.of("w6-copy", count, millis, count + " Zeilen via Postgres COPY (CSV)");
    }

    private static double elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0;
    }
}
