package de.springrest.benchmark.dto;

import java.time.OffsetDateTime;

/**
 * Einheitliche Fehlerantwort der REST-API.
 *
 * <p>Wird vom {@code GlobalExceptionHandler} befuellt, damit der Client jeden Fehler
 * in gleicher, maschinenlesbarer Form erhaelt (statt eines Stacktraces oder der
 * Default-Whitelabel-Seite).</p>
 *
 * @param timestamp Zeitpunkt des Fehlers
 * @param status    HTTP-Statuscode
 * @param error     Kurzbezeichnung des Fehlers (z. B. "Not Found")
 * @param message   menschenlesbare Detailmeldung
 * @param path      angefragter Pfad
 */
public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String path) {
}
