package de.springrest.benchmark;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Einstiegspunkt des SpringRestBenchmark-Servers.
 *
 * <p>Dieses Lehrprojekt demonstriert schrittweise Optimierungen des Datendurchsatzes
 * zwischen Client, Spring-Boot-Server und PostgreSQL. Jede Optimierungsstufe ist als
 * eigener REST-Endpoint umgesetzt, sodass Studenten die Techniken einzeln messen und
 * direkt vergleichen koennen.</p>
 */
@SpringBootApplication
public class BenchmarkApplication {

    public static void main(String[] args) {
        SpringApplication.run(BenchmarkApplication.class, args);
    }
}
