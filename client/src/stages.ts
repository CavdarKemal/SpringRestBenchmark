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

/** Alle registrierten Stufen. Reihenfolge = Anzeigereihenfolge im Dashboard. */
export const STAGES: Stage[] = [
  w0Stage,
  w1Stage,
  w2Stage,
  w3Stage,
  w4Stage,
  w5Stage,
  w6Stage,
  r0Stage,
  seedStage,
];
