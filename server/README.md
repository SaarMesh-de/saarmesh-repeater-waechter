# Datenformat `repeater.json`

Die App liest die Repeater-Liste von live.saarmesh.de. Diese Seite beschreibt das Format für alle, die
die Daten selbst nutzen oder einen eigenen Server anbinden möchten.

| URL | Inhalt |
|---|---|
| `https://live.saarmesh.de/ausfall/repeater.json` | alle bewerteten Repeater, alle 15 Minuten neu (gzip ca. 60 KB) |
| `https://live.saarmesh.de/ausfall/verlauf/<pk>.json` | Sichtungen je Stunde über 21 Tage für einen Repeater |

Beide Dateien sind statisch und werden mit ETag ausgeliefert. Ein unveränderter Abruf kostet daher nur
eine kurze `304`-Antwort.

## `repeater.json` (schema 1)

```json
{
  "schema": 1,
  "generated": 1794809042,              // Unix-Sekunden, Bezugszeit für alle Bewertungen
  "generated_iso": "2026-11-16T06:04:02Z",
  "window_days": 21, "bucket_s": 300,
  "daily_from": 1792994642,             // Beginn des ersten 24-h-Fensters in "daily"
  "repeaters": [{
    "pk": "1f869d80…",                  // Public Key (64 Hex-Zeichen, klein)
    "name": "SaarMesh.de RoofRPT",
    "status": "aktiv",                  // aktiv | ruhig | aus | weg | unbekannt | ersetzt | wenig
    "last": 1794808344,                 // letzte Sichtung (jede Sichtung, nicht nur Adverts)
    "via": "…",                         // Empfangsstation der letzten Sichtung
    "art": "weitergeleitet",            // weitergeleitet | Advert | eigenes Paket | als Observer
    "n": 5930,                          // Anzahl 5-Minuten-Fenster mit Sichtung im Zeitraum
    "lat": 49.23091, "lon": 6.88944,    // null ohne Position
    "first_seen": 1776243848,
    "quiet": 10800, "down": 21600,      // Schwellen dieses Repeaters in Sekunden (fehlen bei "wenig")
    "g50": 600, "g95": 1200,            // typische / seltene Pause in Sekunden
    "watching": 3,                      // Empfangsstationen, die ihn hören müssten und online sind
    "by_pk": "…", "by": "…",            // nur bei "ersetzt": der neue Eintrag
    "daily": [268, 265, …]              // 21 Werte, je 24-h-Fenster, höchstens 288
  }]
}
```

## `verlauf/<pk>.json`

```json
{ "schema": 1, "pk": "1f869d80…", "from": 1792993965, "step": 3600, "hourly": [12, 11, 0, 9, …] }
```

`hourly` enthält 504 Werte (21 Tage × 24 Stunden). Jeder Wert ist die Anzahl der 5-Minuten-Fenster mit
mindestens einer Sichtung, höchstens 12.

## Kompatibilität

Neue Felder kommen abwärtskompatibel hinzu, die App ignoriert unbekannte Felder. Eine inkompatible
Änderung bekommt eine neue Schema-Nummer.

## Datenschutz

Die Dateien enthalten nur, was die öffentliche Seite [Repeater-Ausfälle](https://live.saarmesh.de/ausfall/)
und die Live-Karte ohnehin zeigen.
