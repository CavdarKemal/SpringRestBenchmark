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
 * Stellt die Laufergebnisse dar: ein Balkendiagramm (Durchsatz je Stufe, jeweils
 * der letzte Lauf) plus eine detaillierte Verlaufstabelle. So wird die
 * Verbesserungskurve ueber die Stufen unmittelbar sichtbar.
 */
export function ResultsChart({ results, onClear }: Props) {
  // Pro Stufe nur den juengsten Lauf fuer den Vergleichs-Balken verwenden.
  const latestByStage = new Map<string, RunResult>();
  for (const r of results) latestByStage.set(r.stageId, r);
  const chartData = Array.from(latestByStage.values()).map((r) => ({
    name: r.label,
    'Zeilen/s': Math.round(r.rowsPerSecond),
    'MB/s': Number(r.mbPerSecond.toFixed(2)),
  }));

  return (
    <section className="panel">
      <div className="panel-head">
        <h2>Ergebnisse</h2>
        <button className="danger" onClick={onClear} disabled={results.length === 0}>
          Zuruecksetzen
        </button>
      </div>

      {chartData.length === 0 ? (
        <p className="hint">Noch keine Laeufe. Fuehre oben eine Stufe aus.</p>
      ) : (
        <>
          <div className="chart">
            <ResponsiveContainer width="100%" height={280}>
              <BarChart data={chartData} margin={{ top: 8, right: 16, bottom: 8, left: 8 }}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="name" />
                <YAxis yAxisId="left" orientation="left" />
                <Tooltip formatter={(v: number) => fmt(v, 2)} />
                <Legend />
                <Bar yAxisId="left" dataKey="Zeilen/s" fill="#2563eb" />
              </BarChart>
            </ResponsiveContainer>
          </div>

          <table className="results-table">
            <thead>
              <tr>
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
