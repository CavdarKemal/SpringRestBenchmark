// Hilfsfunktionen zum Erzeugen einzelner Mess-Zeilen im Client.
//
// Fuer die Write-Stufen schickt der Client die Daten selbst. Damit der Fokus auf der
// Uebertragungs-/Insert-Technik bleibt (nicht auf der Datengenerierung), sind die
// Werte simpel zufaellig.

const CATEGORIES = ['TEMP', 'PRESSURE', 'HUMIDITY', 'VIBRATION', 'FLOW'];

/** Struktur entspricht dem serverseitigen MeasurementRequest. */
export interface MeasurementRequest {
  ts: string | null;
  sensorId: number;
  category: string;
  v1: number; v2: number; v3: number; v4: number;
  v5: number; v6: number; v7: number; v8: number;
  payload: string | null;
}

/** Erzeugt eine zufaellige Zeile mit optionalem Text-Payload gegebener Laenge. */
export function makeRow(payloadLength: number): MeasurementRequest {
  return {
    ts: null, // Server setzt die aktuelle Zeit
    sensorId: Math.floor(Math.random() * 500),
    category: CATEGORIES[Math.floor(Math.random() * CATEGORIES.length)],
    v1: Math.random(), v2: Math.random(), v3: Math.random(), v4: Math.random(),
    v5: Math.random(), v6: Math.random(), v7: Math.random(), v8: Math.random(),
    payload: payloadLength > 0 ? 'x'.repeat(payloadLength) : null,
  };
}
