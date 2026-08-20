package de.springrest.benchmark.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * Setzt bei jeder Antwort den HTTP-Header {@code Server-Timing} mit der auf dem Server
 * gemessenen Bearbeitungsdauer.
 *
 * <p>Der Browser stellt {@code Server-Timing} in der Network-/Performance-API bereit,
 * und unser React-Client liest ihn aus, um <em>Server-Zeit</em> von <em>Netzwerk-Zeit</em>
 * zu trennen. So sehen Studenten, ob eine Stufe den Server oder die Leitung entlastet.</p>
 *
 * <p>Format laut Spezifikation: {@code Server-Timing: app;dur=12.34}. Der Wert ist in
 * Millisekunden angegeben.</p>
 *
 * <p><strong>Hinweis:</strong> Fuer gepufferte Antworten (die meisten Stufen) ist das
 * exakt. Fuer echte Streaming-Antworten (R3/R8) sind die Header bereits gesendet, bevor
 * der Body fertig ist; dort liefert der Server die Server-Zeit stattdessen im
 * Antwort-Envelope bzw. als abschliessende Kennzahl. Siehe die jeweiligen Stufen.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ServerTimingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            double durationMillis = (System.nanoTime() - startNanos) / 1_000_000.0;
            // Nur setzen, wenn die Antwort noch nicht abgeschickt wurde (nicht-Streaming).
            if (!response.isCommitted()) {
                response.setHeader("Server-Timing", "app;dur=" + String.format(Locale.ROOT, "%.2f", durationMillis));
            }
        }
    }
}
