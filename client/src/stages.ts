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

// ---------------------------------------------------------------------------
//  Seed (Infrastruktur) — baut den Datenbestand auf
// ---------------------------------------------------------------------------
const seedStage: Stage = {
  id: 'seed',
  label: 'Seed (JDBC-Batch)',
  track: 'write',
  description:
    'Baut den Testdatenbestand auf. Nutzt bereits JDBC-Batch-Inserts. Dient als ' +
    'Referenz fuer das Mess-Harness; die schrittweise Entwicklung dieser Technik ist ' +
    'Thema der Write-Stufen W0..W3.',
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
    'Die naive Referenz: Der Client sendet fuer JEDE Zeile einen eigenen HTTP-Request, ' +
    'der Server speichert sie einzeln (save() + Autocommit). Hier dominiert der ' +
    `HTTP-Overhead. Auf ${W0_MAX_ROWS} Zeilen begrenzt, weil per-Zeile-HTTP sonst ` +
    'unpraktisch lange dauert.',
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
    return toRunResult('w0', w0Stage.label, 'write', count, m,
      `per-Zeile-HTTP, ${count} Zeilen (Cap ${W0_MAX_ROWS})`);
  },
};

// ---------------------------------------------------------------------------
//  R0 — Read-Baseline: findAll() -> komplette Tabelle als JSON
// ---------------------------------------------------------------------------
const r0Stage: Stage = {
  id: 'r0',
  label: 'R0 — findAll() komplett',
  track: 'read',
  description:
    'Die naive Referenz: Der Server laedt die komplette Tabelle als Entity-Liste und ' +
    'serialisiert sie als ein grosses JSON-Array. Alles landet im Speicher, das erste ' +
    'Byte kommt erst spaet (hohe TTFB). Setzt einen Datenbestand voraus (vorher "Seed").',
  async run(): Promise<RunResult> {
    const m = await measure('/api/read/r0', { method: 'GET' });
    // Zeilen aus der Antwort ableiten (das Parsen der grossen JSON ist Teil der Kosten).
    const rows = m.bodyText ? (JSON.parse(m.bodyText) as unknown[]).length : 0;
    return toRunResult('r0', r0Stage.label, 'read', rows, m,
      'volle Entities, ein JSON-Array');
  },
};

/** Alle registrierten Stufen. Reihenfolge = Anzeigereihenfolge im Dashboard. */
export const STAGES: Stage[] = [
  w0Stage,
  r0Stage,
  seedStage,
];
