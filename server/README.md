# Server-Seite: Export im Ausfall-Dienst

Die App braucht **keine** Änderung an CoreScope. Ihre Daten kommen aus dem bestehenden
Ausfall-Dienst (`/opt/saarmesh-ausfall/ausfall.py` auf bold-wright, `saarmesh-ausfall.timer`,
alle 15 Minuten). Er schreibt neben `index.html` und `status.json` jetzt zusätzlich:

| Datei | URL | Inhalt |
|---|---|---|
| `repeater.json` | `https://live.saarmesh.de/ausfall/repeater.json` | alle bewerteten Repeater (≈ 180 KB, gzip ≈ 57 KB) |
| `verlauf/<pk>.json` | `https://live.saarmesh.de/ausfall/verlauf/<pk>.json` | Sichtungen je Stunde, 21 Tage (≈ 1,5 KB) |

Caddy liefert beides schon über das vorhandene `handle_path /ausfall/*` (file_server,
`Cache-Control: no-cache`, ETag, gzip). Die Caddy-Konfiguration bleibt unverändert.

> **Warum nicht `/ausfall/api/status.json`?** `/ausfall/api/*` leitet Caddy bereits an den
> Mail-Dienst `notify.py serve` (127.0.0.1:8098) weiter. Eine statische Datei unter diesem
> Pfad wäre nicht erreichbar. Daher liegt die Datei direkt unter `/ausfall/`.

Änderung: [`ausfall-repeater-json.patch`](ausfall-repeater-json.patch), Backup auf dem Server:
`ausfall.py.bak-20261116-055236-pre-appjson`. Der Export läuft in einem eigenen `try`, ein Fehler
dort blockiert weder die Seite noch Telegram- oder Mail-Alarme.

## Format `repeater.json` (schema 1)

```json
{
  "schema": 1,
  "generated": 1794809042,              // Unix-s, Bezugszeit für alle Bewertungen
  "generated_iso": "2026-11-16T06:04:02Z",
  "window_days": 21, "bucket_s": 300,
  "daily_from": 1792994642,             // Beginn des ersten 24-h-Fensters in "daily"
  "repeaters": [{
    "pk": "1f869d80…",                  // Public Key (64 hex, klein)
    "name": "SaarMesh.de RoofRPT",
    "status": "aktiv",                  // aktiv | ruhig | aus | weg | unbekannt | ersetzt | wenig
    "last": 1794808344,                 // letzte Sichtung (jede Sichtung, nicht nur Adverts)
    "via": "Saarlodri|🤖",              // Observer der letzten Sichtung
    "art": "weitergeleitet",            // weitergeleitet | Advert | eigenes Paket | als Observer
    "n": 5930,                          // Anzahl 5-min-Fenster mit Sichtung im Zeitraum
    "lat": 49.23091, "lon": 6.88944,    // null ohne Position
    "first_seen": 1776243848,
    "quiet": 10800, "down": 21600,      // eigene Schwellen in s (fehlen bei "wenig")
    "g50": 600, "g95": 1200,            // typische / seltene Pause in s
    "watching": 3,                      // Observer, die ihn hören müssten und gerade online sind
    "by_pk": "…", "by": "…",            // nur bei "ersetzt"
    "daily": [268, 265, …]              // 21 Werte, je rollierendes 24-h-Fenster, max. 288
  }]
}
```

Neue Felder sind abwärtskompatibel (die App ignoriert Unbekanntes). Inkompatible Änderungen
bekommen `schema: 2`.

## Datenschutz

Die Datei enthält nur, was die öffentliche Ausfall-Seite ohnehin zeigt, plus die Position aus
CoreScope (die ebenfalls öffentlich auf live.saarmesh.de steht). E-Mail-Abos, `states.json` und
`notify.db` bleiben privat.
