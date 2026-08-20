import { useEffect, useState } from 'react';
import { clearData, fetchCount } from '../api';
import type { RunContext } from '../types';

interface Props {
  ctx: RunContext;
  onChange: (ctx: RunContext) => void;
  /** Signal-Zaehler: aendert sich nach jedem Lauf, damit die Zeilenanzahl neu geladen wird. */
  refreshSignal: number;
}

/**
 * Steuert die globalen Lauf-Parameter (Zeilenzahl, Payload-Groesse) und den
 * Datenbestand (Anzahl anzeigen, leeren). Diese Parameter gelten fuer alle Stufen,
 * damit Vergleiche fair bleiben.
 */
export function DataControls({ ctx, onChange, refreshSignal }: Props) {
  const [count, setCount] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);

  async function reloadCount() {
    try {
      setCount(await fetchCount());
    } catch {
      setCount(null);
    }
  }

  useEffect(() => {
    void reloadCount();
  }, [refreshSignal]);

  return (
    <section className="panel">
      <h2>Datenbestand & Lauf-Parameter</h2>
      <div className="controls">
        <label>
          Zeilen
          <input
            type="number"
            min={1}
            step={10000}
            value={ctx.rows}
            onChange={(e) => onChange({ ...ctx, rows: Number(e.target.value) })}
          />
        </label>
        <label>
          Payload-Laenge
          <input
            type="number"
            min={0}
            step={16}
            value={ctx.payloadLength}
            onChange={(e) => onChange({ ...ctx, payloadLength: Number(e.target.value) })}
          />
        </label>
        <button onClick={() => void reloadCount()} disabled={busy}>
          Anzahl aktualisieren
        </button>
        <button
          className="danger"
          disabled={busy}
          onClick={async () => {
            setBusy(true);
            try {
              await clearData();
              await reloadCount();
            } finally {
              setBusy(false);
            }
          }}
        >
          Tabelle leeren
        </button>
      </div>
      <p className="count">
        Zeilen in DB: <strong>{count === null ? '—' : count.toLocaleString('de-DE')}</strong>
      </p>
    </section>
  );
}
