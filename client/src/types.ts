// Gemeinsame Typen fuer den Benchmark-Client.

/** Ergebnis eines einzelnen Benchmark-Laufs — Grundlage aller Diagramme. */
export interface RunResult {
  stageId: string;
  label: string;
  track: 'read' | 'write';
  rows: number;
  /** Vom Client gelesene (ggf. dekomprimierte) Body-Groesse in Bytes. */
  bytes: number;
  /** Tatsaechlich uebertragene Bytes auf der Leitung (aus 'X-Wire-Bytes'); bei R4 << bytes. */
  wireBytes: number;
  /** Gesamtzeit clientseitig (Wanduhr): Request abschicken bis Antwort vollstaendig. */
  totalMillis: number;
  /** Time-To-First-Byte: bis das erste Byte der Antwort eintrifft (wichtig bei Streaming). */
  ttfbMillis: number;
  /** Reine Server-/DB-Zeit aus dem 'Server-Timing'-Header bzw. dem Antwort-Envelope. */
  serverMillis: number;
  rowsPerSecond: number;
  mbPerSecond: number;
  note?: string;
  timestamp: number;
}

/** Parameter, die eine Stufe bei der Ausfuehrung erhaelt. */
export interface RunContext {
  /** Angepeilte Zeilenzahl fuer den Lauf (Seed-Groesse bzw. Abfrage-Limit). */
  rows: number;
  /** Laenge des Text-Payloads je Zeile (0 = keiner). */
  payloadLength: number;
}

/** Eine Optimierungsstufe (z. B. W0, R3). Registriert in stages.ts. */
export interface Stage {
  id: string;
  label: string;
  track: 'read' | 'write';
  /** Kurzbeschreibung fuer das Info-Panel (ausfuehrliche Doku liegt unter /docs). */
  description: string;
  run: (ctx: RunContext) => Promise<RunResult>;
}
