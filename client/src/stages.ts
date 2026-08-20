// ============================================================================
//  Stufen-Registry
// ============================================================================
//  Jede Optimierungsstufe (W0..W8, R0..R8) wird hier registriert. Das Dashboard
//  listet automatisch alle eingetragenen Stufen und kann sie einzeln ausfuehren
//  und vergleichen.
// ============================================================================

import { clearData } from './api';
import { measure, toRunResult } from './harness';
import { makeRow } from './rows';
import type { Measurement } from './harness';
import type { RunResult, Stage } from './types';

/** Antwort-Envelope der schreibenden Verwaltungs-Endpoints (siehe BenchmarkResult.java). */
interface BenchmarkEnvelope {
  stage: string;
  rowsProcessed: number;
  serverMillis: number;
  rowsPerSecond: number;
  note?: string;
}

/**
 * Obergrenze fuer W0. Ein HTTP-Request pro Zeile ist so teuer, dass grosse Zeilenzahlen
 * unpraktisch lange dauern. Fuer die Baseline reichen wenige tausend Zeilen, um den
 * Effekt eindrucksvoll zu zeigen.
 */
const W0_MAX_ROWS = 2000;

/** Obergrenze fuer W1: N Einzel-Commits sind langsam; fuer die Lektion reichen 20 000. */
const W1_MAX_ROWS = 20_000;

/** Obergrenze fuer W8: reaktiver R2DBC-Bulk-Insert ist langsam; 20 000 genuegen fuer die Lektion. */
const W8_MAX_ROWS = 20_000;

/**
 * Gemeinsamer Ablauf fuer Bulk-Write-Stufen (W1..W6): baut N Zeilen im Client, schickt
 * sie in EINEM Request und liest die reine Server-Insert-Zeit aus dem Envelope.
 *
 * @param maxRows optionale Obergrenze fuer diese Stufe (z. B. bei sehr langsamen Stufen)
 */
async function runBulkWrite(
  id: string,
  label: string,
  url: string,
  rows: number,
  payloadLength: number,
  maxRows?: number,
): Promise<RunResult> {
  const count = maxRows ? Math.min(rows, maxRows) : rows;
  const payload = Array.from({ length: count }, () => makeRow(payloadLength));
  const body = JSON.stringify(payload);

  const m = await measure(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body,
  });
  const env = JSON.parse(m.bodyText) as BenchmarkEnvelope;
  // Fuer Write-Stufen ist die Serverzeit aus dem Envelope massgeblich (ohne Upload/Parse).
  return toRunResult(id, label, 'write', env.rowsProcessed, { ...m, serverMillis: env.serverMillis }, env.note);
}

/**
 * Gemeinsamer Ablauf fuer einfache Read-Stufen (R0/R1/R4...): GET, Bytes/TTFB messen, Zeilen aus dem
 * JSON-Array ableiten. Die Leitungsgroesse (wireBytes) kommt automatisch aus dem X-Wire-Bytes-Header.
 */
async function runRead(id: string, label: string, url: string, note: string): Promise<RunResult> {
  const m = await measure(url, { method: 'GET' });
  const rows = m.bodyText ? (JSON.parse(m.bodyText) as unknown[]).length : 0;
  return toRunResult(id, label, 'read', rows, m, note);
}

// ---------------------------------------------------------------------------
//  Seed (Infrastruktur) — baut den Datenbestand auf
// ---------------------------------------------------------------------------
const seedStage: Stage = {
  id: 'seed',
  label: 'Seed (JDBC-Batch)',
  track: 'write',
  description:
    'Baut den Testdatenbestand auf. Nutzt bereits JDBC-Batch-Inserts. Dient als Referenz fuer das ' +
    'Mess-Harness; die schrittweise Entwicklung dieser Technik ist Thema der Write-Stufen W0..W3.',
  async run(ctx): Promise<RunResult> {
    const url = `/api/data/generate?rows=${ctx.rows}&clear=true&payloadLength=${ctx.payloadLength}`;
    const m = await measure(url, { method: 'POST' });
    const envelope = JSON.parse(m.bodyText) as BenchmarkEnvelope;
    const withServerTime = { ...m, serverMillis: envelope.serverMillis };
    return toRunResult('seed', seedStage.label, 'write', envelope.rowsProcessed, withServerTime, envelope.note);
  },
};

// ---------------------------------------------------------------------------
//  W0 — Write-Baseline: ein HTTP-Request pro Zeile, save() + Autocommit
// ---------------------------------------------------------------------------
const w0Stage: Stage = {
  id: 'w0',
  label: 'W0 — 1 Request/Zeile',
  track: 'write',
  description:
    'Die naive Referenz: Der Client sendet fuer JEDE Zeile einen eigenen HTTP-Request, der Server ' +
    `speichert sie einzeln (save() + Autocommit). Hier dominiert der HTTP-Overhead. Auf ${W0_MAX_ROWS} ` +
    'Zeilen begrenzt, weil per-Zeile-HTTP sonst unpraktisch lange dauert.',
  async run(ctx): Promise<RunResult> {
    const count = Math.min(ctx.rows, W0_MAX_ROWS);
    const body = JSON.stringify(makeRow(ctx.payloadLength));
    const bodyBytes = new TextEncoder().encode(body).byteLength;

    // Fair starten: vor dem Lauf leeren, damit W0 aus dem gleichen Zustand startet.
    await clearData();

    const t0 = performance.now();
    let firstDoneAt = 0;
    for (let i = 0; i < count; i++) {
      const resp = await fetch('/api/write/w0', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body,
      });
      if (!resp.ok) throw new Error(`HTTP ${resp.status} bei /api/write/w0`);
      await resp.arrayBuffer(); // Antwort leeren, damit die Verbindung frei wird
      if (firstDoneAt === 0) firstDoneAt = performance.now();
    }
    const totalMillis = performance.now() - t0;

    // Die Serverzeit ist hier nicht als eine Zahl greifbar (viele Einzelrequests),
    // deshalb wird der Durchsatz aus der clientseitigen Gesamtzeit bestimmt.
    const m: Measurement = {
      bytes: bodyBytes * count,
      wireBytes: bodyBytes * count,
      totalMillis,
      ttfbMillis: firstDoneAt > 0 ? firstDoneAt - t0 : totalMillis,
      serverMillis: 0,
      bodyText: '',
    };
    return toRunResult('w0', w0Stage.label, 'write', count, m, `per-Zeile-HTTP, ${count} Zeilen (Cap ${W0_MAX_ROWS})`);
  },
};

// ---------------------------------------------------------------------------
//  W1 — Bulk-Payload, Einzel-INSERT, Autocommit pro Zeile
// ---------------------------------------------------------------------------
const w1Stage: Stage = {
  id: 'w1',
  label: 'W1 — Bulk, Einzel-Commit',
  track: 'write',
  description:
    'Alle Zeilen in EINEM Request, aber der Server speichert sie einzeln mit je eigenem Commit. Der ' +
    'HTTP-Overhead aus W0 ist weg — trotzdem kaum schneller, weil die vielen Commits (fsync!) dominieren. ' +
    `Auf ${W1_MAX_ROWS} Zeilen begrenzt.`,
  run: (ctx) => runBulkWrite('w1', w1Stage.label, '/api/write/w1', ctx.rows, ctx.payloadLength, W1_MAX_ROWS),
};

// ---------------------------------------------------------------------------
//  W2 — Alles in EINER Transaktion (ein Commit)
// ---------------------------------------------------------------------------
const w2Stage: Stage = {
  id: 'w2',
  label: 'W2 — Eine Transaktion',
  track: 'write',
  description:
    'Gleiche zeilenweise INSERTs wie W1, aber alle in EINER Transaktion — also nur EIN Commit statt N. ' +
    'Das ist der erste grosse Sprung: Nicht die INSERTs, sondern die vielen Commits waren der Flaschenhals.',
  run: (ctx) => runBulkWrite('w2', w2Stage.label, '/api/write/w2', ctx.rows, ctx.payloadLength),
};

// ---------------------------------------------------------------------------
//  W3 — JDBC-Batch-INSERT (batchUpdate in Chunks)
// ---------------------------------------------------------------------------
const w3Stage: Stage = {
  id: 'w3',
  label: 'W3 — JDBC-Batch',
  track: 'write',
  description:
    'Statt N einzelner INSERTs werden die Zeilen in Chunks als ein JDBC-Batch an die DB geschickt. Das ' +
    'spart die vielen Round-Trips — der erwartet grosse Sprung gegenueber W2. Nutzt rohes JDBC, weil ' +
    'Hibernate bei IDENTITY-Schluesseln nicht batchen kann.',
  run: (ctx) => runBulkWrite('w3', w3Stage.label, '/api/write/w3', ctx.rows, ctx.payloadLength),
};

// ---------------------------------------------------------------------------
//  W4 — JDBC-Batch mit reWriteBatchedInserts=true
// ---------------------------------------------------------------------------
const w4Stage: Stage = {
  id: 'w4',
  label: 'W4 — Batch + reWrite',
  track: 'write',
  description:
    'Wie W3, aber die zweite Verbindung nutzt reWriteBatchedInserts=true: pgjdbc schreibt den Batch in ' +
    'Multi-Row-INSERTs um (VALUES (..),(..),(..)). Das senkt Parsing- und Protokoll-Overhead noch einmal.',
  run: (ctx) => runBulkWrite('w4', w4Stage.label, '/api/write/w4', ctx.rows, ctx.payloadLength),
};

// ---------------------------------------------------------------------------
//  W5 — Spring Batch (chunk-orientiert, robust/restartfaehig)
// ---------------------------------------------------------------------------
const w5Stage: Stage = {
  id: 'w5',
  label: 'W5 — Spring Batch',
  track: 'write',
  description:
    'Chunk-orientierter Import mit einem echten Spring-Batch-Job (ListItemReader + JdbcBatchItemWriter). ' +
    'Durchsatz aehnlich W3 — der Mehrwert ist Robustheit: Chunk-Commit, Restart, Skip/Retry, Monitoring ueber ' +
    'die Batch-Metadatentabellen. Zeigt, wann ein Batch-Framework statt rohem JDBC sinnvoll ist.',
  run: (ctx) => runBulkWrite('w5', w5Stage.label, '/api/write/w5', ctx.rows, ctx.payloadLength),
};

// ---------------------------------------------------------------------------
//  W6 — Postgres COPY (nativer Bulk-Load)
// ---------------------------------------------------------------------------
const w6Stage: Stage = {
  id: 'w6',
  label: 'W6 — Postgres COPY',
  track: 'write',
  description:
    'Nutzt den nativen Bulk-Load-Pfad von PostgreSQL (COPY ... FROM STDIN) ueber den pgjdbc-CopyManager. ' +
    'Der schnellste Weg, Massendaten zu laden: Die DB umgeht den regulaeren, pro-Zeile geplanten INSERT-Pfad.',
  run: (ctx) => runBulkWrite('w6', w6Stage.label, '/api/write/w6', ctx.rows, ctx.payloadLength),
};

// ---------------------------------------------------------------------------
//  W7 — Parallel-Ingest ueber Virtual Threads (+ groesserer Pool)
// ---------------------------------------------------------------------------
const w7Stage: Stage = {
  id: 'w7',
  label: 'W7 — Parallel (VThreads)',
  track: 'write',
  description:
    'Teilt die Zeilen in Partitionen und fuegt sie gleichzeitig ueber mehrere DB-Verbindungen ein ' +
    '(Virtual-Thread-pro-Task). Erst mit genug Verbindungen im Pool bringt die Parallelitaet ihren ' +
    'Nutzen — deshalb nutzt diese Stufe einen groesseren Pool (16). Zeigt Parallelitaet + Pool-Sizing.',
  run: (ctx) => runBulkWrite('w7', w7Stage.label, '/api/write/w7', ctx.rows, ctx.payloadLength),
};

// ---------------------------------------------------------------------------
//  W8 — Reaktiver Ingest ueber R2DBC (nicht-blockierend, Backpressure)
// ---------------------------------------------------------------------------
const w8Stage: Stage = {
  id: 'w8',
  label: 'W8 — R2DBC reaktiv',
  track: 'write',
  description:
    'Nicht-blockierender Insert-Pfad ueber R2DBC: Die Zeilen laufen als reaktiver Strom in Chunks, die ' +
    'gleichzeitigen Inserts sind begrenzt (Backpressure). Reaktiv glaenzt bei hoher Nebenlaeufigkeit/IO — ' +
    'beim rohen Bulk-Durchsatz bleibt COPY (W6) klar vorn (hier sogar am langsamsten). Anderes ' +
    `Programmiermodell, kein Turbo-Knopf. Auf ${W8_MAX_ROWS} Zeilen begrenzt.`,
  run: (ctx) => runBulkWrite('w8', w8Stage.label, '/api/write/w8', ctx.rows, ctx.payloadLength, W8_MAX_ROWS),
};

// ---------------------------------------------------------------------------
//  R0 — Read-Baseline: findAll() -> komplette Tabelle als JSON
// ---------------------------------------------------------------------------
const r0Stage: Stage = {
  id: 'r0',
  label: 'R0 — findAll() komplett',
  track: 'read',
  description:
    'Die naive Referenz: Der Server laedt die komplette Tabelle als Entity-Liste und serialisiert sie ' +
    'als ein grosses JSON-Array. Alles landet im Speicher, das erste Byte kommt erst spaet (hohe TTFB). ' +
    'Setzt einen Datenbestand voraus (vorher "Seed").',
  async run(): Promise<RunResult> {
    const m = await measure('/api/read/r0', { method: 'GET' });
    // Zeilen aus der Antwort ableiten (das Parsen der grossen JSON ist Teil der Kosten).
    const rows = m.bodyText ? (JSON.parse(m.bodyText) as unknown[]).length : 0;
    return toRunResult('r0', r0Stage.label, 'read', rows, m, 'volle Entities, ein JSON-Array');
  },
};

// ---------------------------------------------------------------------------
//  R1 — DTO-Projektion (nur benoetigte Spalten)
// ---------------------------------------------------------------------------
const r1Stage: Stage = {
  id: 'r1',
  label: 'R1 — DTO-Projektion',
  track: 'read',
  description:
    'Statt der vollen Entity (R0) liefert der Server ein schlankes DTO mit nur den benoetigten Spalten ' +
    '(ohne v2..v8, ohne payload). Die Nutzlast schrumpft deutlich — besonders mit payload. Setzt einen ' +
    'Datenbestand voraus (vorher "Seed", idealerweise mit Payload-Laenge > 0).',
  run: () => runRead('r1', r1Stage.label, '/api/read/r1', 'nur benoetigte Spalten'),
};

// ---------------------------------------------------------------------------
//  R4 — HTTP-Kompression (gzip)
// ---------------------------------------------------------------------------
const r4Stage: Stage = {
  id: 'r4',
  label: 'R4 — gzip-Kompression',
  track: 'read',
  description:
    'Gleiche Projektionsdaten wie R1, aber gzip-komprimiert uebertragen. Der Browser dekomprimiert ' +
    'automatisch; die eingesparte Leitungsgroesse zeigt die Spalte "Wire KB" (aus dem X-Wire-Bytes-Header). ' +
    'JSON komprimiert sehr gut, weil es viel Wiederholung enthaelt.',
  run: () => runRead('r4', r4Stage.label, '/api/read/r4', 'gzip-komprimiert'),
};

// ---------------------------------------------------------------------------
//  R2 — Pagination: Offset vs. Keyset (ganze Tabelle seitenweise traversieren)
// ---------------------------------------------------------------------------
const R2_PAGE_SIZE = 5000;

/** Baut aus reinen Zahlen (ohne Server-Timing) ein Read-RunResult; Durchsatz aus der Gesamtzeit. */
function toReadResult(id: string, label: string, rows: number, bytes: number, totalMillis: number, note: string): RunResult {
  const m: Measurement = { bytes, wireBytes: bytes, totalMillis, ttfbMillis: totalMillis, serverMillis: 0, bodyText: '' };
  return toRunResult(id, label, 'read', rows, m, note);
}

async function traverseOffset(id: string, label: string): Promise<RunResult> {
  let rows = 0;
  let bytes = 0;
  let offset = 0;
  const t0 = performance.now();
  for (;;) {
    const resp = await fetch(`/api/read/r2/offset?offset=${offset}&limit=${R2_PAGE_SIZE}`);
    if (!resp.ok) throw new Error(`HTTP ${resp.status} bei /api/read/r2/offset`);
    const buf = new Uint8Array(await resp.arrayBuffer());
    bytes += buf.byteLength;
    const page = JSON.parse(new TextDecoder().decode(buf)) as unknown[];
    rows += page.length;
    if (page.length < R2_PAGE_SIZE) break;
    offset += R2_PAGE_SIZE;
  }
  return toReadResult(id, label, rows, bytes, performance.now() - t0, `Offset-Pagination, Seiten a ${R2_PAGE_SIZE}`);
}

async function traverseKeyset(id: string, label: string): Promise<RunResult> {
  let rows = 0;
  let bytes = 0;
  let afterId = 0;
  const t0 = performance.now();
  for (;;) {
    const resp = await fetch(`/api/read/r2/keyset?afterId=${afterId}&limit=${R2_PAGE_SIZE}`);
    if (!resp.ok) throw new Error(`HTTP ${resp.status} bei /api/read/r2/keyset`);
    const buf = new Uint8Array(await resp.arrayBuffer());
    bytes += buf.byteLength;
    const page = JSON.parse(new TextDecoder().decode(buf)) as { id: number }[];
    rows += page.length;
    if (page.length < R2_PAGE_SIZE) break;
    afterId = page[page.length - 1].id;
  }
  return toReadResult(id, label, rows, bytes, performance.now() - t0, `Keyset-Pagination, Seiten a ${R2_PAGE_SIZE}`);
}

const r2OffsetStage: Stage = {
  id: 'r2-offset',
  label: 'R2 — Offset-Pagination',
  track: 'read',
  description:
    'Blaettert die ganze Tabelle seitenweise per OFFSET/LIMIT durch. Tiefe Seiten werden zunehmend ' +
    'langsamer, weil die DB die uebersprungenen Zeilen jedes Mal erneut durchlaeuft — die Gesamtzeit ' +
    'waechst ueberproportional.',
  run: () => traverseOffset('r2-offset', r2OffsetStage.label),
};

const r2KeysetStage: Stage = {
  id: 'r2-keyset',
  label: 'R2 — Keyset-Pagination',
  track: 'read',
  description:
    'Dieselbe Traversierung, aber per WHERE id > afterId (Keyset/Seek). Nutzt den Primaerschluessel-Index, ' +
    'jede Seite ist gleich schnell — unabhaengig von der Tiefe. Der klare Gewinn gegenueber Offset bei ' +
    'tiefen Seiten.',
  run: () => traverseKeyset('r2-keyset', r2KeysetStage.label),
};

// ---------------------------------------------------------------------------
//  R3 — Server-seitiges Streaming (NDJSON)
// ---------------------------------------------------------------------------
const r3Stage: Stage = {
  id: 'r3',
  label: 'R3 — NDJSON-Streaming',
  track: 'read',
  description:
    'Der Server streamt die Zeilen ueber einen DB-Cursor als NDJSON (ein JSON-Objekt pro Zeile). Das erste ' +
    'Byte kommt sehr frueh (niedrige TTFB), der Speicher bleibt konstant — anders als R0/R1, die erst alles ' +
    'fertig serialisieren und dann senden.',
  async run(): Promise<RunResult> {
    const m = await measure('/api/read/r3', { method: 'GET' });
    const rows = m.bodyText ? m.bodyText.trim().split('\n').filter(Boolean).length : 0;
    return toRunResult('r3', r3Stage.label, 'read', rows, m, 'NDJSON-Stream, niedrige TTFB');
  },
};

// ---------------------------------------------------------------------------
//  R5 — Caching (Caffeine): kalt (DB) vs. warm (Cache)
// ---------------------------------------------------------------------------
const r5Stage: Stage = {
  id: 'r5',
  label: 'R5 — Caching',
  track: 'read',
  description:
    'Eine teure Aggregat-Abfrage, deren Ergebnis gecacht wird. Dieser Lauf leert erst den Cache, misst den ' +
    'KALTEN Aufruf (DB) und dann den WARMEN (Cache). Die Server-ms-Spalte zeigt den warmen Wert; die Notiz ' +
    'nennt beide. Bei Wiederholung wird die DB gar nicht mehr befragt.',
  async run(): Promise<RunResult> {
    await fetch('/api/read/r5/evict', { method: 'POST' });
    const cold = await measure('/api/read/r5', { method: 'GET' });
    const warm = await measure('/api/read/r5', { method: 'GET' });
    const rows = warm.bodyText ? (JSON.parse(warm.bodyText) as unknown[]).length : 0;
    return toRunResult('r5', r5Stage.label, 'read', rows, warm,
      `kalt ${cold.serverMillis.toFixed(0)}ms -> warm ${warm.serverMillis.toFixed(0)}ms (Cache-Treffer)`);
  },
};

// ---------------------------------------------------------------------------
//  R6 — Parallel-Queries (mehrere unabhaengige Abfragen)
// ---------------------------------------------------------------------------
async function runR6(id: string, label: string, parallel: boolean): Promise<RunResult> {
  const m = await measure(`/api/read/r6?parallel=${parallel}`, { method: 'GET' });
  const env = JSON.parse(m.bodyText) as BenchmarkEnvelope;
  return toRunResult(id, label, 'read', env.rowsProcessed, { ...m, serverMillis: env.serverMillis }, env.note);
}

const r6SeqStage: Stage = {
  id: 'r6-seq',
  label: 'R6 — Queries seriell',
  track: 'read',
  description:
    'Ein „Dashboard" fuehrt 8 unabhaengige Abfragen nacheinander aus. Die Serverzeit ist die Summe aller ' +
    'Abfragen — der Referenzwert fuer die parallele Variante.',
  run: () => runR6('r6-seq', r6SeqStage.label, false),
};

const r6ParStage: Stage = {
  id: 'r6-par',
  label: 'R6 — Queries parallel',
  track: 'read',
  description:
    'Dieselben 8 unabhaengigen Abfragen, aber gleichzeitig ueber Virtual Threads. Die Serverzeit sinkt Richtung ' +
    'der langsamsten Einzelabfrage statt der Summe — deutlich schneller als R6 seriell.',
  run: () => runR6('r6-par', r6ParStage.label, true),
};

/** Alle registrierten Stufen. Reihenfolge = Anzeigereihenfolge im Dashboard. */
export const STAGES: Stage[] = [
  w0Stage,
  w1Stage,
  w2Stage,
  w3Stage,
  w4Stage,
  w5Stage,
  w6Stage,
  w7Stage,
  w8Stage,
  r0Stage,
  r1Stage,
  r2OffsetStage,
  r2KeysetStage,
  r3Stage,
  r4Stage,
  r5Stage,
  r6SeqStage,
  r6ParStage,
  seedStage,
];
