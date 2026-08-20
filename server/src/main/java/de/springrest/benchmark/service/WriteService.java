package de.springrest.benchmark.service;

import de.springrest.benchmark.dto.MeasurementRequest;
import de.springrest.benchmark.entity.Measurement;
import de.springrest.benchmark.repository.MeasurementRepository;
import org.springframework.stereotype.Service;

/**
 * Geschaeftslogik der schreibenden Benchmark-Stufen (W0..W8).
 *
 * <p>Jede Methode entspricht einer Optimierungsstufe. Sie sind bewusst getrennt, damit
 * der Unterschied zwischen den Techniken im Code sichtbar bleibt.</p>
 */
@Service
public class WriteService {

    private final MeasurementRepository repository;

    public WriteService(MeasurementRepository repository) {
        this.repository = repository;
    }

    /**
     * <strong>W0 — Baseline.</strong> Speichert genau eine Zeile ueber
     * {@code repository.save()}.
     *
     * <p>Bewusst naiv: Es gibt keine explizite Transaktionsklammer, also committet jeder
     * Aufruf einzeln (ein INSERT + ein Commit pro Zeile). In Kombination mit einem
     * HTTP-Request pro Zeile auf der Client-Seite entsteht so der maximale Overhead —
     * die Referenz, gegen die alle weiteren Write-Stufen antreten.</p>
     *
     * @param request die zu speichernde Messung
     * @return die vergebene id
     */
    public Long saveOne(MeasurementRequest request) {
        Measurement saved = repository.save(request.toEntity());
        return saved.getId();
    }
}
