// Kleine API-Helfer fuer die Verwaltungs-Endpoints (kein Benchmark, nur Infrastruktur).

/** Liefert die aktuelle Zeilenanzahl der measurements-Tabelle. */
export async function fetchCount(): Promise<number> {
  const resp = await fetch('/api/data/count');
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
  const json = (await resp.json()) as { count: number };
  return json.count;
}

/** Leert die measurements-Tabelle. */
export async function clearData(): Promise<void> {
  const resp = await fetch('/api/data', { method: 'DELETE' });
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
}
