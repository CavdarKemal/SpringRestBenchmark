import { useState } from 'react';
import { STAGES } from '../stages';
import type { RunContext, RunResult, Stage } from '../types';

interface Props {
  ctx: RunContext;
  onResult: (result: RunResult) => void;
}

/**
 * Listet alle registrierten Stufen und fuehrt sie auf Knopfdruck aus. Jeder Lauf
 * liefert ein RunResult zurueck, das im Dashboard als Balken/Zeile erscheint.
 */
export function StageRunner({ ctx, onResult }: Props) {
  const [runningId, setRunningId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function runStage(stage: Stage) {
    setRunningId(stage.id);
    setError(null);
    try {
      const result = await stage.run(ctx);
      onResult(result);
    } catch (e) {
      setError(`${stage.label}: ${(e as Error).message}`);
    } finally {
      setRunningId(null);
    }
  }

  return (
    <section className="panel">
      <h2>Stufen</h2>
      {error && <p className="error">{error}</p>}
      <ul className="stage-list">
        {STAGES.map((stage) => (
          <li key={stage.id} className={`stage stage-${stage.track}`}>
            <div className="stage-head">
              <span className={`badge badge-${stage.track}`}>{stage.track === 'read' ? 'READ' : 'WRITE'}</span>
              <strong>{stage.label}</strong>
              <button disabled={runningId !== null} onClick={() => void runStage(stage)}>
                {runningId === stage.id ? 'laeuft…' : 'Run'}
              </button>
            </div>
            <p className="stage-desc">{stage.description}</p>
          </li>
        ))}
      </ul>
    </section>
  );
}
