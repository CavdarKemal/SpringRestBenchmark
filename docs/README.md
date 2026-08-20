# Optimierungs-Roadmap

Diese Übersicht listet alle geplanten Stufen. Zu jeder umgesetzten Stufe gehört
eine eigene Markdown-Datei mit: *Konzept · Warum schneller · Welches Problem gelöst ·
Code-Walkthrough · gemessene Zahlen · Fallstricke/Trade-offs.*

## Write-Track — Ingest (Client → Server → DB)

| Stufe | Technik | Kernmittel | Doku |
|-------|---------|-----------|------|
| W0 | Baseline: 1 HTTP-Request pro Zeile, `save()`, Autocommit | Spring Data JPA | [W0.md](W0.md) ✅ |
| W1 | Bulk-Payload: N Zeilen pro Request, weiter Einzel-INSERT (Autocommit) | Spring MVC / JPA | [W1.md](W1.md) ✅ |
| W2 | Alle Zeilen in EINER Transaktion (1 Commit) | `@Transactional` | [W2.md](W2.md) ✅ |
| W3 | JDBC-Batch-INSERT (`batchUpdate`) | Spring JDBC | [W3.md](W3.md) ✅ |
| W4 | Batch + `reWriteBatchedInserts=true` | pgjdbc | [W4.md](W4.md) ✅ |
| W5 | Chunk-orientierter Import | Spring Batch | [W5.md](W5.md) ✅ |
| W6 | Bulk-Load per `COPY` | pgjdbc CopyManager | [W6.md](W6.md) ✅ |
| W7 | Parallel-Ingest + Pool-Sizing | Virtual Threads + HikariCP | [W7.md](W7.md) ✅ |
| W8 | Reactive Ingest mit Backpressure | R2DBC (Reactor) | [W8.md](W8.md) ✅ |

> Reihenfolge gegenüber dem ursprünglichen Plan leicht verfeinert, damit jede Stufe einen
> messbaren Effekt zeigt (Transaktionsgrenze vor Pool-Tuning; Pool-Sizing dort, wo unter
> Nebenläufigkeit wirksam — W7). Alle geplanten Techniken bleiben enthalten.

## Read-Track — Query (DB → Server → Client)

| Stufe | Technik | Kernmittel | Doku |
|-------|---------|-----------|------|
| R0 | Baseline: `findAll()` → volle Entities als JSON | Spring Data JPA | [R0.md](R0.md) ✅ |
| R1 | DTO-Projektion (nur benötigte Spalten) | Spring JDBC | [R1.md](R1.md) ✅ |
| R2 | Pagination: Offset → Keyset/Seek | Spring JDBC | [R2.md](R2.md) ✅ |
| R3 | Server-seitiges Streaming (NDJSON, Cursor) | StreamingResponseBody | [R3.md](R3.md) ✅ |
| R4 | HTTP-Kompression (gzip) | manuelle gzip-Kompression | [R4.md](R4.md) ✅ |
| R5 | Caching | Spring Cache + Caffeine | [R5.md](R5.md) ✅ |
| R6 | Async/Parallel-Queries | Virtual Threads | [R6.md](R6.md) ✅ |
| R7 | Binärformat statt JSON | CBOR (Jackson 3) | [R7.md](R7.md) ✅ |
| R8 | Reactive End-to-End (NDJSON) | R2DBC (Reactor) | [R8.md](R8.md) ✅ |

## Client-Rendering (Bonus) ✅

Auch die Anzeige gehört zum Durchsatz „bis zur Wahrnehmung":

- **Virtualisierte Liste** (TanStack Virtual): nur die sichtbaren Zeilen sind im DOM — flüssig
  auch bei 100 000+ Zeilen.
- **Streaming-Render**: Zeilen erscheinen inkrementell, während der NDJSON-Stream (R3) läuft.
- **Vergleichs-Dashboard**: Write-Durchsatz- und Read-Nutzlast-Kurven, „▶ Alle Write/Read
  ausführen", Detailtabelle.

## Didaktischer Hinweis

Die Stufen sind bewusst so gebaut, dass der Durchsatz über den Verlauf **monoton
steigt** — mit klar benannten Trade-offs (z. B. Keyset-Pagination verliert die
Möglichkeit, direkt auf Seite *n* zu springen). Genau diese Trade-offs sind der
Lernkern: Es gibt kein „schneller ist immer besser", sondern „schneller unter
welchen Bedingungen".
