import { useRef, useState } from 'react';
import { useVirtualizer } from '@tanstack/react-virtual';

/** Struktur einer Projektionszeile (entspricht MeasurementDto). */
interface Row {
  id: number;
  ts: string;
  sensorId: number;
  category: string;
  v1: number;
}

/**
 * Demonstriert clientseitige Rendering-Optimierung:
 *
 * <ul>
 *   <li><b>Virtualisiert:</b> Es sind immer nur die sichtbaren Zeilen im DOM. Dadurch bleibt die Liste auch
 *       bei 100 000+ Zeilen fluessig — ein naives {@code rows.map()} wuerde den Browser lahmlegen.</li>
 *   <li><b>Streaming-Render:</b> Die Zeilen erscheinen inkrementell, waehrend sie ueber den NDJSON-Stream
 *       (R3) eintreffen — der Nutzer sieht sofort etwas, statt auf die komplette Antwort zu warten.</li>
 * </ul>
 */
export function DataViewer() {
  const [rows, setRows] = useState<Row[]>([]);
  const [status, setStatus] = useState('');
  const [busy, setBusy] = useState(false);
  const parentRef = useRef<HTMLDivElement>(null);

  const virtualizer = useVirtualizer({
    count: rows.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => 26,
    overscan: 12,
  });

  /** Laedt die ganze Projektion (R1) und rendert sie virtualisiert. */
  async function loadVirtualized() {
    setBusy(true);
    setStatus('lade R1 …');
    setRows([]);
    try {
      const t0 = performance.now();
      const resp = await fetch('/api/read/r1');
      const data = (await resp.json()) as Row[];
      setRows(data);
      const ms = Math.round(performance.now() - t0);
      setStatus(`${data.length.toLocaleString('de-DE')} Zeilen geladen in ${ms} ms — virtualisiert gerendert`);
    } finally {
      setBusy(false);
    }
  }

  /** Konsumiert den NDJSON-Stream (R3) und fuegt die Zeilen inkrementell ein. */
  async function streamIncremental() {
    setBusy(true);
    setStatus('streame R3 …');
    setRows([]);
    try {
      const t0 = performance.now();
      const resp = await fetch('/api/read/r3');
      const reader = resp.body!.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      let firstAt = 0;
      let lastFlush = 0;
      const acc: Row[] = [];
      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        if (firstAt === 0) firstAt = performance.now();
        buffer += decoder.decode(value, { stream: true });
        let nl = buffer.indexOf('\n');
        while (nl >= 0) {
          const line = buffer.slice(0, nl);
          buffer = buffer.slice(nl + 1);
          if (line) acc.push(JSON.parse(line) as Row);
          nl = buffer.indexOf('\n');
        }
        // Nur alle ~2000 Zeilen neu rendern, um das Kopieren des Arrays zu begrenzen.
        if (acc.length - lastFlush >= 2000) {
          lastFlush = acc.length;
          setRows(acc.slice());
          setStatus(`streaming … ${acc.length.toLocaleString('de-DE')} Zeilen (TTFB ${Math.round(firstAt - t0)} ms)`);
        }
      }
      setRows(acc.slice());
      const ttfb = Math.round(firstAt - t0);
      const total = Math.round(performance.now() - t0);
      setStatus(`${acc.length.toLocaleString('de-DE')} Zeilen gestreamt — TTFB ${ttfb} ms, gesamt ${total} ms`);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="panel">
      <h2>Client-Rendering: virtualisierte Liste</h2>
      <p className="stage-desc">
        Rendert die Projektion (R1) in einer virtualisierten Liste — nur die sichtbaren Zeilen sind im DOM,
        deshalb bleibt auch bei 100 000+ Zeilen alles fluessig. „Streamen" fuegt die Zeilen inkrementell ein,
        waehrend sie ueber den NDJSON-Stream (R3) eintreffen.
      </p>
      <div className="controls">
        <button onClick={() => void loadVirtualized()} disabled={busy}>
          Laden (virtualisiert)
        </button>
        <button onClick={() => void streamIncremental()} disabled={busy}>
          Streamen (inkrementell)
        </button>
      </div>
      <p className="count">{status || 'Noch nichts geladen. Vorher „Seed" ausfuehren.'}</p>
      <div ref={parentRef} className="viewer">
        <div style={{ height: virtualizer.getTotalSize(), position: 'relative' }}>
          {virtualizer.getVirtualItems().map((vi) => {
            const r = rows[vi.index];
            return (
              <div
                key={vi.key}
                className="viewer-row"
                style={{
                  position: 'absolute',
                  top: 0,
                  left: 0,
                  width: '100%',
                  height: vi.size,
                  transform: `translateY(${vi.start}px)`,
                }}
              >
                <span className="c-id">#{r.id}</span>
                <span className="c-cat">{r.category}</span>
                <span className="c-sensor">S{r.sensorId}</span>
                <span className="c-v1">{r.v1.toFixed(4)}</span>
                <span className="c-ts">{r.ts}</span>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
