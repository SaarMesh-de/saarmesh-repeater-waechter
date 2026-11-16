# Architektur und Plan

## 1. Analyse der vorhandenen Datenquellen (Stand 16.11.2026)

| Quelle | Was sie liefert | Bewertung für die App |
|---|---|---|
| `/ausfall/` (`ausfall.py`, Timer alle 15 min) | „zuletzt gesehen“ über **alle** Sichtungen (aufgelöste Pfad-Hops, eigene Pakete, Observer-Rolle), Schwellen je Repeater aus Perzentilen der Pausen der letzten 21 Tage, Status `aktiv/ruhig/aus/weg/unbekannt/ersetzt/wenig`, Observer-Ausfall-Erkennung | **Hauptquelle.** Die App rechnet die Ausfall-Logik nicht nach, sondern übernimmt sie. |
| `/ausfall/status.json` | nur Zähler (`aus`, `ruhig`, `aktiv` …) | zu wenig für die App |
| `/opt/saarmesh-ausfall/states.json` | Zustand je Repeater | privat (nicht im Webroot), für den Mail-Dienst |
| `/ausfall/api/*` → `notify.py serve` (:8098) | `subscribe`, `manage-link`, `confirm`, `unsub` … (E-Mail-Abos mit Double-Opt-in) | Phase 2 (Paten-Modus) |
| CoreScope `/api/nodes`, `/api/nodes/<pk>` | Name, Key, Position, `last_seen`/`last_heard`, `battery_mv`, `temperature_c`, Relay-Zähler | Detail-/Telemetrie-Quelle für Phase 2 |
| CoreScope `/api/nodes/<pk>/analytics?days=N` | Aktivitäts-Zeitreihe, SNR-Trend, Observer-Abdeckung | nur ca. 3 Tage im Speicher (`partial: true`), daher nicht für den Verlauf |
| `/relevanz/`, `/clock/`, `/positionen/`, `/statistik/` | HTML-Seiten | Deeplinks aus der Detailansicht |

**Lücke:** Es gab keinen JSON-Endpunkt mit dem Status je Repeater. Ergänzt wurde deshalb im
Ausfall-Projekt (nicht in CoreScope) ein statischer Export, siehe [`server/README.md`](../server/README.md).
Der vorgeschlagene Pfad `/ausfall/api/status.json` hätte mit dem Mail-Dienst kollidiert, daher
`/ausfall/repeater.json` und `/ausfall/verlauf/<pk>.json`.

Hinweis am Rande: Der laufende CoreScope-Container nutzt das Image `corescope:fork-nodetel-v3.17.0-b1f8b67`,
nicht `fork-aca6d9c`. Daran wurde nichts geändert.

## 2. Datenmodell

```
Repeater         (Room: repeater)   1:1 aus repeater.json, Cache für Offline-Betrieb
Favorite         (Room: favorite)   pk, offlineAfterHours?, notify, wasDown?, label,
                                    batteryLevel?, batteryProfile?, batteryLowMv?, batteryCriticalMv?
Battery          (Room: battery)    pk, latestMv, latestTs, low/critical (Server), samples, fetchedAt
SightingHistory  (Room: verlauf)    pk, from, step=3600, hourly[504]
AppSettings      (DataStore)        serverUrl, pollMinutes, notificationsEnabled, lastGenerated, lastFetch
```

`RepeaterStatus` (App) = f(Server-Status, Stille bezogen auf `generated`, eigener Schwellwert):

| Server | ohne eigenen Schwellwert | mit eigenem Schwellwert X h |
|---|---|---|
| `aktiv` / `ruhig` | ONLINE ≤ quiet < ÜBERFÄLLIG ≤ down < OFFLINE | ONLINE ≤ min(quiet, X/2) < ÜBERFÄLLIG ≤ X < OFFLINE |
| `aus` / `weg` | OFFLINE | wie oben, nach Stille |
| `unbekannt` | UNBEKANNT (Observer fehlen) | wie oben, nach Stille |
| `wenig` | WENIG_DATEN | wie oben, Standardwerte 3 h / 6 h |
| `ersetzt` | ERSETZT | ERSETZT |

Bezugszeit ist `generated` (Erzeugungszeit der Serverdaten), nicht die Uhr des Telefons. Ohne
Netz altern zwischengespeicherte Daten also nicht in falsche Offline-Alarme hinein. Wie alt die
Daten sind, zeigt die Liste separat an („Datenstand vor …“).

**Statuswechsel** (`TransitionDetector`), gleiche Semantik wie die Telegram-Meldung im Ausfall-Dienst:
- erste Bewertung: nichts melden, nur merken
- nicht down → OFFLINE: „vermutlich ausgefallen“
- down → ONLINE/ÜBERFÄLLIG: „wieder da“
- UNBEKANNT/ERSETZT/WENIG_DATEN: nichts melden, gemerkten Zustand behalten
- Ändert man den Schwellwert, wird der Zustand zurückgesetzt (kein Alarm durch die Umstellung selbst)

## 3. App-Architektur

```
ui (Compose, M3)  ── ViewModels (Hilt) ── RepeaterRepository ──┬─ AusfallApi (Retrofit, nur GET)
  ListScreen                                                   ├─ Room (Cache)
  DetailScreen (Canvas-Diagramm, osmdroid)                     └─ SettingsRepository (DataStore)
  SettingsScreen
work: PollWorker (WorkManager, periodisch, Netz erforderlich) → Notifier, FavoritesWidget.updateAll
widget: FavoritesWidget (Glance) liest nur den Room-Cache
domain: StatusLogic, TransitionDetector, RegionResolver, AgoFormat  (reines Kotlin, unit-getestet)
```

- **Netz:** OkHttp mit 10-MB-HTTP-Cache. Caddy liefert ETag und gzip, ein unveränderter Abruf
  kostet nur ein `304`, ein geänderter ca. 57 KB. Bei 15 min sind das höchstens ca. 5 MB pro Tag.
- **Nur lesen:** Die App hat ausschließlich `INTERNET`, `ACCESS_NETWORK_STATE` und `POST_NOTIFICATIONS`.
  Kein Bluetooth, kein USB, keine Companion-Anbindung, kein schreibender Endpunkt. Damit kann sie
  nichts ins Mesh senden.
- **Region:** grobe Heuristik (Namenspräfix `DE-SL`/`DE-RP`/`LX`/`FR…`, PLZ im Namen, sonst
  Bounding-Box der Position). Gedacht als Filter, nicht als Wahrheit.
- **Sprache:** alle Texte in `res/values/strings.xml` (Deutsch als Standard). Französisch: Datei nach
  `values-fr/` kopieren und übersetzen, dann `fr` in `res/xml/locales_config.xml` freischalten.

## 4. Push-Strategie

**MVP:** WorkManager-Polling (Standard 15 min, einstellbar 15/30/60/120/360 min), ohne Firebase
und ohne Google-Abhängigkeit. Der Ausfall-Dienst rechnet ohnehin nur alle 15 min, schneller
abzufragen bringt also nichts. Android kann Hintergrundarbeit in Doze oder bei Akkuoptimierung
verzögern. Das ist für Ausfälle im Stundenbereich vertretbar.

**Phase 2, Bewertung echtes Server-Push:**

| | ntfy (self-hosted auf bold-wright) | FCM |
|---|---|---|
| Google-Abhängigkeit | keine (auch auf GrapheneOS/LineageOS ohne GMS) | Google Play Services Pflicht |
| Betrieb | ein kleiner Go-Dienst plus Caddy-Route, Topic je Repeater, z. B. `ausfall-<pk8>` | Firebase-Projekt, Service-Account-Key auf dem Server |
| Zustellung | eigene Dauerverbindung oder **UnifiedPush** (ntfy-App als Distributor), dann ohne eigenen Dauerdienst in unserer App | sehr zuverlässig, akkuschonend |
| Datenschutz | alles auf eigenem Server | Metadaten über Google |
| Aufwand im Ausfall-Dienst | `notify.py alert` schickt zusätzlich `POST https://live.saarmesh.de/ntfy/ausfall-<pk8>` | HTTP-v1-API mit OAuth |

**Empfehlung: ntfy plus UnifiedPush.** Das passt zur Linie „ohne Google“, nutzt die vorhandene
Erkennung in `notify.py` 1:1 (dort sitzt der Zustandswechsel schon) und lässt sich in der App als
zweite `PushSource` neben dem Polling einhängen. Polling bleibt als Fallback und für das Widget.
Die Topics sind öffentlich lesbar, das ist unkritisch, denn sie verraten nur, was die Ausfall-Seite
ohnehin zeigt. Schreibrechte auf Topics nur für den Ausfall-Dienst (ntfy `auth-default-access: read-only`).

## 5. Akku-Telemetrie (v0.2.0)

**Datenweg:** Repeater → Abfrage-Station eines Betreibers (Telemetrie-Abfrage im Mesh, z. B. per
Script über den meshcore-mux) → MQTT → CoreScope-Fork (`node_metrics`) →
`GET /api/nodes/<pk>/battery?days=N` → App. Die App fragt **nie** selbst im Mesh ab.

- CoreScope liefert `latest_mv`, `latest_ts`, `samples[]` und `thresholds` (`low_mv` 3300, `critical_mv` 3000).
  Ohne Werte: `samples: []`, `status: "unknown"`, die Karte bleibt dann aus. Unbekannter Knoten: 404.
- Die Antwort ist `no-store`, deshalb drosselt die App selbst: höchstens 1 Abruf pro Stunde je
  Favorit im Hintergrund, beim Öffnen der Detailansicht höchstens alle 10 Minuten.
- **Stufen:** OK > schwach (≤ low) > kritisch (≤ critical), mit 100 mV Hysterese beim Erholen.
- **Benachrichtigung** nur bei Verschlechterung (OK→schwach, →kritisch). Die Erholung wird still
  gemerkt. Werte älter als 48 h lösen nichts aus.
- **Schwellen pro Favorit (v0.3.0):** Akku-Typ Server / Li-Ion (3500/3300 mV) / LiFePO4 (3100/2900 mV)
  / eigene Werte. Ungültige eigene Werte (kritisch ≥ schwach, außerhalb 2–15 V) fallen auf den Server
  zurück. Ein Wechsel setzt die gemerkte Stufe zurück, damit die Umstellung selbst keinen Alarm auslöst.
  Plausibilität: Liegt der Messwert mehr als 100 mV über dem Vollladewert des Typs (Li-Ion 4,25 V,
  LiFePO4 3,65 V), zeigt die App einen Hinweis.
- **Inhero MR2 (v0.3.2):** Die MR2-Firmware gibt in `getBattMilliVolts()` nicht die Zellspannung zurück,
  sondern den per Coulomb-Zähler bestimmten Ladestand, umgerechnet über eine Li-Ion-OCV-Tabelle
  (0 % = 3000 mV … 100 % = 4200 mV, `variants/inhero_mr2/helpers/BatteryOcvMapping.cpp` im Inhero-Fork).
  Status und Telemetrie-Kanal 1 enthalten daher diesen Ersatzwert, und er landet so auch in CoreScope.
  Die echte Akkuspannung, den Ladestand, den Strom und die Solarwerte liefert das Board zusätzlich auf
  einem eigenen LPP-Kanal (nächster freier Kanal, meist 2) bzw. per CLI `get board.telem`. Die App
  rechnet den Ersatzwert mit derselben Tabelle in Prozent zurück (Typ „Inhero MR2 (Ladestand)“).
  Sauberer wäre es, wenn die Abfrage-Station für MR2-Boards den Board-Kanal ausliest.
- **Nicht bewertbar:** Ist ein Messwert für den gewählten Typ unplausibel, gibt es keine Ampelfarbe
  und keine Warnungen. Die gemerkte Stufe wird zurückgesetzt.
- **Trend:** lineare Regression über 7 Tage, angezeigt ab 6 Werten und 24 h Zeitspanne.
- Stand 16.11.2026: Werte gibt es nur für die Repeater, die das Script des Betreibers abfragt (5 von 871).
  Mehr Abdeckung bringen verteilte Abfrage-Stationen. Jede fragt nur die Repeater in ihrer
  Reichweite ab, höchstens stündlich, abgestimmt über eine gemeinsame Liste, damit kein Repeater
  doppelt abgefragt wird. Jede Station hat einen eigenen MQTT-Login.

## 6. Später
- **Paten-Modus:** Die vorhandenen Endpunkte `POST /ausfall/api/subscribe {email, pk, consent}`
  und `manage-link` können aus der Detailansicht genutzt werden („Per E-Mail benachrichtigen“).
  Double-Opt-in bleibt beim Server. Das ist der einzige schreibende Aufruf, den die App je machen
  würde, und er geht an den Mail-Dienst, nicht ins Mesh.
- **Server-Push:** siehe oben (ntfy/UnifiedPush).
