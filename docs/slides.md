---
marp: true
theme: default
paginate: true
title: SpringRestBenchmark — Datendurchsatz optimieren
---

<!-- _class: lead -->

# SpringRestBenchmark

## Datendurchsatz Schritt für Schritt optimieren

**Client (React) ⇄ Spring Boot 4.1 ⇄ PostgreSQL**

Ein Lehrprojekt: von der naivsten Technik zur besten — jede Stufe **gemessen** und
**erklärt**.

---

## Worum geht es?

- **Frage:** Wie holt man den maximalen Datendurchsatz zwischen Client, Server und Datenbank?
- **Antwort:** In **Stufen** — jede demonstriert **eine** Technik.
- Zwei Richtungen:
  - **Write (Ingest):** Client → Server → DB — Stufen **W0 … W8**
  - **Read (Query):** DB → Server → Client — Stufen **R0 … R8**
- **Kernbotschaft:** Es gibt kein „schneller ist immer besser" — jede Technik hat ihren
  **Kontext** und ihre **Trade-offs**.

---

## Architektur

```
client/   React + Vite + TypeScript   — Benchmark-Runner, Dashboard, virtualisierte Liste
server/   Spring Boot 4.1 (Java 21)    — ein REST-Endpoint je Optimierungsstufe
infra/    Docker Compose               — PostgreSQL 16
docs/     Markdown je Stufe            — ausführliche Erklärungen
```

- Jede Stufe = eigener Endpoint + eigene Service-Klasse + Doku + Messvergleich.
- Der Client wählt eine Stufe, misst automatisch und stellt die Kurve dar.

---

## Wie wird gemessen? — Das Mess-Harness

- **Server-seitig:** Header `Server-Timing: app;dur=<ms>` (reine Serverzeit); schreibende
  Stufen liefern zusätzlich einen `BenchmarkResult`-Envelope.
- **Client-seitig:** `performance.now()` + Stream-Reader misst
  - **Gesamtzeit** (Wanduhr)
  - **Time-To-First-Byte (TTFB)** — wichtig bei Streaming
  - **Bytes auf der Leitung** (bei Kompression via `X-Wire-Bytes`)
- **Metriken:** Zeilen/s, MB/s, TTFB, Serverzeit, Wire-KB.

> Nur wer sauber misst, sieht den Effekt einer Optimierung.

---

## Das Datenmodell

Eine bewusst **breite, generische** Tabelle `measurements`:

| Spalte | Zweck |
|--------|-------|
| `id` | PK, zugleich Sortierachse für Keyset-Pagination |
| `ts` | Zeitstempel |
| `sensor_id`, `category` | niedrige Kardinalität → Gruppierung/Caching |
| `v1 … v8` | numerische Nutzlast (macht Zeilen „schwer") |
| `payload` | optionaler Text → zeigt Projektion & Kompression |

Leicht auf **Millionen Zeilen** skalierbar.

---

<!-- _class: lead -->

# Write-Track

## Ingest: Client → Server → DB

---

## Write-Track — die Kurve

| Stufe | Technik | Zeilen/s | Lektion |
|-------|---------|---------:|---------|
| W0 | 1 HTTP-Request pro Zeile | ~sehr niedrig | HTTP + Commit pro Zeile |
| W1 | Bulk-Payload, Einzel-Commit | ~112 | HTTP weg — Commits dominieren |
| W2 | Eine Transaktion | ~500 | **~4×**: N Commits → 1 |
| W3 | JDBC-Batch | ~15 000 | **~30×**: Round-Trips bündeln |
| W4 | + reWriteBatchedInserts | ~45 000 | Multi-Row-INSERT |
| W5 | Spring Batch | ~22 000 | Robustheit, nicht Tempo |
| **W6** | **Postgres COPY** | **~100 000** | schnellster Bulk-Load |
| W7 | Virtual Threads + Pool | ~84 000 | Parallelität + Pool-Sizing |
| W8 | R2DBC reaktiv | ~900 | reaktiv ≠ Bulk-Turbo |

---

## Write — die zwei großen Hebel

1. **Commits bündeln** (W1 → W2): Ein `fsync` pro Zeile ist der versteckte Killer.
   Eine gemeinsame Transaktion bringt sofort das ~4-fache.
2. **Round-Trips bündeln** (W2 → W3): Ein JDBC-Batch statt N Einzel-INSERTs bringt
   nochmal ~30×.

Danach: **das richtige Werkzeug** schlägt jede INSERT-Optimierung → **COPY** (W6).

> Merke: Bei `GenerationType.IDENTITY` kann Hibernate INSERTs **nicht** batchen —
> deshalb ab W3 rohes JDBC.

---

## Write — Parallelität & Reaktiv

- **W7 — Virtual Threads:** Ingest über mehrere Verbindungen parallel.
  - Lektion: Der **Connection-Pool** muss ≥ Parallelität sein, sonst serialisiert alles.
- **W8 — R2DBC reaktiv:** non-blocking, mit Backpressure.
  - Ergebnis: **langsam** beim Bulk-Insert.
  - Lektion: **Reaktiv ist kein Turbo-Knopf** — es glänzt bei hoher Nebenläufigkeit / I/O,
    nicht beim stumpfen Massen-Insert.

---

<!-- _class: lead -->

# Read-Track

## Query: DB → Server → Client

---

## Read-Track — die Kurve

| Stufe | Technik | Effekt |
|-------|---------|--------|
| R0 | Baseline (volle Entities) | 35,4 MB · 8,8 s |
| R1 | DTO-Projektion | **3,3× kleiner** (10,7 MB) |
| R2 | Keyset- vs Offset-Pagination | Keyset **~3,4×** bei tiefen Seiten |
| R3 | NDJSON-Streaming | TTFB **~10× niedriger** (23 ms) |
| R4 | gzip-Kompression | **~15× kleiner** (2,4 MB) |
| R5 | Caching (Caffeine) | warm **~9×** (12 ms) |
| R6 | Parallel-Queries | ~1,6× (kernbegrenzt) |
| R7 | CBOR-Binärformat | ~25 % kleiner als R1 |
| R8 | R2DBC reaktiv | langsam → reaktiv ≠ Turbo |

---

## Read — vier Stellschrauben

- **Weniger Bytes:** Projektion (R1) → Kompression (R4) → Binärformat (R7)
  → zusammen **Größenordnungen** kleiner.
- **Früher liefern:** Streaming (R3) senkt die **TTFB** drastisch (erstes Byte sofort).
- **Weniger scannen:** Keyset-Pagination (R2) bleibt bei tiefen Seiten konstant schnell.
- **Arbeit vermeiden / verteilen:** Caching (R5), Parallel-Queries (R6).

> Trade-off-Beispiele: Keyset kann nicht auf „Seite n" springen; Cache muss invalidiert
> werden; Binärformat ist nicht menschenlesbar.

---

## Client-Rendering — auch das zählt

- **Virtualisierte Liste** (TanStack Virtual): nur sichtbare Zeilen im DOM
  → flüssig auch bei **100 000+** Zeilen.
- **Streaming-Render:** Zeilen erscheinen **inkrementell**, während der NDJSON-Stream läuft.
- **Vergleichs-Dashboard:** Write-Durchsatz- und Read-Nutzlast-Kurven + Detailtabelle,
  „▶ Alle Write/Read ausführen".

Durchsatz zählt bis **zur Wahrnehmung** des Nutzers, nicht nur bis zum Netzwerk.

---

## Die große Lektion

> **Es gibt kein „schneller ist immer besser".**

- Jede Technik hat einen **Kontext**, in dem sie glänzt — und Trade-offs.
- Der größte Hebel ist oft **das Bündeln** (Commits, Round-Trips) und **das richtige
  Werkzeug** (COPY), nicht das trendigste Framework.
- **Reaktiv** (W8/R8) ist ein anderes **Programmiermodell** für Nebenläufigkeit/IO —
  kein pauschaler Geschwindigkeitsgewinn.
- **Immer messen** — Intuition trügt.

---

## Ausprobieren

```bash
# 1) Datenbank
docker compose -f infra/docker-compose.yml up -d

# 2) Server (JDK 21 + Maven 4)
cit 21
java -jar server/target/benchmark-server-0.0.1-SNAPSHOT.jar

# 3) Client
npm install --prefix client
npm run dev --prefix client        # http://localhost:5173
```

Dann: **Seed** → einzelne Stufe oder **„Alle Write/Read"** → Kurve im Dashboard ansehen.

---

<!-- _class: lead -->

# Fragen?

Vollständige Erklärung je Stufe: `docs/W0–W8.md`, `docs/R0–R8.md`
Aufgaben für Studenten: `docs/AUFGABEN.md`
