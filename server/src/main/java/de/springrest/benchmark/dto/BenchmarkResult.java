package de.springrest.benchmark.dto;

/**
 * Einheitlicher Ergebnis-Envelope fuer schreibende Benchmark-Stufen (W0..W8).
 *
 * <p>Statt nur "OK" zurueckzugeben, liefert jeder Write-Endpoint messbare Kennzahlen.
 * Der React-Client stellt diese direkt als Balken/Linien dar und kann die Stufen so
 * unmittelbar vergleichen.</p>
 *
 * @param stage           Kennung der Stufe, z. B. {@code "W3-jdbc-batch"}
 * @param rowsProcessed   Anzahl tatsaechlich verarbeiteter Zeilen
 * @param serverMillis    reine Server-/DB-Zeit in Millisekunden (ohne Netzwerk)
 * @param rowsPerSecond   abgeleiteter Durchsatz (Zeilen pro Sekunde)
 * @param note            optionaler Hinweis fuer die Doku-Anzeige (z. B. Batch-Groesse)
 */
public record BenchmarkResult(
        String stage,
        long rowsProcessed,
        double serverMillis,
        double rowsPerSecond,
        String note) {

    /**
     * Baut ein Ergebnis und berechnet den Durchsatz aus Zeilen und Dauer.
     *
     * @param stage         Stufen-Kennung
     * @param rowsProcessed verarbeitete Zeilen
     * @param serverMillis  gemessene Server-Zeit in Millisekunden
     * @param note          optionaler Hinweis (darf {@code null} sein)
     */
    public static BenchmarkResult of(String stage, long rowsProcessed, double serverMillis, String note) {
        double rowsPerSecond = serverMillis > 0
                ? rowsProcessed / (serverMillis / 1000.0)
                : 0.0;
        return new BenchmarkResult(stage, rowsProcessed, serverMillis, rowsPerSecond, note);
    }
}
