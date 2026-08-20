import { useState } from 'react';
import { STAGES } from '../stages';
import type { RunContext, RunResult, Stage } from '../types';

interface Props {
  ctx: RunContext;
  onResult: (result: RunResult) => void;
}

/**
 * Listet alle registrierten Stufen (nach Track gruppiert) und fuehrt sie aus — einzeln oder als ganzen
 * Track hintereinander („Alle Write/Read"). Jeder Lauf liefert ein RunResult ans Dashboard.
 */
export function StageRunner({ ctx, onResult }: Props) {
  const [runningId, setRunningId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function runStage(stage: Stage) {
    setRunningId(stage.id);
    setError(null);
    try {
      onResult(await stage.run(ctx));
    } catch (e) {
      setError(`${stage.label}: ${(e as Error).message}`);
    } finally {
      setRunningId(null);
    }
  }

  /** Fuehrt alle Stufen eines Tracks nacheinander aus (die Seed-Infrastruktur ausgenommen). */
  async function runTrack(track: 'read' | 'write') {
    setRunningId(`all-${track}`);
    setError(null);
    try {
      for (const stage of STAGES.filter((s) => s.track === track && s.id !== 'seed')) {
        try {
          onResult(await stage.run(ctx));
        } catch (e) {
          setError(`${stage.label}: ${(e as Error).message}`);
        }
      }
    } finally {
      setRunningId(null);
    }
  }

  const busy = runningId !== null;

  function renderStage(stage: Stage) {
    return (
      <li key={stage.id} className={`stage stage-${stage.track}`}>
        <div className="stage-head">
          <span className={`badge badge-${stage.track}`}>{stage.track === 'read' ? 'READ' : 'WRITE'}</span>
          <strong>{stage.label}</strong>
          <button disabled={busy} onClick={() => void runStage(stage)}>
            {runningId === stage.id ? 'laeuft…' : 'Run'}
          </button>
        </div>
        <p className="stage-desc">{stage.description}</p>
      </li>
    );
  }

  const writeStages = STAGES.filter((s) => s.track === 'write' && s.id !== 'seed');
  const readStages = STAGES.filter((s) => s.track === 'read');
  const seedStage = STAGES.find((s) => s.id === 'seed');

  return (
    <section className="panel">
      <h2>Stufen</h2>
      {error && <p className="error">{error}</p>}

      <div className="track-head">
        <h3>Write-Track</h3>
        <button disabled={busy} onClick={() => void runTrack('write')}>
          {runningId === 'all-write' ? 'laeuft…' : '▶ Alle Write ausfuehren'}
        </button>
      </div>
      <ul className="stage-list">{writeStages.map(renderStage)}</ul>

      <div className="track-head">
        <h3>Read-Track</h3>
        <button disabled={busy} onClick={() => void runTrack('read')}>
          {runningId === 'all-read' ? 'laeuft…' : '▶ Alle Read ausfuehren'}
        </button>
      </div>
      <ul className="stage-list">{readStages.map(renderStage)}</ul>

      {seedStage && (
        <>
          <div className="track-head">
            <h3>Infrastruktur</h3>
          </div>
          <ul className="stage-list">{renderStage(seedStage)}</ul>
        </>
      )}
    </section>
  );
}
