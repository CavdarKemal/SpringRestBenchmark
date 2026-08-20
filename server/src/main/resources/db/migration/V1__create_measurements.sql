-- ============================================================================
--  V1 — Basistabelle 'measurements'
-- ============================================================================
--  Eine bewusst breite, generische Tabelle. Sie dient als Datenlieferant fuer
--  alle Benchmark-Stufen (Read wie Write) und laesst sich leicht auf Millionen
--  Zeilen skalieren.
--
--  Spaltenwahl:
--    - id           : Primaerschluessel, zugleich monotone Sortierachse fuer
--                     Keyset-Pagination (R2).
--    - ts           : Zeitstempel der Messung (Filter-/Sortierbeispiele).
--    - sensor_id    : niedrige Kardinalitaet -> gut fuer Gruppierungen/Caching (R5).
--    - category     : kurzer Text -> Projektionen (R1) koennen ihn weglassen.
--    - v1..v8       : numerische Nutzlast -> macht Zeilen "schwer" genug, damit
--                     Serialisierung und Bandbreite messbar werden.
--    - payload      : optionaler groesserer Text -> zeigt den Effekt von
--                     Projektion (R1) und Kompression (R4) besonders deutlich.
-- ============================================================================

CREATE TABLE measurements (
    id          BIGSERIAL       PRIMARY KEY,
    ts          TIMESTAMPTZ     NOT NULL,
    sensor_id   INTEGER         NOT NULL,
    category    VARCHAR(32)     NOT NULL,
    v1          DOUBLE PRECISION NOT NULL,
    v2          DOUBLE PRECISION NOT NULL,
    v3          DOUBLE PRECISION NOT NULL,
    v4          DOUBLE PRECISION NOT NULL,
    v5          DOUBLE PRECISION NOT NULL,
    v6          DOUBLE PRECISION NOT NULL,
    v7          DOUBLE PRECISION NOT NULL,
    v8          DOUBLE PRECISION NOT NULL,
    payload     TEXT
);

-- Index fuer Zeitbereichs-Abfragen und Sortierung nach ts.
CREATE INDEX idx_measurements_ts ON measurements (ts);

-- Index fuer Filter/Gruppierung nach Sensor (Caching-Beispiel R5).
CREATE INDEX idx_measurements_sensor ON measurements (sensor_id);
