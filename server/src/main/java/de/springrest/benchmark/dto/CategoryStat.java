package de.springrest.benchmark.dto;

/**
 * Aggregat je Kategorie — Ergebnis der teuren Gruppierungsabfrage in Stufe R5 (Caching) und
 * Bestandteil des Dashboards in R6 (Parallel-Queries).
 *
 * @param category Kategorie
 * @param count    Anzahl Messungen dieser Kategorie
 * @param avgV1    Mittelwert von v1
 * @param minV1    Minimum von v1
 * @param maxV1    Maximum von v1
 */
public record CategoryStat(String category, long count, double avgV1, double minV1, double maxV1) {
}
