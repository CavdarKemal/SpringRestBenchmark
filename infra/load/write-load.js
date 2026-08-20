// ============================================================================
//  k6-Lasttest: paralleler Ingest unter Nebenlaeufigkeit
// ============================================================================
//  Mehrere gleichzeitige „Nutzer" fuegen Batches ein. Wir nutzen bewusst den
//  Datengenerator-Endpoint mit clear=false (anhaengen), NICHT die W-Stufen:
//  Die W-Endpoints leeren die Tabelle vor jedem Lauf (truncate) und wuerden
//  sich unter Nebenlaeufigkeit gegenseitig ueberschreiben.
//
//  So sieht man, wie viele Zeilen/s der Server bei paralleler Last schafft und
//  wo der Connection-Pool zum Flaschenhals wird.
//
//  Ausfuehren:
//    k6 run infra/load/write-load.js
//    k6 run -e ROWS=2000 -e BASE_URL=http://localhost:8080 infra/load/write-load.js
// ============================================================================

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const ROWS = __ENV.ROWS || 1000;

// Eigene Metrik: wie viele Zeilen insgesamt eingefuegt wurden.
const rowsInserted = new Counter('rows_inserted');

export const options = {
  stages: [
    { duration: '15s', target: 10 },
    { duration: '30s', target: 10 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const res = http.post(`${BASE}/api/data/generate?rows=${ROWS}&clear=false`);
  const ok = check(res, { 'status 200': (r) => r.status === 200 });
  if (ok) {
    rowsInserted.add(Number(ROWS));
  }
}
