package de.springrest.benchmark.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reiner Unit-Test (kein Spring, keine DB) fuer die Durchsatz-Berechnung im
 * {@link BenchmarkResult}. Schnell und ohne Infrastruktur — das passende Werkzeug
 * fuer reine Logik.
 */
class BenchmarkResultTest {

    @Test
    @DisplayName("rowsPerSecond wird korrekt aus Zeilen und Dauer berechnet")
    void computesThroughput() {
        // 1000 Zeilen in 1000 ms == 1000 Zeilen/Sekunde
        BenchmarkResult result = BenchmarkResult.of("test", 1000, 1000.0, null);

        assertThat(result.rowsProcessed()).isEqualTo(1000);
        assertThat(result.serverMillis()).isEqualTo(1000.0);
        assertThat(result.rowsPerSecond()).isEqualTo(1000.0);
    }

    @Test
    @DisplayName("Bei 0 ms Dauer ist der Durchsatz 0 (keine Division durch Null)")
    void handlesZeroDuration() {
        BenchmarkResult result = BenchmarkResult.of("test", 500, 0.0, "note");

        assertThat(result.rowsPerSecond()).isZero();
        assertThat(result.note()).isEqualTo("note");
    }

    @Test
    @DisplayName("500 Zeilen in 250 ms ergeben 2000 Zeilen/Sekunde")
    void computesFractionalDuration() {
        BenchmarkResult result = BenchmarkResult.of("test", 500, 250.0, null);

        assertThat(result.rowsPerSecond()).isEqualTo(2000.0);
    }
}
