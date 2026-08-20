# Aufgaben für Studenten

Diese Aufgaben führen durch das Projekt — vom Beobachten über das Verstehen bis zum
Erweitern. Sie sind in drei Stufen gegliedert:

- 🟢 **Einsteiger** — beobachten, messen, vorhersagen
- 🟡 **Fortgeschritten** — Parameter verändern, Ursachen erklären
- 🔴 **Profi** — Code erweitern, eigene Stufen bauen

> Arbeite immer nach dem Muster **Vorhersagen → Messen → Erklären**. Schreibe deine
> Vorhersage *auf*, bevor du misst — der Aha-Effekt entsteht bei den Überraschungen.

---

## 0. Vorbereitung

1. Starte Datenbank, Server und Client (siehe [README](../README.md)).
2. Seede einen Datenbestand: **100 000 Zeilen, Payload-Länge 32**.
3. Mach dich mit dem Dashboard vertraut: Was bedeuten die Spalten *Server ms*, *TTFB*,
   *Wire KB*, *Zeilen/s*, *MB/s*?

---

## 1. Write-Track 🟢

### 1.1 Vorhersagen
Bevor du misst: Ordne die Stufen W0–W6 nach erwartetem Durchsatz (langsamste zuerst).
Führe dann **▶ Alle Write** aus und vergleiche mit deiner Reihenfolge.

### 1.2 Der überraschende Sprung
- Um welchen Faktor ist **W2** schneller als **W1**? Warum bringt das bloße Weglassen des
  HTTP-Overheads (W0 → W1) so wenig, aber die gemeinsame Transaktion (W1 → W2) so viel?
- Stichwort: Was passiert bei einem `COMMIT` physisch auf der Platte?

### 1.3 Warum rohes JDBC ab W3? 🟡
- Lies `docs/W3.md`. Warum kann **Hibernate** die INSERTs bei `GenerationType.IDENTITY`
  nicht als Batch bündeln? Was müsste man an der Entity ändern, damit es ginge — und welchen
  Preis hätte das?

### 1.4 Warum ist COPY (W6) so viel schneller? 🟡
- Erkläre in zwei Sätzen, was `COPY ... FROM STDIN` anders macht als viele `INSERT`s.

---

## 2. Read-Track 🟢

### 2.1 Nutzlast schrumpfen
Führe R0, R1, R4, R7 aus (Wire KB vergleichen).
- Welchen Anteil spart **Projektion** (R0 → R1), welchen **Kompression** (R1 → R4)?
- Ist **CBOR** (R7) kleiner oder größer als **gzip-JSON** (R4)? Warum?

### 2.2 Zeit vs. Bytes
- R1 und R3 übertragen ähnlich viele Bytes. Warum ist die **TTFB** von R3 trotzdem ~10× kleiner?
- Für welche Art von Anwendung ist niedrige TTFB wichtiger als Gesamtzeit?

### 2.3 Pagination 🟡
- Führe R2 (Offset) und R2 (Keyset) aus. Warum wächst der Offset-Nachteil mit der Tiefe?
- **Denkaufgabe:** Nenne einen Anwendungsfall, in dem Offset-Pagination *nötig* ist und
  Keyset nicht genügt.

### 2.4 Caching 🟡
- Führe R5 aus. Erkläre die Notiz „kalt X ms → warm Y ms".
- **Falle:** Was liefert R5 zurück, wenn sich die Daten ändern, der Cache aber nicht geleert
  wird? Verifiziere im Code (`ReadService.categoryStats` + Test `r5CachesUntilEvict`).

---

## 3. Experimente (Parameter verändern) 🟡

Miss jeweils **vorher/nachher** und notiere den Effekt.

### 3.1 Batch-Größe
In `WriteService.BATCH_SIZE` (Default 1000) ändern auf 100, dann 5000. Baue neu
(`cit 21` bzw. `ci 21`), miss W3/W4. Wo liegt das Optimum, und warum ist „größer" nicht
immer besser?

### 3.2 Payload-Länge
Seede mit Payload-Länge 0, dann 256. Wie verändert sich der Unterschied zwischen **R0** und
**R1** (Projektion)? Warum?

### 3.3 Connection-Pool 🔴
Setze die Pool-Größe klein: starte den Server mit `HIKARI_MAX_POOL=2`
(`HIKARI_MAX_POOL=2 java -jar ...`). Führe **W7** (parallel) aus.
- Was passiert mit dem Durchsatz? Erkläre den Zusammenhang Pool-Größe ↔ Parallelität.

### 3.4 Lasttest 🔴
Führe `infra/load/read-load.js` mit k6 aus (siehe `infra/load/README.md`). Erhöhe die
VU-Zahl (20 → 50 → 100). Ab wann steigt die **p95-Latenz** stark? Vergleiche mit der
Pool-Größe.

---

## 4. Erweitern (Code schreiben) 🔴

### 4.1 Neue Read-Stufe: Filter-Projektion
Baue eine Stufe **R1b**, die nur Zeilen einer bestimmten `category` zurückgibt.
- Server: neue Methode in `ReadService` + Endpoint in `ReadController`.
- Client: neue `Stage` in `stages.ts`, in `STAGES` registrieren.
- Test: Integrationstest analog `ReadStagesIntegrationTest`.
- **Definition of Done:** Endpoint funktioniert · Test grün · im Dashboard sichtbar.

### 4.2 Neue Write-Stufe: `UPSERT`
Baue eine Stufe, die per `INSERT ... ON CONFLICT` schreibt (idempotenter Ingest).
Überlege: Funktioniert `reWriteBatchedInserts` damit noch? (Tipp: Doku W4 lesen.)

### 4.3 Metrik ergänzen
Erweitere das Dashboard um eine Spalte **„Bytes/Zeile"**. Wo im Client (`harness.ts` /
`ResultsChart.tsx`) musst du ansetzen?

---

## 5. Quiz (zum Abschluss)

1. Nenne die **zwei größten Hebel** im Write-Track und begründe die Reihenfolge.
2. Warum ist **reaktiv** (W8/R8) beim Bulk-Insert *nicht* schneller — und wann *ist* es die
   richtige Wahl?
3. Eine API liefert 50 MB JSON pro Request und ist „zu langsam". Nenne **drei** verschiedene
   Techniken aus diesem Projekt, um das zu verbessern, mit je einem Satz Trade-off.
4. Richtig oder falsch: „Ein größerer Connection-Pool ist immer besser." Begründe.
5. Was misst der Header `Server-Timing`, und warum reicht er bei der Streaming-Stufe R3
   nicht aus?

---

## 6. Abgabe / Reflexion

Schreibe eine halbe Seite zur Leitfrage des Projekts:

> **„Warum gibt es kein *schneller ist immer besser*?"**

Belege deine Antwort mit **zwei** konkreten Messungen aus diesem Projekt, bei denen die
„fortgeschrittenere" Technik *nicht* die schnellere war — und erkläre den Kontext, in dem
sie trotzdem die richtige Wahl wäre.

---

*Musterlösungen und Referenzzahlen: siehe die Stufen-Dokumente `docs/W0–W8.md` und
`docs/R0–R8.md` sowie die Integrationstests unter `server/src/test/`.*
