# Technische Beschreibung

Diese Seite erklärt, woher die App ihre Daten bekommt und wie sie daraus Status und Warnungen ableitet.
Für die Benutzung brauchst du sie nicht, siehe dazu die [README](../README.md).

## Datenquellen

Die App ruft nur öffentliche Daten per HTTPS ab (Standard-Server `https://live.saarmesh.de`, in den
Einstellungen änderbar):

| Endpunkt | Inhalt | Wann |
|---|---|---|
| `/ausfall/repeater.json` | Status aller Repeater (siehe [server/README.md](../server/README.md)) | beim Öffnen, bei Pull-to-refresh und im Hintergrund (ab 15 min) |
| `/ausfall/verlauf/<pk>.json` | Sichtungen je Stunde über 21 Tage | beim Öffnen der Detailansicht |
| `/api/nodes/<pk>/battery?days=30` | Akku-Verlauf mit Schwellen des Servers | für Favoriten höchstens stündlich, in der Detailansicht höchstens alle 10 min |
| OpenStreetMap-Kacheln | Karte | in der Detailansicht |

Die Akkuwerte selbst erzeugt die App nicht. Stationen von Repeater-Betreibern fragen sie ab und melden
sie an live.saarmesh.de. Liegen für einen Repeater keine Werte vor, zeigt die App keine Akku-Karte.

Die App benötigt nur die Berechtigungen `INTERNET`, `ACCESS_NETWORK_STATE` und `POST_NOTIFICATIONS`. Sie
hat keinen Zugriff auf Bluetooth, USB oder ein Funkmodul und kann deshalb nichts ins Mesh senden.

## Status eines Repeaters

Grundlage ist die Bewertung von live.saarmesh.de. Dort gilt jede Sichtung, nicht nur Adverts. Die
Schwellen ergeben sich aus den üblichen Pausen des jeweiligen Repeaters. Daraus leitet die App ab:

| Server | ohne eigene Schwelle | mit eigener Schwelle X h |
|---|---|---|
| `aktiv` / `ruhig` | online bis zur üblichen Pause, danach überfällig, nach der Ausfallschwelle offline | online bis X/2 (oder bis zur üblichen Pause, wenn sie kürzer ist), überfällig bis X, danach offline |
| `aus` / `weg` | offline | nach Stille wie oben |
| `unbekannt` | unklar (Empfangsstationen liefern keine Daten) | nach Stille wie oben |
| `wenig` | zu wenig Daten | nach Stille wie oben |
| `ersetzt` | ersetzt | ersetzt |

Bezugszeit ist der Zeitpunkt, zu dem der Server die Daten erzeugt hat, nicht die Uhr des Handys. Ohne Netz
entstehen so keine falschen Offline-Meldungen. Wie alt die Daten sind, zeigt die Liste an.

**Benachrichtigungen** gibt es beim Wechsel zu „offline“ und zurück zu „online“ oder „überfällig“. Die
erste Bewertung nach dem Favorisieren meldet nichts. Unklare Zustände (unklar, ersetzt, zu wenig Daten)
lösen ebenfalls nichts aus. Wer die Schwelle ändert, bekommt durch die Umstellung selbst keine Meldung.

## Akku

- **Stufen:** in Ordnung, schwach, kritisch. Die Schwellen hängen vom gewählten Akku-Typ ab:

  | Akku-Typ | schwach | kritisch |
  |---|---|---|
  | Server-Standard | ≤ 3,30 V | ≤ 3,00 V |
  | Li-Ion | ≤ 3,50 V | ≤ 3,30 V |
  | LiFePO4 | ≤ 3,10 V | ≤ 2,90 V |
  | Inhero MR2 (Ladestand) | ≤ 25 % | ≤ 10 % |
  | eigene Werte | frei | frei |
  | kein Akku (Netzteil) | keine Bewertung | keine Bewertung |

- **Hysterese:** Ein Akku gilt erst wieder als besser, wenn er 100 mV über der Schwelle liegt. So
  erzeugt der Tagesgang eines Solar-Repeaters keine Meldungsflut.
- **Benachrichtigung** nur bei Verschlechterung (in Ordnung → schwach → kritisch). Messwerte, die älter
  als 48 Stunden sind, lösen nichts aus.
- **Plausibilität:** Liegt ein Messwert mehr als 100 mV über der Ladeschlussspannung des gewählten Typs
  (Li-Ion 4,25 V, LiFePO4 3,65 V), zeigt die App den Wert als „nicht bewertbar“ an und warnt nicht.
- **Inhero MR2:** Dieses Board meldet als Akkuspannung seinen Ladestand, umgerechnet auf eine
  Li-Ion-Kennlinie (100 % = 4,20 V). Mit dem Typ „Inhero MR2 (Ladestand)“ rechnet die App das mit
  derselben Tabelle wie die Firmware in Prozent zurück. Liest die Abfrage-Station stattdessen den
  Akku-Kanal des Boards aus (z. B. das Home-Assistant-Add-on ab 1.5.1-sm.21), kommt die echte
  Zellspannung an. Dann passt der Akku-Typ der Zellen, etwa LiFePO4, und nicht „Inhero MR2“.
- **Trend:** lineare Regression über 7 Tage, ab 6 Messwerten und mindestens 24 Stunden Zeitspanne.

## Region

Die Region im Filter ist eine grobe Zuordnung. Sie stammt aus dem Namen (z. B. `DE-SL`, `DE-RP`, `LX`),
einer Postleitzahl im Namen oder sonst aus der Position. Sie dient nur zum Filtern.
