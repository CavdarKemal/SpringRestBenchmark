package de.springrest.benchmark.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web-Konfiguration fuer die lokale Lehr-Umgebung.
 *
 * <p>Der React-Client laeuft im Vite-Dev-Server (Port 5173), der Backend-Server auf
 * Port 8080. Damit der Browser Cross-Origin-Requests erlaubt, wird CORS fuer den
 * Dev-Origin freigegeben.</p>
 *
 * <p>Wichtig fuer das Mess-Harness: {@code Server-Timing} muss als <em>exposed header</em>
 * deklariert werden, sonst kann das clientseitige JavaScript ihn nicht auslesen.</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173", "http://127.0.0.1:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .exposedHeaders("Server-Timing", "X-Wire-Bytes", "X-Rows")
                .allowCredentials(false);
    }
}
