package de.springrest.benchmark.controller;

import de.springrest.benchmark.dto.MeasurementRequest;
import de.springrest.benchmark.service.WriteService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST-Endpoints der schreibenden Benchmark-Stufen (W0..W8).
 *
 * <p>Jede Stufe bekommt einen eigenen Pfad unterhalb von {@code /api/write}, damit der
 * Client sie einzeln ansteuern und messen kann.</p>
 */
@RestController
@RequestMapping("/api/write")
public class WriteController {

    private final WriteService writeService;

    public WriteController(WriteService writeService) {
        this.writeService = writeService;
    }

    /**
     * <strong>W0 — Baseline.</strong> Nimmt genau eine Zeile entgegen und speichert sie.
     *
     * <p>Der Client ruft diesen Endpoint in einer Schleife auf — ein HTTP-Request je
     * Zeile. Der Durchsatz dieser Stufe wird daher clientseitig ueber die Gesamtzeit
     * aller Einzelaufrufe gemessen.</p>
     */
    @PostMapping("/w0")
    public Map<String, Long> w0SingleRow(@RequestBody MeasurementRequest request) {
        Long id = writeService.saveOne(request);
        return Map.of("id", id);
    }
}
