package de.springrest.benchmark.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Aktiviert die Spring-Cache-Abstraktion fuer Stufe R5.
 *
 * <p>Als Cache-Provider ist Caffeine auf dem Classpath; Spring Boot konfiguriert ihn automatisch
 * (siehe {@code spring.cache.type=caffeine}). Ohne {@link EnableCaching} wuerden die
 * {@code @Cacheable}-Annotationen ignoriert.</p>
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
