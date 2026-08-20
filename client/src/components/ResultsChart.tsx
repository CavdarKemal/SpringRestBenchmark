import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import type { RunResult } from '../types';

interface Props {
  results: RunResult[];
  onClear: () => void;
}

/** Formatiert Zahlen kompakt mit deutschem Tausenderpunkt. */
function fmt(n: number, digits = 0): string {
  return n.toLocaleString('de-DE', { maximumFractionDigits: digits });
}

/**
 * Vergleichs-Dashboard: zeigt pro Stufe den jeweils juengsten Lauf.
 *
 * <ul>
 *   <li>Write-Track: Durchsatz in Zeilen/s (der aussagekraeftige Ingest-Wert).</li>
 *   <li>Read-Track: Nutzlast auf der Leitung in „Wire KB" (zeigt Projektion/Kompression/Binaerformat).</li>
 *   <li>Darunter die vollstaendige Detailtabelle mit allen Metriken.</li>
 * </ul>
 */
export function ResultsChart({ results, onClear }: Props) {
  // Pro Stufe nur den juengsten Lauf.
  const latestByStage = new Map<string, RunResult>();
  for (const r of results) latestByStage.set(r.stageId, r);
  const latest = Array.from(latestByStage.values());

  const writeData = latest
    .filter((r) => r.track === 'write')
    .map((r) => ({ name: r.label, 'Zeilen/s': Math.round(r.rowsPerSecond) }));
  const readData = latest
    .filter((r) => r.track === 'read')
    .map((r) => ({ name: r.label, 'Wire KB': Number((r.wireBytes / 1024).toFixed(1)) }));

  return (
    <section className="panel">
      <div className="panel-head">
        <h2>Vergleichs-Dashboard</h2>
        <button className="danger" onClick={onClear} disabled={results.length === 0}>
          Zuruecksetzen
        </button>
      </div>

      {latest.length === 0 ? (
        <p className="hint">Noch keine Laeufe. Fuehre oben einzelne Stufen oder einen ganzen Track aus.</p>
      ) : (
        <>
          {writeData.length > 0 && (
            <>
              <h3>Write-Durchsatz (Zeilen/s)</h3>
              <div className="chart">
                <ResponsiveContainer width="100%" height={240}>
                  <BarChart data={writeData} margin={{ top: 8, right: 16, bottom: 8, left: 8 }}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="name" hide={writeData.length > 6} />
                    <YAxis />
                    <Tooltip formatter={(v: number) => fmt(v)} />
                    <Legend />
                    <Bar dataKey="Zeilen/s" fill="#2563eb" />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </>
          )}

          {readData.length > 0 && (
            <>
              <h3>Read-Nutzlast auf der Leitung (Wire KB, kleiner = besser)</h3>
              <div className="chart">
                <ResponsiveContainer width="100%" height={240}>
                  <BarChart data={readData} margin={{ top: 8, right: 16, bottom: 8, left: 8 }}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="name" hide={readData.length > 6} />
                    <YAxis />
                    <Tooltip formatter={(v: number) => fmt(v, 1)} />
                    <Legend />
                    <Bar dataKey="Wire KB" fill="#0e9f6e" />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </>
          )}

          <table className="results-table">
            <thead>
              <tr>
                <th>Track</th>
                <th>Stufe</th>
                <th>Zeilen</th>
                <th>Server ms</th>
                <th>Gesamt ms</th>
                <th>TTFB ms</th>
                <th>Wire KB</th>
                <th>Zeilen/s</th>
                <th>MB/s</th>
              </tr>
            </thead>
            <tbody>
              {[...results].reverse().map((r, i) => (
                <tr key={i}>
                  <td>
                    <span className={`badge badge-${r.track}`}>{r.track === 'read' ? 'R' : 'W'}</span>
                  </td>
                  <td>{r.label}</td>
                  <td>{fmt(r.rows)}</td>
                  <td>{fmt(r.serverMillis, 1)}</td>
                  <td>{fmt(r.totalMillis, 1)}</td>
                  <td>{fmt(r.ttfbMillis, 1)}</td>
                  <td>{fmt(r.wireBytes / 1024, 1)}</td>
                  <td>{fmt(r.rowsPerSecond)}</td>
                  <td>{fmt(r.mbPerSecond, 2)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </section>
  );
}
