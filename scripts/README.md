# Docs → PDF-Handbuch

Führt die Markdown-Dokumentation (`README.md`, `docs/README.md`, `docs/W0–W8.md`,
`docs/R0–R8.md`, `docs/AUFGABEN.md`) zu **einem** durchsuchbaren Dokument zusammen:

- `docs/handbuch.html` — selbstständige HTML-Version (in jedem Browser öffenbar)
- `docs/SpringRestBenchmark-Handbuch.pdf` — daraus gedrucktes PDF

## Neu erzeugen

```bash
# 1) HTML-Handbuch bauen (im Ordner scripts/)
npm install
npm run build           # -> docs/handbuch.html

# 2) HTML -> PDF via Edge/Chrome headless (Beispiel Windows/Edge)
"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" \
  --headless --disable-gpu --no-pdf-header-footer \
  --print-to-pdf="docs\SpringRestBenchmark-Handbuch.pdf" \
  "file:///<ABSOLUTER-PFAD>/docs/handbuch.html"
```

Statt Edge funktioniert genauso **Google Chrome** (gleiche `--headless --print-to-pdf`-Flags).
Alternativ: `docs/handbuch.html` im Browser öffnen und **Drucken → Als PDF speichern**.

## Warum dieser Weg?

Der Browser (Chromium) rendert Umlaute, Emojis, Box-Zeichnungen und Tabellen sauber — anders
als LaTeX-basierte Konverter, die an Emojis/Sonderzeichen scheitern. Es wird kein zusätzliches
Werkzeug außer einem vorhandenen Browser benötigt.

Reihenfolge und Layout steuert `build-docs.mjs` (CSS mit Seitenumbrüchen je Kapitel,
`page-break-inside: avoid` für Tabellen/Codeblöcke).
