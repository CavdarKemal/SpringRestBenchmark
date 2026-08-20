package de.springrest.benchmark.service;

import de.springrest.benchmark.dto.CategoryStat;
import de.springrest.benchmark.dto.MeasurementDto;
import de.springrest.benchmark.entity.Measurement;
import de.springrest.benchmark.repository.MeasurementRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

    /** Gemeinsamer RowMapper von einer Projektionszeile auf ein {@link MeasurementDto}. */
    private static final RowMapper<MeasurementDto> MAPPER = (rs, rowNum) -> new MeasurementDto(
            rs.getLong("id"),
            rs.getObject("ts", OffsetDateTime.class),
            rs.getInt("sensor_id"),
            rs.getString("category"),
            rs.getDouble("v1"));

    private final MeasurementRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ReadService(MeasurementRepository repository, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
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
     * schlankes {@link MeasurementDto} ab. Gegenueber R0 entfallen {@code v2..v8} und das {@code payload}.
     */
    @Transactional(readOnly = true)
    public List<MeasurementDto> projection() {
        return jdbcTemplate.query(PROJECTION_SQL, MAPPER);
    }

    /**
     * <strong>R2 (Offset).</strong> Eine Seite per {@code OFFSET ? LIMIT ?}. Nachteil: Die DB muss die
     * uebersprungenen Zeilen bei jeder tieferen Seite erneut durchlaufen — der Aufwand waechst mit der
     * Seitentiefe.
     */
    @Transactional(readOnly = true)
    public List<MeasurementDto> pageByOffset(long offset, int limit) {
        return jdbcTemplate.query(
                "SELECT id, ts, sensor_id, category, v1 FROM measurements ORDER BY id OFFSET ? LIMIT ?",
                MAPPER, offset, limit);
    }

    /**
     * <strong>R2 (Keyset/Seek).</strong> Eine Seite per {@code WHERE id > ? ORDER BY id LIMIT ?}. Nutzt den
     * Primaerschluessel-Index und bleibt <em>unabhaengig von der Seitentiefe</em> konstant schnell.
     */
    @Transactional(readOnly = true)
    public List<MeasurementDto> pageByKeyset(long afterId, int limit) {
        return jdbcTemplate.query(
                "SELECT id, ts, sensor_id, category, v1 FROM measurements WHERE id > ? ORDER BY id LIMIT ?",
                MAPPER, afterId, limit);
    }

    /**
     * <strong>R3 — Server-seitiges Streaming (NDJSON).</strong> Schreibt die Projektion Zeile fuer Zeile als
     * NDJSON (ein JSON-Objekt pro Zeile) in den Ausgabestrom. Ein <em>Server-seitiger Cursor</em>
     * ({@code fetchSize} + {@code autoCommit=false}) sorgt dafuer, dass immer nur ein kleiner Block im
     * Speicher liegt — konstanter Speicherbedarf, und das erste Byte erreicht den Client sehr frueh (niedrige
     * TTFB), statt erst nach dem vollstaendigen Serialisieren wie bei R0/R1.
     */
    public void streamNdjson(OutputStream out) {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            connection.setAutoCommit(false); // Voraussetzung fuer den PostgreSQL-Cursor
            try (PreparedStatement ps = connection.prepareStatement(
                    PROJECTION_SQL, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                ps.setFetchSize(1_000); // blockweise nachladen statt alles auf einmal
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        MeasurementDto dto = new MeasurementDto(
                                rs.getLong("id"),
                                rs.getObject("ts", OffsetDateTime.class),
                                rs.getInt("sensor_id"),
                                rs.getString("category"),
                                rs.getDouble("v1"));
                        out.write(objectMapper.writeValueAsBytes(dto));
                        out.write('\n');
                    }
                }
            }
            connection.commit();
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("NDJSON-Streaming fehlgeschlagen", e);
        }
    }

    /**
     * <strong>R5 — Caching.</strong> Eine bewusst teure Gruppierungsabfrage (Aggregat je Kategorie ueber alle
     * Zeilen). Dank {@link Cacheable} wird das Ergebnis nach dem ersten Aufruf aus dem Caffeine-Cache bedient —
     * die DB wird bei Wiederholungen gar nicht mehr befragt.
     */
    @Cacheable("categoryStats")
    public List<CategoryStat> categoryStats() {
        return jdbcTemplate.query(
                "SELECT category, count(*) AS cnt, avg(v1) AS a, min(v1) AS mn, max(v1) AS mx "
                        + "FROM measurements GROUP BY category ORDER BY category",
                (rs, rowNum) -> new CategoryStat(
                        rs.getString("category"),
                        rs.getLong("cnt"),
                        rs.getDouble("a"),
                        rs.getDouble("mn"),
                        rs.getDouble("mx")));
    }

    /** Leert den R5-Cache, damit der naechste Aufruf wieder „kalt" (aus der DB) laeuft. */
    @CacheEvict(value = "categoryStats", allEntries = true)
    public void evictCategoryStats() {
        // Body absichtlich leer — die Annotation erledigt das Evict.
    }

    /**
     * <strong>R6 — Parallel-Queries.</strong> Ein „Dashboard" fuehrt mehrere <em>unabhaengige</em> Abfragen aus.
     * Seriell summiert sich deren Zeit; parallel (ein Virtual Thread je Abfrage) zaehlt nur die langsamste.
     *
     * @param parallel {@code true} = alle Abfragen gleichzeitig ueber Virtual Threads, sonst nacheinander
     * @return Anzahl ausgefuehrter Abfragen
     */
    public int dashboard(boolean parallel) {
        int queryCount = 8;
        if (!parallel) {
            for (int part = 0; part < queryCount; part++) {
                aggregatePartition(part);
            }
            return queryCount;
        }
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Long>> futures = new ArrayList<>();
            for (int part = 0; part < queryCount; part++) {
                int p = part;
                futures.add(executor.submit(() -> aggregatePartition(p)));
            }
            for (Future<Long> future : futures) {
                future.get();
            }
            return queryCount;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Parallel-Queries unterbrochen", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Parallel-Queries fehlgeschlagen", e.getCause());
        }
    }

    /** Eine bewusst nicht-indizierte Aggregat-Abfrage (Full-Scan je Partition) — Kandidat fuer Parallelitaet. */
    private long aggregatePartition(int part) {
        Long count = jdbcTemplate.query(
                "SELECT count(*) AS c, avg(v1), min(v1), max(v1) FROM measurements WHERE sensor_id % 8 = ?",
                rs -> {
                    rs.next();
                    return rs.getLong("c");
                },
                part);
        return count != null ? count : 0L;
    }
}
