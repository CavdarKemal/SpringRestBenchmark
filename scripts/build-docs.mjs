// ============================================================================
//  Fuehrt die Markdown-Dokumentation zu einem einzigen HTML-Handbuch zusammen.
//  Danach kann daraus per Browser (Edge/Chrome headless) ein PDF gedruckt werden:
//
//    npm install                 # im Ordner scripts/
//    npm run build               # erzeugt docs/handbuch.html
//    <Edge> --headless --print-to-pdf=docs/SpringRestBenchmark-Handbuch.pdf \
//           file:///.../docs/handbuch.html
//
//  (Der komplette Ablauf steht in scripts/README.md.)
// ============================================================================

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { marked } from 'marked';

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, '..');
const docs = join(root, 'docs');

// Reihenfolge der Dokumente im Handbuch.
const files = [
  { path: join(root, 'README.md'), title: 'Ueberblick & Setup' },
  { path: join(docs, 'README.md'), title: 'Optimierungs-Roadmap' },
  ...['W0', 'W1', 'W2', 'W3', 'W4', 'W5', 'W6', 'W7', 'W8'].map((s) => ({ path: join(docs, `${s}.md`), title: s })),
  ...['R0', 'R1', 'R2', 'R3', 'R4', 'R5', 'R6', 'R7', 'R8'].map((s) => ({ path: join(docs, `${s}.md`), title: s })),
  { path: join(docs, 'AUFGABEN.md'), title: 'Aufgaben fuer Studenten' },
];

/** Wandelt Cross-Referenzen auf .md-Dateien in reinen Text um (Links zeigen im PDF sonst ins Leere). */
function stripMdLinks(md) {
  return md.replace(/\[([^\]]+)\]\([^)]*\.md[^)]*\)/g, '$1');
}

const sections = files
  .map((f, i) => {
    const md = stripMdLinks(readFileSync(f.path, 'utf8'));
    const html = marked.parse(md);
    const cls = i === 0 ? 'doc' : 'doc pagebreak';
    return `<section class="${cls}">${html}</section>`;
  })
  .join('\n');

const toc = files.map((f) => `<li>${f.title}</li>`).join('');

const css = `
  * { box-sizing: border-box; }
  body { font-family: 'Segoe UI', system-ui, sans-serif; color: #1f2933; line-height: 1.5; font-size: 12px; margin: 0; }
  @page { size: A4; margin: 16mm; }
  .title { text-align: center; padding-top: 85mm; page-break-after: always; }
  .title h1 { font-size: 36px; margin: 0 0 6px; color: #1a3e8c; }
  .title .sub { color: #52606d; font-size: 15px; margin: 2px 0; }
  .pagebreak { page-break-before: always; }
  h1 { font-size: 22px; border-bottom: 2px solid #2563eb; padding-bottom: 4px; color: #1a3e8c; }
  h2 { font-size: 16px; margin-top: 18px; }
  h3 { font-size: 13.5px; }
  h1, h2, h3 { page-break-after: avoid; }
  p, li { orphans: 2; widows: 2; }
  code { background: #eef2f7; padding: 1px 4px; border-radius: 3px; font-size: 11px; }
  pre { background: #f5f7fa; border: 1px solid #d9dee7; border-radius: 6px; padding: 10px; overflow: auto;
        font-size: 10.5px; page-break-inside: avoid; }
  pre code { background: none; padding: 0; }
  table { border-collapse: collapse; width: 100%; font-size: 11px; page-break-inside: avoid; margin: 8px 0; }
  th, td { border: 1px solid #d9dee7; padding: 4px 7px; text-align: left; }
  th { background: #eef2f7; }
  blockquote { border-left: 4px solid #2563eb; margin: 10px 0; padding: 2px 12px; color: #3b4a5a; background: #f7f9fc; }
  a { color: #2563eb; text-decoration: none; }
  ul.toc { columns: 2; font-size: 13px; }
`;

const html = `<!doctype html>
<html lang="de">
<head><meta charset="utf-8"><title>SpringRestBenchmark — Handbuch</title><style>${css}</style></head>
<body>
  <section class="title">
    <h1>SpringRestBenchmark</h1>
    <p class="sub">Datendurchsatz Schritt für Schritt optimieren</p>
    <p class="sub">Handbuch — Client ⇄ Spring Boot 4.1 ⇄ PostgreSQL</p>
  </section>
  <section class="doc"><h1>Inhalt</h1><ul class="toc">${toc}</ul></section>
  ${sections}
</body>
</html>`;

const outPath = join(docs, 'handbuch.html');
writeFileSync(outPath, html, 'utf8');
console.log(`handbuch.html geschrieben (${(html.length / 1024).toFixed(0)} KB): ${outPath}`);
