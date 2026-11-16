# SaarMesh Repeater-Wächter

Android-App zur Überwachung der Repeater im **SaarMesh**-Netz (MeshCore, LoRa 869,618 MHz,
Region SaarLorLux): Statusübersicht, Homescreen-Widget und Benachrichtigung bei Ausfall.

Die App **liest nur**. Sie sendet niemals etwas ins Mesh und ändert keine Repeater-Einstellungen.
Ihre Daten kommen aus dem öffentlichen Ausfall-Dienst auf [live.saarmesh.de/ausfall/](https://live.saarmesh.de/ausfall/).

## Screenshots

| Liste | Detail | Widget |
|---|---|---|
| _folgt_ | _folgt_ | _folgt_ |

## Funktionen (Phase 1)

- **Repeater-Liste** mit Suche, Filter nach Status (online / überfällig / offline / sonstige),
  Region (grob) und „Meine“, relativer Zeit („vor 3 h“), Pull-to-refresh
- **Meine Repeater**: frei wählbare Favoriten (★), anfangs leer, jeder wählt seine eigenen Repeater
- **Detailansicht**: Status mit Erklärung, übliche Pausen und Ausfallschwelle, Sichtungsverlauf
  (21 Tage täglich bzw. 7 Tage stündlich), OSM-Karte (osmdroid), Links auf Knoten, Ausfälle,
  Relevanz, Uhren, Positionen und Statistik
- **Benachrichtigungen** bei Ausfall und Rückkehr favorisierter Repeater, Schwellwert pro
  Repeater („automatisch“ = Schwelle des Ausfall-Dienstes, oder 2–72 h)
- **Akku** (ab v0.2.0): Spannung, Verlauf über 7 bzw. 30 Tage, Trend in mV/Tag und Warnschwellen,
  sofern CoreScope Werte für den Repeater hat. Benachrichtigung bei „schwach“ und „kritisch“ für
  Favoriten (mit Hysterese, damit Solar-Tagesgänge nicht dauernd melden)
- **Akku-Typ pro Repeater** (ab v0.3.0): Li-Ion (schwach ≤ 3,50 V, kritisch ≤ 3,30 V), LiFePO4
  (≤ 3,10 V / ≤ 2,90 V), Server-Standard (3,30 / 3,00 V), eigene Werte oder „Kein Akku
  (Netzteil)“ – dann nur Anzeige der Versorgungsspannung, keine Akku-Warnungen. Mit Hinweis, wenn die
  gemeldete Spannung nicht zum Typ passt (z. B. 4,16 V bei LiFePO4 → Board misst etwas anderes)
- **Widget** (Jetpack Glance): Ampel für die Favoriten
- **Offline-Cache** (Room), Material 3 mit dynamischen Farben und Dark Mode
- Server-URL einstellbar (Standard `https://live.saarmesh.de`)

## Datenquellen

| URL | Zweck |
|---|---|
| `https://live.saarmesh.de/ausfall/repeater.json` | Status aller Repeater (alle 15 min neu) |
| `https://live.saarmesh.de/ausfall/verlauf/<pk>.json` | Sichtungen je Stunde, 21 Tage |
| `https://live.saarmesh.de/api/nodes/<pk>/battery?days=30` | Akku-Verlauf (CoreScope), nur für Favoriten bzw. die geöffnete Detailansicht, höchstens 1× pro Stunde |
| `https://live.saarmesh.de/#/nodes/<pk>` und Unterseiten | Deeplinks |
| OpenStreetMap-Tiles | Karte |

Wie „zuletzt gesehen“ und die Schwellen berechnet werden (jede Sichtung, nicht nur Adverts,
Schwellen aus den üblichen Pausen, Observer-Ausfall-Erkennung), steht auf der Ausfall-Seite unter
„Wie wird gerechnet?“. Format und Server-Änderung: [`server/README.md`](server/README.md).
Architektur, Statuslogik und Push-Strategie: [`docs/ARCHITEKTUR.md`](docs/ARCHITEKTUR.md).

## Build

Voraussetzungen: JDK 17 oder neuer, Android SDK (Platform 36). Pfad in `local.properties`:

```properties
sdk.dir=/pfad/zum/Android/sdk
```

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # Unit-Tests (Statuslogik, Statuswechsel, Parser, Filter)
```

Die Debug-Variante hat die App-ID `de.saarmesh.waechter.debug` und lässt sich parallel zur
Release-Version installieren.

### Release

Ein Tag `v*` (z. B. `v0.1.0`) startet den Workflow `.github/workflows/android.yml`. Er baut die
Release-APK und hängt sie an ein GitHub-Release. Signiert wird nur, wenn diese Repository-Secrets
gesetzt sind, sonst entsteht eine unsignierte APK:

`SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`

Lokal geht es alternativ über eine nicht eingecheckte `keystore.properties`
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Es gehören keine Secrets ins Repo.

## Roadmap

**Phase 2**
- Mehr Repeater mit Akku-Werten: verteilte Abfrage-Stationen der Betreiber, die ihre erreichbaren
  Repeater abfragen und an CoreScope melden (siehe `docs/ARCHITEKTUR.md`)
- Paten-Modus: Repeater per E-Mail abonnieren über das bestehende Abo von `/ausfall/`
- Echtes Server-Push über ntfy (self-hosted) und UnifiedPush statt Polling, ohne Google-Dienste
- Französische Oberfläche (SaarLorLux)

**Später**
- Wear-OS-Kachel, Karte aller Repeater, Export der Favoriten

## Lizenz

MIT
