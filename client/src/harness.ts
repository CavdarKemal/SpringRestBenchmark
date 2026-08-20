// ============================================================================
//  Mess-Harness (clientseitig)
// ============================================================================
//  Kapselt die eigentliche Zeit- und Byte-Messung eines HTTP-Aufrufs. Das ist
//  das Herz des Lehrprojekts: Nur wenn wir sauber messen, wird der Effekt einer
//  Optimierung sichtbar.
//
//  Gemessen werden:
//    - totalMillis : Wanduhr vom Absenden bis zur vollstaendig gelesenen Antwort
//    - ttfbMillis  : bis zum ersten empfangenen Byte (bei Streaming aussagekraeftig)
//    - bytes       : tatsaechlich uebertragene Body-Groesse (Nutzlast auf der Leitung)
//    - serverMillis: reine Serverzeit aus dem 'Server-Timing'-Header
// ============================================================================

import type { RunResult } from './types';

/** Liest die Serverzeit (in ms) aus einem 'Server-Timing: app;dur=..'-Header. */
export function parseServerTiming(header: string | null): number {
  if (!header) return 0;
  const match = header.match(/dur=([0-9.]+)/);
  return match ? parseFloat(match[1]) : 0;
}

/** Rohergebnis einer Messung, bevor daraus ein RunResult gebaut wird. */
export interface Measurement {
  bytes: number;
  totalMillis: number;
  ttfbMillis: number;
  serverMillis: number;
  /** Der vollstaendig gelesene Antwort-Text (fuer JSON-Auswertung durch die Stufe). */
  bodyText: string;
}

/**
 * Fuehrt einen HTTP-Aufruf aus und misst dabei Zeit und Bytes.
 *
 * <p>Der Body wird bewusst ueber den Stream-Reader gelesen, damit wir den
 * Zeitpunkt des ersten Bytes (TTFB) und die exakte Byte-Zahl bestimmen koennen.</p>
 */
export async function measure(url: string, init?: RequestInit): Promise<Measurement> {
  const t0 = performance.now();
  const response = await fetch(url, init);
  if (!response.ok) {
    throw new Error(`HTTP ${response.status} bei ${url}`);
  }

  const serverMillis = parseServerTiming(response.headers.get('Server-Timing'));

  let bytes = 0;
  let firstByteAt = 0;
  const chunks: Uint8Array[] = [];

  if (response.body) {
    const reader = response.body.getReader();
    // Wir lesen den Stream Chunk fuer Chunk und stoppen die Zeit beim ersten Chunk.
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      if (firstByteAt === 0) firstByteAt = performance.now();
      bytes += value.byteLength;
      chunks.push(value);
    }
  }

  const t1 = performance.now();
  const totalMillis = t1 - t0;
  const ttfbMillis = firstByteAt > 0 ? firstByteAt - t0 : totalMillis;

  // Chunks zu Text zusammenfuehren (fuer die JSON-Auswertung der Stufe).
  const merged = new Uint8Array(bytes);
  let offset = 0;
  for (const c of chunks) {
    merged.set(c, offset);
    offset += c.byteLength;
  }
  const bodyText = new TextDecoder().decode(merged);

  return { bytes, totalMillis, ttfbMillis, serverMillis, bodyText };
}

/** Baut aus einer Messung und Metadaten das finale, anzeigefertige RunResult. */
export function toRunResult(
  stageId: string,
  label: string,
  track: 'read' | 'write',
  rows: number,
  m: Measurement,
  note?: string,
): RunResult {
  const seconds = m.serverMillis > 0 ? m.serverMillis / 1000 : m.totalMillis / 1000;
  const rowsPerSecond = seconds > 0 ? rows / seconds : 0;
  const mbPerSecond = seconds > 0 ? m.bytes / (1024 * 1024) / seconds : 0;
  return {
    stageId,
    label,
    track,
    rows,
    bytes: m.bytes,
    totalMillis: m.totalMillis,
    ttfbMillis: m.ttfbMillis,
    serverMillis: m.serverMillis,
    rowsPerSecond,
    mbPerSecond,
    note,
    timestamp: Date.now(),
  };
}
