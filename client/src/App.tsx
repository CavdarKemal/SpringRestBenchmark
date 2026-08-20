import { useState } from 'react';
import { DataControls } from './components/DataControls';
import { DataViewer } from './components/DataViewer';
import { ResultsChart } from './components/ResultsChart';
import { StageRunner } from './components/StageRunner';
import type { RunContext, RunResult } from './types';

/**
 * Wurzel-Komponente des Lehr-Clients.
 *
 * <p>Haelt die globalen Lauf-Parameter (damit alle Stufen fair unter gleichen
 * Bedingungen laufen) sowie die Liste der Ergebnisse. Die drei Panels darunter
 * teilen sich diesen Zustand.</p>
 */
export default function App() {
  const [ctx, setCtx] = useState<RunContext>({ rows: 100_000, payloadLength: 0 });
  const [results, setResults] = useState<RunResult[]>([]);
  const [refreshSignal, setRefreshSignal] = useState(0);

  function handleResult(result: RunResult) {
    setResults((prev) => [...prev, result]);
    // Nach einem (potentiell schreibenden) Lauf die Zeilenanzahl neu laden.
    setRefreshSignal((n) => n + 1);
  }

  return (
    <div className="app">
      <header>
        <h1>SpringRestBenchmark</h1>
        <p className="subtitle">Datendurchsatz Schritt fuer Schritt optimieren — Client ⇄ Spring Boot ⇄ PostgreSQL</p>
      </header>

      <DataControls ctx={ctx} onChange={setCtx} refreshSignal={refreshSignal} />
      <StageRunner ctx={ctx} onResult={handleResult} />
      <ResultsChart results={results} onClear={() => setResults([])} />
      <DataViewer />

      <footer>
        <p>
          Serverzeit stammt aus dem <code>Server-Timing</code>-Header bzw. dem Antwort-Envelope.
          Die Bytes werden clientseitig aus dem Antwort-Stream gezaehlt.
        </p>
      </footer>
    </div>
  );
}
