package de.springrest.benchmark.config;

import de.springrest.benchmark.dto.MeasurementRequest;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Spring-Batch-Bausteine fuer Stufe W5.
 *
 * <p>Hier lebt der wiederverwendbare {@link JdbcBatchItemWriter}: Er schreibt pro Chunk einen JDBC-Batch in
 * die Tabelle {@code measurements}. Job und Step selbst werden pro Lauf im {@code WriteService} gebaut, weil die
 * zu ladenden Daten aus dem jeweiligen HTTP-Request stammen (ein per-Request-{@code ListItemReader}).</p>
 */
@Configuration
public class BatchJobConfig {

    /** Gleiche Spalten wie ueberall; Spring Batch fuehrt die INSERTs chunk-weise als JDBC-Batch aus. */
    static final String INSERT_SQL = """
            INSERT INTO measurements (ts, sensor_id, category, v1, v2, v3, v4, v5, v6, v7, v8, payload)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    /**
     * ItemWriter, der eine Chunk-Liste von {@link MeasurementRequest} als JDBC-Batch schreibt.
     * Positionsbasiert ueber einen {@code ItemPreparedStatementSetter} (das Record hat keine Bean-Getter,
     * die auf Spaltennamen passen).
     */
    @Bean
    public JdbcBatchItemWriter<MeasurementRequest> measurementItemWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<MeasurementRequest>()
                .dataSource(dataSource)
                .sql(INSERT_SQL)
                .itemPreparedStatementSetter((item, ps) -> {
                    OffsetDateTime ts = item.ts() != null ? item.ts() : OffsetDateTime.now(ZoneOffset.UTC);
                    ps.setObject(1, Timestamp.from(ts.toInstant()));
                    ps.setInt(2, item.sensorId());
                    ps.setString(3, item.category());
                    ps.setDouble(4, item.v1());
                    ps.setDouble(5, item.v2());
                    ps.setDouble(6, item.v3());
                    ps.setDouble(7, item.v4());
                    ps.setDouble(8, item.v5());
                    ps.setDouble(9, item.v6());
                    ps.setDouble(10, item.v7());
                    ps.setDouble(11, item.v8());
                    if (item.payload() != null) {
                        ps.setString(12, item.payload());
                    } else {
                        ps.setNull(12, Types.VARCHAR);
                    }
                })
                .build();
    }
}
