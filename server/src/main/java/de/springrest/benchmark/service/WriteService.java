package de.springrest.benchmark.service;

import de.springrest.benchmark.dto.MeasurementRequest;
import de.springrest.benchmark.entity.Measurement;
import de.springrest.benchmark.repository.MeasurementRepository;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Geschaeftslogik der schreibenden Benchmark-Stufen (W0..W8).
 *
 * <p>Jede Methode entspricht einer Optimierungsstufe. Sie sind bewusst getrennt, damit der Unterschied
 * zwischen den Techniken im Code sichtbar bleibt.</p>
 *
 * <p><strong>Wichtiger Hintergrund zu JPA vs. JDBC:</strong> Die Entity nutzt {@code GenerationType.IDENTITY}.
 * Damit kann Hibernate INSERTs <em>nicht</em> als JDBC-Batch buendeln (es braucht die generierte id sofort nach
 * jedem INSERT). Deshalb arbeiten die JPA-Stufen (W1/W2) prinzipbedingt zeilenweise, waehrend die echten
 * Batch-Stufen (ab W3) direkt ueber {@link JdbcTemplate} gehen.</p>
 */
@Service
public class WriteService {

    /** INSERT fuer die JDBC-Batch-Stufen (W3/W4). Gleiche Spalten wie im Datengenerator. */
    private static final String INSERT_SQL = """
            INSERT INTO measurements (ts, sensor_id, category, v1, v2, v3, v4, v5, v6, v7, v8, payload)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    /** Zeilen pro Batch-Chunk. Bewusster Kompromiss aus Treiber-Speicher und Round-Trip-Ersparnis. */
    private static final int BATCH_SIZE = 1_000;

    private final MeasurementRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final JdbcTemplate rewriteJdbcTemplate;
    private final JobLauncher jobLauncher;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JdbcBatchItemWriter<MeasurementRequest> measurementItemWriter;

    public WriteService(MeasurementRepository repository, JdbcTemplate jdbcTemplate,
                        @Qualifier("rewriteJdbcTemplate") JdbcTemplate rewriteJdbcTemplate,
                        JobLauncher jobLauncher, JobRepository jobRepository,
                        PlatformTransactionManager transactionManager,
                        JdbcBatchItemWriter<MeasurementRequest> measurementItemWriter) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
        this.rewriteJdbcTemplate = rewriteJdbcTemplate;
        this.jobLauncher = jobLauncher;
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.measurementItemWriter = measurementItemWriter;
    }

    /** Leert die Tabelle vor einem Lauf (nicht Teil der gemessenen Zeit). */
    public void truncate() {
        jdbcTemplate.execute("TRUNCATE TABLE measurements RESTART IDENTITY");
    }

    /**
     * <strong>W0 — Baseline.</strong> Speichert genau eine Zeile ueber {@code repository.save()} (ein Aufruf
     * committet einzeln). Wird vom Client in einer Schleife aufgerufen — ein HTTP-Request pro Zeile.
     */
    public Long saveOne(MeasurementRequest request) {
        return repository.save(request.toEntity()).getId();
    }

    /**
     * <strong>W1 — Bulk-Payload, Einzel-INSERT mit Autocommit.</strong> Alle Zeilen kommen in <em>einem</em>
     * HTTP-Request an, werden aber weiterhin einzeln gespeichert.
     *
     * <p>Diese Methode ist <em>nicht</em> {@code @Transactional}: Jeder {@code repository.save()}-Aufruf laeuft
     * daher in seiner eigenen Transaktion und committet einzeln (N Commits, N {@code fsync}). Lektion: Der
     * HTTP-Overhead aus W0 ist weg — trotzdem bleibt es langsam, weil die vielen Commits dominieren.</p>
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
     * <strong>W2 — Alles in EINER Transaktion.</strong> Gleiche zeilenweise INSERTs wie W1, aber die Methode ist
     * {@code @Transactional}: Alle Zeilen teilen sich <em>eine</em> Transaktion und damit <em>einen</em> Commit.
     *
     * <p>Lektion: Der grosse Sprung kommt nicht vom Buendeln der INSERTs, sondern vom Zusammenfassen der Commits
     * (N {@code fsync} → 1).</p>
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

    /**
     * <strong>W3 — JDBC-Batch-INSERT.</strong> Statt N einzelner INSERTs werden die Zeilen in Chunks von
     * {@link #BATCH_SIZE} als <em>ein</em> JDBC-Batch an die DB geschickt. Das spart die vielen Round-Trips.
     *
     * <p>Warum JDBC statt JPA? Bei {@code GenerationType.IDENTITY} kann Hibernate INSERTs nicht batchen — hier
     * umgehen wir Hibernate bewusst und sprechen die DB direkt an.</p>
     */
    public int w3JdbcBatch(List<MeasurementRequest> rows) {
        return batchInsert(jdbcTemplate, rows);
    }

    /**
     * <strong>W4 — JDBC-Batch mit {@code reWriteBatchedInserts=true}.</strong> Gleicher Batch wie W3, aber der
     * pgjdbc-Treiber schreibt den Batch in ein Multi-Row-INSERT um
     * ({@code INSERT ... VALUES (...),(...),(...)}). Das reduziert Parsing- und Protokoll-Overhead nochmals.
     */
    public int w4JdbcBatchRewrite(List<MeasurementRequest> rows) {
        return batchInsert(rewriteJdbcTemplate, rows);
    }

    /**
     * <strong>W5 — Spring Batch (chunk-orientiert).</strong> Verarbeitet die Zeilen mit einem echten
     * Spring-Batch-Job: ein {@code ListItemReader} liefert die Zeilen, ein {@link JdbcBatchItemWriter} schreibt
     * sie chunk-weise (je {@link #BATCH_SIZE}) als JDBC-Batch. Nach jedem Chunk committet Spring Batch und haelt
     * den Fortschritt im JobRepository fest.
     *
     * <p>Durchsatz-technisch liegt W5 in der Naehe von W3 (gleiche Batch-Idee). Der Gewinn ist ein anderer:
     * <em>Robustheit</em> — Chunk-Commit, Wiederaufsetzbarkeit (Restart), Skip-/Retry-Policies, Monitoring ueber
     * die Batch-Metadaten. Das ist der Sinn eines Batch-<em>Frameworks</em> gegenueber rohem JDBC.</p>
     *
     * <p>Job und Step werden pro Aufruf gebaut, weil die Daten aus dem jeweiligen Request stammen. Jeder Lauf
     * bekommt eindeutige Job-Parameter, damit Spring Batch ihn als neue Instanz ausfuehrt.</p>
     */
    public int w5SpringBatch(List<MeasurementRequest> rows) {
        Step step = new StepBuilder("w5-import-step", jobRepository)
                .<MeasurementRequest, MeasurementRequest>chunk(BATCH_SIZE, transactionManager)
                .reader(new ListItemReader<>(rows))
                .writer(measurementItemWriter)
                .build();
        Job job = new JobBuilder("w5-import-job", jobRepository)
                .start(step)
                .build();
        try {
            JobExecution execution = jobLauncher.run(job,
                    new JobParametersBuilder().addLong("run", System.nanoTime()).toJobParameters());
            if (!"COMPLETED".equals(execution.getStatus().name())) {
                throw new IllegalStateException("Spring-Batch-Job nicht erfolgreich: " + execution.getStatus());
            }
            return rows.size();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Spring-Batch-Job fehlgeschlagen", e);
        }
    }

    /**
     * <strong>W6 — Postgres COPY.</strong> Nutzt den nativen Bulk-Load-Pfad von PostgreSQL (Protokoll
     * {@code COPY ... FROM STDIN}) ueber den pgjdbc-{@link CopyManager}. Das ist der schnellste Weg,
     * grosse Datenmengen zu laden: Die DB umgeht den regulaeren, pro-Zeile geplanten INSERT-Pfad.
     */
    public int w6Copy(List<MeasurementRequest> rows) {
        DataSource dataSource = jdbcTemplate.getDataSource();
        if (dataSource == null) {
            throw new IllegalStateException("Keine DataSource verfuegbar");
        }
        String sql = "COPY measurements (ts, sensor_id, category, v1, v2, v3, v4, v5, v6, v7, v8, payload) "
                + "FROM STDIN WITH (FORMAT csv)";
        try (Connection connection = dataSource.getConnection()) {
            CopyManager copyManager = connection.unwrap(PGConnection.class).getCopyAPI();
            long copied = copyManager.copyIn(sql, new StringReader(toCsv(rows)));
            return (int) copied;
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("COPY fehlgeschlagen", e);
        }
    }

    /**
     * Baut den CSV-Text fuer COPY. Unsere Daten enthalten keine Sonderzeichen (Kategorie aus fester Liste,
     * Payload nur Kleinbuchstaben), daher ist kein CSV-Escaping noetig. Ein leeres Feld bedeutet im
     * CSV-Modus {@code NULL} — genau richtig fuer ein fehlendes payload.
     */
    private String toCsv(List<MeasurementRequest> rows) {
        StringBuilder sb = new StringBuilder(rows.size() * 96);
        for (MeasurementRequest r : rows) {
            OffsetDateTime ts = r.ts() != null ? r.ts() : OffsetDateTime.now(ZoneOffset.UTC);
            sb.append(ts).append(',').append(r.sensorId()).append(',').append(r.category()).append(',')
              .append(r.v1()).append(',').append(r.v2()).append(',').append(r.v3()).append(',').append(r.v4()).append(',')
              .append(r.v5()).append(',').append(r.v6()).append(',').append(r.v7()).append(',').append(r.v8()).append(',');
            if (r.payload() != null) {
                sb.append(r.payload());
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** Gemeinsame Batch-Insert-Logik fuer W3/W4; unterscheidet sich nur im verwendeten JdbcTemplate. */
    private int batchInsert(JdbcTemplate template, List<MeasurementRequest> rows) {
        template.batchUpdate(INSERT_SQL, rows, BATCH_SIZE, (ps, r) -> {
            OffsetDateTime ts = r.ts() != null ? r.ts() : OffsetDateTime.now(ZoneOffset.UTC);
            ps.setObject(1, Timestamp.from(ts.toInstant()));
            ps.setInt(2, r.sensorId());
            ps.setString(3, r.category());
            ps.setDouble(4, r.v1());
            ps.setDouble(5, r.v2());
            ps.setDouble(6, r.v3());
            ps.setDouble(7, r.v4());
            ps.setDouble(8, r.v5());
            ps.setDouble(9, r.v6());
            ps.setDouble(10, r.v7());
            ps.setDouble(11, r.v8());
            if (r.payload() != null) {
                ps.setString(12, r.payload());
            } else {
                ps.setNull(12, Types.VARCHAR);
            }
        });
        return rows.size();
    }
}
