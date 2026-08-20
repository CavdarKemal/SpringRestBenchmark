package de.springrest.benchmark.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * JPA-Entity zur Tabelle {@code measurements}.
 *
 * <p>Bewusst als "schwere", breite Zeile modelliert: Neben dem Zeitstempel und der
 * Sensor-Zuordnung gibt es acht numerische Messwerte ({@code v1}..{@code v8}) sowie
 * ein optionales Text-{@code payload}. Diese Fuelle macht Unterschiede zwischen den
 * Optimierungsstufen (Projektion, Kompression, Serialisierungsformat) ueberhaupt
 * erst messbar.</p>
 *
 * <p>Das Schema stammt ausschliesslich aus Flyway (siehe
 * {@code V1__create_measurements.sql}); Hibernate validiert nur (ddl-auto=validate).</p>
 */
@Entity
@Table(name = "measurements")
public class Measurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ts", nullable = false)
    private OffsetDateTime ts;

    @Column(name = "sensor_id", nullable = false)
    private int sensorId;

    @Column(name = "category", nullable = false, length = 32)
    private String category;

    @Column(name = "v1", nullable = false) private double v1;
    @Column(name = "v2", nullable = false) private double v2;
    @Column(name = "v3", nullable = false) private double v3;
    @Column(name = "v4", nullable = false) private double v4;
    @Column(name = "v5", nullable = false) private double v5;
    @Column(name = "v6", nullable = false) private double v6;
    @Column(name = "v7", nullable = false) private double v7;
    @Column(name = "v8", nullable = false) private double v8;

    @Column(name = "payload")
    private String payload;

    /** Von JPA benoetigter parameterloser Konstruktor. */
    protected Measurement() {
    }

    public Measurement(OffsetDateTime ts, int sensorId, String category,
                       double v1, double v2, double v3, double v4,
                       double v5, double v6, double v7, double v8, String payload) {
        this.ts = ts;
        this.sensorId = sensorId;
        this.category = category;
        this.v1 = v1;
        this.v2 = v2;
        this.v3 = v3;
        this.v4 = v4;
        this.v5 = v5;
        this.v6 = v6;
        this.v7 = v7;
        this.v8 = v8;
        this.payload = payload;
    }

    public Long getId() { return id; }
    public OffsetDateTime getTs() { return ts; }
    public int getSensorId() { return sensorId; }
    public String getCategory() { return category; }
    public double getV1() { return v1; }
    public double getV2() { return v2; }
    public double getV3() { return v3; }
    public double getV4() { return v4; }
    public double getV5() { return v5; }
    public double getV6() { return v6; }
    public double getV7() { return v7; }
    public double getV8() { return v8; }
    public String getPayload() { return payload; }
}
