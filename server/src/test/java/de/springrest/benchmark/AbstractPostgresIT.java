package de.springrest.benchmark;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Basisklasse fuer alle Integrationstests.
 *
 * <p>Startet <strong>einmalig</strong> ein echtes PostgreSQL in einem Container
 * (Singleton-Pattern: der statische Container wird nur einmal hochgefahren und von
 * allen Testklassen geteilt). Ueber {@link ServiceConnection} verdrahtet Spring Boot
 * die DataSource automatisch mit diesem Container — es sind keine manuellen
 * {@code spring.datasource.*}-Properties noetig.</p>
 *
 * <p>Flyway migriert das Schema beim ersten Kontextstart; die Tests laufen also gegen
 * exakt dasselbe Schema wie die Anwendung.</p>
 */
@SpringBootTest
public abstract class AbstractPostgresIT {

    // Hinweis: In Testcontainers 2.x ist diese Klasse (Paket
    // org.testcontainers.postgresql) final und NICHT generisch — daher ohne <>.
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

    static {
        POSTGRES.start();
    }
}
