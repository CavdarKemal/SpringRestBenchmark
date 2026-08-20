package de.springrest.benchmark.repository;

import de.springrest.benchmark.entity.Measurement;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring-Data-Repository fuer {@link Measurement}.
 *
 * <p>Dient in der Read-Baseline (R0) als bewusst naive Datenquelle: {@code findAll()}
 * laedt die komplette Tabelle als Entity-Liste in den Speicher. Spaetere Stufen
 * umgehen dieses Repository zugunsten von Projektionen, Streaming oder direktem JDBC.</p>
 */
public interface MeasurementRepository extends JpaRepository<Measurement, Long> {
}
