package de.springrest.benchmark.controller;

import de.springrest.benchmark.entity.Measurement;
import de.springrest.benchmark.service.ReadService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST-Endpoints der lesenden Benchmark-Stufen (R0..R8).
 *
 * <p>Jede Stufe bekommt einen eigenen Pfad unterhalb von {@code /api/read}, damit der
 * Client sie einzeln ansteuern und messen kann.</p>
 */
@RestController
@RequestMapping("/api/read")
public class ReadController {

    private final ReadService readService;

    public ReadController(ReadService readService) {
        this.readService = readService;
    }

    /**
     * <strong>R0 — Baseline.</strong> Liefert die komplette Tabelle als JSON-Array
     * vollstaendiger Entities.
     *
     * <p>Achtung: Es werden bewusst die <em>Entities selbst</em> serialisiert (nicht ein
     * schlankes DTO). Das ist ein Anti-Pattern und zugleich der Ausgangspunkt: Stufe R1
     * fuehrt die DTO-Projektion ein und zeigt, wie viel Nutzlast sich allein dadurch
     * einsparen laesst.</p>
     */
    @GetMapping("/r0")
    public List<Measurement> r0FindAll() {
        return readService.findAllNaive();
    }
}
