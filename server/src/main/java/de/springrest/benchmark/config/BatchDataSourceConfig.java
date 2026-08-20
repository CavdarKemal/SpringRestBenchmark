package de.springrest.benchmark.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Stellt zwei {@link JdbcTemplate}s bereit, um die Wirkung von PostgreSQLs
 * {@code reWriteBatchedInserts} in den Stufen W3 und W4 direkt vergleichbar zu machen.
 *
 * <ul>
 *   <li><b>jdbcTemplate</b> (primaer): nutzt die von Spring Boot konfigurierte DataSource
 *       (ohne {@code reWriteBatchedInserts}). Basis fuer W3 und den Datengenerator.</li>
 *   <li><b>rewriteJdbcTemplate</b>: nutzt eine zweite Verbindung mit
 *       {@code reWriteBatchedInserts=true}. Basis fuer W4.</li>
 * </ul>
 *
 * <p>Die zweite Verbindung wird bewusst <em>aus der primaeren DataSource abgeleitet</em>
 * (gleiche URL/Zugangsdaten, nur um den Parameter ergaenzt). Dadurch funktioniert sie
 * unveraendert auch in den Integrationstests, wo die primaere DataSource per
 * {@code @ServiceConnection} auf einen Testcontainer zeigt.</p>
 *
 * <p>Hinweis: Wir exponieren nur JdbcTemplates, <em>keine</em> zweite
 * {@code DataSource}-Bean — sonst wuerden Boots JPA-/Flyway-Autokonfigurationen
 * (die eine eindeutige DataSource erwarten) durcheinandergeraten.</p>
 */
@Configuration
public class BatchDataSourceConfig {

    /**
     * Primaeres JdbcTemplate auf der von Boot konfigurierten DataSource. Als
     * {@code @Primary} markiert, damit bestehende Injektionen (Datengenerator, W1..W3)
     * genau dieses bekommen.
     */
    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * JdbcTemplate auf einer zweiten Verbindung mit {@code reWriteBatchedInserts=true}.
     * Die DataSource wird hier erzeugt, aber nicht als Bean exponiert.
     */
    @Bean("rewriteJdbcTemplate")
    public JdbcTemplate rewriteJdbcTemplate(DataSource primary) {
        HikariDataSource source = (HikariDataSource) primary;
        String url = source.getJdbcUrl();
        String rewriteUrl = url + (url.contains("?") ? "&" : "?") + "reWriteBatchedInserts=true";

        HikariDataSource rewrite = new HikariDataSource();
        rewrite.setJdbcUrl(rewriteUrl);
        rewrite.setUsername(source.getUsername());
        rewrite.setPassword(source.getPassword());
        rewrite.setMaximumPoolSize(5);
        rewrite.setPoolName("rewrite-pool");
        return new JdbcTemplate(rewrite);
    }

    /**
     * JdbcTemplate fuer den parallelen Ingest (Stufe W7). Bewusst mit einem <em>groesseren</em> Pool
     * (16) und {@code reWriteBatchedInserts=true}: Erst wenn der Pool mindestens so viele Verbindungen
     * bietet wie parallele Tasks laufen, bringt die Parallelitaet ihren vollen Nutzen. Genau dieser
     * Zusammenhang (Pool-Groesse ↔ Parallelitaet) ist die Lektion von W7.
     */
    @Bean("parallelJdbcTemplate")
    public JdbcTemplate parallelJdbcTemplate(DataSource primary) {
        HikariDataSource source = (HikariDataSource) primary;
        String url = source.getJdbcUrl();
        String rewriteUrl = url + (url.contains("?") ? "&" : "?") + "reWriteBatchedInserts=true";

        HikariDataSource parallel = new HikariDataSource();
        parallel.setJdbcUrl(rewriteUrl);
        parallel.setUsername(source.getUsername());
        parallel.setPassword(source.getPassword());
        parallel.setMaximumPoolSize(16);
        parallel.setPoolName("parallel-pool");
        return new JdbcTemplate(parallel);
    }
}
