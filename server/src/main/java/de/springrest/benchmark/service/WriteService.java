package de.springrest.benchmark.service;

import de.springrest.benchmark.dto.MeasurementRequest;
import de.springrest.benchmark.entity.Measurement;
import de.springrest.benchmark.repository.MeasurementRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Geschaeftslogik der schreibenden Benchmark-Stufen (W0..W8).
 *
 * <p>Jede Methode entspricht einer Optimierungsstufe. Sie sind bewusst getrennt, damit der Unterschied zwischen den Techniken im Code sichtbar bleibt.</p>
 *
 * <p><strong>Wichtiger Hintergrund zu JPA vs. JDBC:</strong> Die Entity nutzt {@code GenerationType.IDENTITY}.
 * Damit kann Hibernate INSERTs <em>nicht</em> als JDBC-Batch buendeln (es braucht die generierte id sofort nach jedem INSERT).
 * Deshalb arbeiten die JPA-Stufen (W1/W2) prinzipbedingt zeilenweise, waehrend die echten
 * Batch-Stufen (ab W3) direkt ueber {@link JdbcTemplate} gehen.</p>
 */
@Service
public class WriteService {

    private final MeasurementRepository repository;
    private final JdbcTemplate jdbcTemplate;

    public WriteService(MeasurementRepository repository, JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Leert die Tabelle vor einem Lauf (nicht Teil der gemessenen Zeit). */
    public void truncate() {
        jdbcTemplate.execute("TRUNCATE TABLE measurements RESTART IDENTITY");
    }

    /**
     * <strong>W0 — Baseline.</strong> Speichert genau eine Zeile ueber {@code repository.save()} (ein Aufruf committet einzeln).
     * Wird vom Client in einer Schleife aufgerufen — ein HTTP-Request pro Zeile.
     */
    public Long saveOne(MeasurementRequest request) {
        return repository.save(request.toEntity()).getId();
    }

    /**
     * <strong>W1 — Bulk-Payload, Einzel-INSERT mit Autocommit.</strong> Alle Zeilen kommen in <em>einem</em> HTTP-Request an, werden aber weiterhin einzeln gespeichert.
     *
     * <p>Diese Methode ist <em>nicht</em> {@code @Transactional}: Jeder
     * {@code repository.save()}-Aufruf laeuft daher in seiner eigenen Transaktion und committet einzeln (N Commits, N {@code fsync}). Lektion:
     * Der HTTP-Overhead aus W0 ist weg — trotzdem bleibt es langsam, weil die vielen Commits dominieren.</p>
     */
    public int w1BulkAutocommit(List<MeasurementRequest> requests) {
        int count = 0;
        for (MeasurementRequest request : requests) {
            repository.save(request.toEntity()); // eigener Commit pro Zeile
            count++;
        }
        return count;
    }

    /**
     * <strong>W2 — Alles in EINER Transaktion.</strong> Gleiche zeilenweise INSERTs wie W1, aber die Methode ist {@code @Transactional}:
     *   Alle Zeilen teilen sich <em>eine</em>
     * Transaktion und damit <em>einen</em> Commit.
     *
     * <p>Lektion: Der grosse Sprung kommt nicht vom Buendeln der INSERTs, sondern vom Zusammenfassen der Commits (N {@code fsync} → 1).</p>
     */
    @Transactional
    public int w2SingleTransaction(List<MeasurementRequest> requests) {
        int count = 0;
        for (MeasurementRequest request : requests) {
            Measurement entity = request.toEntity();
            repository.save(entity);
            count++;
        }
        return count;
    }
}
