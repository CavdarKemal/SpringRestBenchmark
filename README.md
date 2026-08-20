# SpringRestBenchmark

Ein **Lehrprojekt** rund um die Frage: *Wie holt man den maximalen Datendurchsatz
zwischen React-Client, Spring-Boot-Server und PostgreSQL heraus?*

Das Projekt entwickelt die Antwort **in Stufen**. Jede Stufe demonstriert eine
Optimierungstechnik, ist als eigener REST-Endpoint umgesetzt, wird im Browser
gemessen und ausführlich dokumentiert. So wird die Verbesserungskurve unmittelbar
sichtbar.

## Architektur

```
client/   React + Vite + TypeScript  — Benchmark-Runner, Recharts-Dashboard
server/   Spring Boot 4.1 (Java 21)   — ein Endpoint je Optimierungsstufe
infra/    Docker Compose              — PostgreSQL 16
docs/     Markdown je Stufe           — ausführliche Erklärungen
```

## Voraussetzungen

- **JDK 21** (unter `C:\Program Files\AdoptOpenJDK\jdk-21`)
- **Maven 4** (Aufruf über `ci 21` / `cit 21`) — ⚠️ `ci 11` funktioniert **nicht**,
  Spring Boot 4.1 verlangt mindestens JDK 17.
- **Docker** (für PostgreSQL)
- **Node.js 20+** und npm (für den Client)

## Schnellstart

```bash
# 1) Datenbank starten
docker compose -f infra/docker-compose.yml up -d

# 2) Server bauen und starten
cit 21                                             # Build inkl. Tests (im Ordner server/)
java -jar server/target/benchmark-server-0.0.1-SNAPSHOT.jar

# 3) Client starten
npm install --prefix client
npm run dev --prefix client                        # http://localhost:5173
```

Der Vite-Dev-Server leitet `/api/*` und `/actuator/*` per Proxy an den Server
(Port 8080) weiter — es ist also keine CORS-Konfiguration nötig.

## Bedienung

1. Im Client die **Zeilenzahl** und optional die **Payload-Länge** wählen, dann **Seed** ausführen.
2. Eine einzelne **Stufe** (Button *Run*) oder einen ganzen Track auf einmal
   (**▶ Alle Write / Alle Read**) ausführen.
3. Das **Vergleichs-Dashboard** zeigt zwei Kurven — Write-Durchsatz (Zeilen/s) und
   Read-Nutzlast (Wire KB) — plus eine Detailtabelle (Serverzeit, TTFB, Bytes, Durchsatz).
4. Der **Daten-Viewer** unten rendert die Zeilen **virtualisiert** (nur sichtbare im DOM,
   flüssig auch bei 100 000+ Zeilen) und kann sie per **Streaming** inkrementell einfüllen.

## Mess-Prinzip

- Der Server setzt bei jeder Antwort den Header `Server-Timing: app;dur=<ms>`
  (reine Serverzeit). Schreibende Stufen liefern zusätzlich einen
  `BenchmarkResult`-Envelope mit Zeilenzahl und Serverzeit.
- Der Client misst clientseitig Gesamtzeit, Time-To-First-Byte und die tatsächlich
  übertragene Byte-Menge (aus dem Antwort-Stream).

## Tests

```bash
cit 21          # baut den Server inkl. aller Tests (JDK 21 + Maven 4)
```

- **Unit-Tests** für reine Logik (z. B. Durchsatz-Berechnung) — ohne Spring/DB, blitzschnell.
- **Integrationstests** gegen ein **echtes PostgreSQL** via **Testcontainers**: Die Basisklasse
  `AbstractPostgresIT` startet einmalig einen Container und verdrahtet die DataSource per
  `@ServiceConnection`; Flyway migriert das echte Schema.
- Konvention: Integrationstests heißen `*IntegrationTest` (laufen so in der Surefire-`test`-Phase),
  damit ein einziges `cit 21` **alle** Tests ausführt. Jede Optimierungsstufe bekommt mindestens
  einen Test.
- Voraussetzung: Docker muss laufen (Testcontainers zieht `postgres:16`).

## Lasttests (Mehr-Nutzer)

Die Client-Messungen sind Einzel-Nutzer-Messungen. Für **nebenläufige** Last (viele
gleichzeitige Nutzer, RPS, Latenz-Perzentile) gibt es [k6](https://k6.io)-Skripte unter
[infra/load/](infra/load/README.md) — für Read- und Write-Last, auch ohne k6-Installation
per Docker ausführbar.

## Optimierungs-Roadmap

Siehe [docs/README.md](docs/README.md) für den vollständigen Stufenplan
(Write-Track **W0–W8**, Read-Track **R0–R8**).

| Milestone | Inhalt | Status |
|-----------|--------|--------|
| **M0** | Fundament: Infra, Server-Skelett, Mess-Harness, Client | ✅ fertig |
| **M1** | Baselines R0 + W0 (bewusst naiv) | ✅ fertig |
| **M2** | Write-Track W1–W8 | ✅ fertig |
| **M3** | Read-Track R1–R8 | ✅ fertig |
| **M4** | Client-Rendering + Vergleichs-Dashboard | ✅ fertig |
