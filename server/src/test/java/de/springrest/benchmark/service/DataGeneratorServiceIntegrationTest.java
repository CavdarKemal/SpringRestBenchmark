package de.springrest.benchmark.service;

import de.springrest.benchmark.AbstractPostgresIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integrationstest fuer den {@link DataGeneratorService} gegen ein echtes PostgreSQL.
 *
 * <p>Prueft, dass Seeding, Zaehlen und Leeren korrekt zusammenspielen — die Grundlage
 * fuer reproduzierbare Benchmark-Laeufe.</p>
 *
 * <p>Namenskonvention: Endung {@code IntegrationTest} (nicht {@code IT}), damit
 * Maven-Surefire die Klasse in der {@code test}-Phase ausfuehrt und ein einziges
 * {@code cit 21} alle Tests abdeckt.</p>
 */
class DataGeneratorServiceIntegrationTest extends AbstractPostgresIT {

    @Autowired
    DataGeneratorService generator;

    @BeforeEach
    void cleanSlate() {
        generator.clear();
    }

    @Test
    @DisplayName("generate() fuegt exakt die angeforderte Zeilenzahl ein")
    void generatesExactRowCount() {
        long inserted = generator.generate(500, true, 16);

        assertThat(inserted).isEqualTo(500);
        assertThat(generator.count()).isEqualTo(500);
    }

    @Test
    @DisplayName("generate() mit mehreren Batches (> BATCH_SIZE) zaehlt korrekt")
    void generatesAcrossMultipleBatches() {
        // BATCH_SIZE ist 1000 -> 2500 erzwingt drei Batches (1000 + 1000 + 500)
        long inserted = generator.generate(2500, true, 0);

        assertThat(inserted).isEqualTo(2500);
        assertThat(generator.count()).isEqualTo(2500);
    }

    @Test
    @DisplayName("clear() leert die Tabelle vollstaendig")
    void clearEmptiesTable() {
        generator.generate(100, true, 0);
        assertThat(generator.count()).isEqualTo(100);

        generator.clear();

        assertThat(generator.count()).isZero();
    }
}
