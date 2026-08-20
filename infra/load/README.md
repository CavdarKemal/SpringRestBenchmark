# k6-Lasttests (Mehr-Nutzer)

Die bisherigen Messungen im Client sind **Einzel-Nutzer**-Messungen (ein Request nach dem
anderen). Diese **k6**-Skripte erzeugen **viele gleichzeitige Nutzer** (Virtual Users, VUs)
und zeigen, wie der Server unter **Last** skaliert: Requests/s (RPS), Latenz-Perzentile (p95)
und Fehlerrate.

> [k6](https://k6.io) ist ein Open-Source-Lasttest-Werkzeug. Skripte sind JavaScript.

## Installation

- **Windows:** `choco install k6` oder `winget install k6.k6`
- **macOS:** `brew install k6`
- **Ohne Installation (Docker):**
  ```bash
  docker run --rm -i --network=host grafana/k6 run - < infra/load/read-load.js
  ```

## Voraussetzung

1. Server läuft (Port 8080) und PostgreSQL ist gestartet.
2. Für die **Read**-Last sollte die DB geseedet sein, z. B.:
   ```bash
   curl -X POST "http://localhost:8080/api/data/generate?rows=100000&clear=true"
   ```

## Skripte

### `read-load.js` — Read unter Last
Simuliert Dashboard-Verkehr: kleine, häufige Reads (Keyset-Seite, gecachtes Aggregat R5,
Parallel-Dashboard R6). 20 gleichzeitige Nutzer.

```bash
k6 run infra/load/read-load.js
# anderer Host/Port:
k6 run -e BASE_URL=http://localhost:8080 infra/load/read-load.js
```

### `write-load.js` — paralleler Ingest unter Last
10 gleichzeitige Nutzer fügen Batches ein (über den Datengenerator mit `clear=false`,
damit sie sich nicht gegenseitig per Truncate überschreiben). Eigene Metrik `rows_inserted`.

```bash
k6 run infra/load/write-load.js
k6 run -e ROWS=2000 infra/load/write-load.js
```

## Was man beobachtet

- **`http_reqs` / RPS:** Durchsatz an Requests pro Sekunde.
- **`http_req_duration` p95:** Latenz — steigt sie mit mehr VUs stark, ist eine Ressource
  (CPU, Connection-Pool, I/O) am Limit.
- **`http_req_failed`:** Fehlerrate (Threshold < 1 %).
- **`rows_inserted`** (Write): Gesamtzahl eingefügter Zeilen.

## Didaktische Aufgabe

Erhöhe die VU-Zahl in `options.stages` schrittweise (z. B. 20 → 50 → 100) und beobachte, ab
wann die p95-Latenz „durch die Decke geht". Vergleiche das mit der **HikariCP-Poolgröße**
(`HIKARI_MAX_POOL`, Default 10): Was passiert, wenn mehr gleichzeitige Requests als
Pool-Verbindungen anliegen?
