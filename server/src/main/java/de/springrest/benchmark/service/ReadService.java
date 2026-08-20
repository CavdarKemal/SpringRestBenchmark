package de.springrest.benchmark.service;

import de.springrest.benchmark.entity.Measurement;
import de.springrest.benchmark.repository.MeasurementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Geschaeftslogik der lesenden Benchmark-Stufen (R0..R8).
 *
 * <p>Jede Methode entspricht einer Optimierungsstufe fuer den Weg DB -> Server -> Client.</p>
 */
@Service
public class ReadService {

    private final MeasurementRepository repository;

    public ReadService(MeasurementRepository repository) {
        this.repository = repository;
    }

    /**
     * <strong>R0 — Baseline.</strong> Laedt die <em>komplette</em> Tabelle als
     * Entity-Liste in den Speicher.
     *
     * <p>Bewusst naiv und gleich mehrfach ineffizient:</p>
     * <ul>
     *   <li>Es werden <em>alle</em> Zeilen auf einmal geladen — der Speicherbedarf
     *       waechst linear mit der Tabellengroesse.</li>
     *   <li>Es werden <em>vollstaendige</em> Entities materialisiert (alle Spalten,
     *       inkl. payload), auch wenn der Client nur wenige Felder braucht.</li>
     *   <li>Anschliessend wird die gesamte Liste als ein grosses JSON-Array serialisiert;
     *       das erste Byte erreicht den Client erst, wenn alles fertig ist (hohe TTFB).</li>
     * </ul>
     *
     * <p>Genau diese drei Schwaechen greifen die Stufen R1 (Projektion), R2 (Pagination)
     * und R3 (Streaming) nacheinander an.</p>
     *
     * @return alle Messungen als Entity-Liste
     */
    @Transactional(readOnly = true)
    public List<Measurement> findAllNaive() {
        return repository.findAll();
    }
}
