package de.springrest.benchmark.dto;

import java.time.OffsetDateTime;

/**
 * Schlankes Lese-DTO fuer die Read-Stufen (ab R1).
 *
 * <p>Bewusst nur die tatsaechlich benoetigten Felder — <em>ohne</em> die schweren Messwerte
 * {@code v2..v8} und <em>ohne</em> das {@code payload}. Genau dieser Unterschied zur vollen Entity
 * (die R0 serialisiert) macht die Nutzlast deutlich kleiner. Das ist die Lektion von R1 (Projektion)
 * und die Basis fuer R4 (Kompression), R3 (Streaming) usw.</p>
 *
 * @param id        Primaerschluessel (zugleich Sortierachse fuer Keyset-Pagination in R2)
 * @param ts        Zeitstempel der Messung
 * @param sensorId  Sensor-Kennung
 * @param category  Kategorie
 * @param v1        ein einzelner Messwert (stellvertretend)
 */
public record MeasurementDto(
        long id,
        OffsetDateTime ts,
        int sensorId,
        String category,
        double v1) {
}
