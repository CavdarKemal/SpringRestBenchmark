package de.springrest.benchmark.controller;

import de.springrest.benchmark.dto.BenchmarkResult;
import de.springrest.benchmark.service.DataGeneratorService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Verwaltungs-Endpoints fuer den Testdatenbestand.
 *
 * <p>Diese Endpoints gehoeren zum Mess-Harness, nicht zu den Benchmark-Stufen. Sie
 * erlauben es, vor einem Lauf einen definierten Datenbestand herzustellen (Seeding),
 * die Groesse abzufragen und die Tabelle zu leeren.</p>
 *
 * <ul>
 *   <li>{@code POST /api/data/generate?rows=1000000&clear=true&payloadLength=64}</li>
 *   <li>{@code GET  /api/data/count}</li>
 *   <li>{@code DELETE /api/data}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/data")
public class DataController {

    private final DataGeneratorService generator;

    public DataController(DataGeneratorService generator) {
        this.generator = generator;
    }

    /**
     * Erzeugt Testdaten und liefert die Seeding-Kennzahlen zurueck.
     *
     * @param rows          Anzahl Zeilen (Default 100000)
     * @param clear         Tabelle vorher leeren (Default true)
     * @param payloadLength Laenge des Text-Payloads je Zeile (Default 0 = kein Payload)
     */
    @PostMapping("/generate")
    public BenchmarkResult generate(
            @RequestParam(defaultValue = "100000") long rows,
            @RequestParam(defaultValue = "true") boolean clear,
            @RequestParam(defaultValue = "0") int payloadLength) {

        long start = System.nanoTime();
        long inserted = generator.generate(rows, clear, payloadLength);
        double millis = (System.nanoTime() - start) / 1_000_000.0;

        return BenchmarkResult.of("seed", inserted, millis,
                "Seeding via JDBC-Batch (payloadLength=" + payloadLength + ")");
    }

    /** Liefert die aktuelle Zeilenanzahl der Tabelle. */
    @GetMapping("/count")
    public Map<String, Long> count() {
        return Map.of("count", generator.count());
    }

    /** Leert die Tabelle vollstaendig. */
    @DeleteMapping
    public Map<String, String> clear() {
        generator.clear();
        return Map.of("status", "cleared");
    }
}
