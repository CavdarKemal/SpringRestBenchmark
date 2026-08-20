package de.springrest.benchmark.dto;

import de.springrest.benchmark.entity.Measurement;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Eingabe-DTO fuer schreibende Stufen (W0..W8).
 *
 * <p>Eine einzelne zu speichernde Messung. Der Client schickt in W0 pro Zeile genau
 * ein solches Objekt (ein HTTP-Request je Zeile), spaeter ganze Listen davon.</p>
 *
 * @param ts        Zeitstempel; wenn {@code null}, setzt der Server die aktuelle Zeit
 * @param sensorId  Sensor-Kennung
 * @param category  Kategorie (kurzer Text)
 * @param v1..v8    numerische Messwerte
 * @param payload   optionaler Text-Payload
 */
public record MeasurementRequest(
        OffsetDateTime ts,
        int sensorId,
        String category,
        double v1, double v2, double v3, double v4,
        double v5, double v6, double v7, double v8,
        String payload) {

    /** Bildet das DTO auf eine neue {@link Measurement}-Entity ab. */
    public Measurement toEntity() {
        OffsetDateTime effectiveTs = ts != null ? ts : OffsetDateTime.now(ZoneOffset.UTC);
        return new Measurement(effectiveTs, sensorId, category,
                v1, v2, v3, v4, v5, v6, v7, v8, payload);
    }
}
