// ============================================================================
//  k6-Lasttest: Read-Endpoints unter Nebenlaeufigkeit (viele gleichzeitige Nutzer)
// ============================================================================
//  Simuliert typischen API-Verkehr eines Dashboards: kleine, haeufige Reads
//  (Keyset-Seite, gecachtes Aggregat, Parallel-Dashboard). So sieht man, wie
//  viele Requests/s (RPS) der Server unter Last schafft und wie die Latenz
//  (p95) mit steigender Nutzerzahl waechst.
//
//  Voraussetzung: Die DB ist geseedet (z. B. 100000 Zeilen). Siehe README.
//
//  Ausfuehren:
//    k6 run infra/load/read-load.js
//    k6 run -e BASE_URL=http://localhost:8080 infra/load/read-load.js
// ============================================================================

import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  // Nutzerzahl (VUs) rampt hoch, haelt, rampt runter.
  stages: [
    { duration: '15s', target: 20 },
    { duration: '30s', target: 20 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],       // < 1 % Fehler
    http_req_duration: ['p(95)<1000'],    // 95 % der Requests unter 1 s
  },
};

// Kleine, realistische API-Reads. (R0/R1 liefern die ganze Tabelle und wuerden
// vor allem die Bandbreite testen — hier geht es um RPS/Latenz unter Last.)
const endpoints = [
  () => `${BASE}/api/read/r2/keyset?afterId=${Math.floor(Math.random() * 90000)}&limit=100`,
  () => `${BASE}/api/read/r5`,
  () => `${BASE}/api/read/r6?parallel=true`,
];

export default function () {
  const url = endpoints[Math.floor(Math.random() * endpoints.length)]();
  const res = http.get(url);
  check(res, { 'status 200': (r) => r.status === 200 });
}
