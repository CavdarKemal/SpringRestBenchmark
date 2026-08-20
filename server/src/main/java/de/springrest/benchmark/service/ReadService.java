package de.springrest.benchmark.service;

import de.springrest.benchmark.dto.MeasurementDto;
import de.springrest.benchmark.entity.Measurement;
import de.springrest.benchmark.repository.MeasurementRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Geschaeftslogik der lesenden Benchmark-Stufen (R0..R8).
 *
 * <p>Jede Methode entspricht einer Optimierungsstufe fuer den Weg DB -> Server -> Client.</p>
 */
@Service
public class ReadService {

    /** Projektion: nur die wirklich benoetigten Spalten (kein v2..v8, kein payload). */
    private static final String PROJECTION_SQL =
            "SELECT id, ts, sensor_id, category, v1 FROM measurements ORDER BY id";

    private final MeasurementRepository repository;
    private final JdbcTemplate jdbcTemplate;

    public ReadService(MeasurementRepository repository, JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * <strong>R0 — Baseline.</strong> Laedt die <em>komplette</em> Tabelle als Entity-Liste in den Speicher
     * und serialisiert vollstaendige Entities. Bewusst mehrfach ineffizient (alles in RAM, alle Spalten,
     * erst fertig dann senden) — Ausgangspunkt fuer R1 (Projektion), R2 (Pagination), R3 (Streaming).
     */
    @Transactional(readOnly = true)
    public List<Measurement> findAllNaive() {
        return repository.findAll();
    }

    /**
     * <strong>R1 — DTO-Projektion.</strong> Selektiert nur die benoetigten Spalten und bildet sie auf ein
     * schlankes {@link MeasurementDto} ab. Gegenueber R0 entfallen {@code v2..v8} und das {@code payload} —
     * die Nutzlast wird dadurch deutlich kleiner. Zudem werden keine JPA-Entities materialisiert.
     */
    @Transactional(readOnly = true)
    public List<MeasurementDto> projection() {
        return jdbcTemplate.query(PROJECTION_SQL, (rs, rowNum) -> new MeasurementDto(
                rs.getLong("id"),
                rs.getObject("ts", OffsetDateTime.class),
                rs.getInt("sensor_id"),
                rs.getString("category"),
                rs.getDouble("v1")));
    }
}
