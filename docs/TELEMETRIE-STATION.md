# Akkuwerte deines Repeaters an live.saarmesh.de melden

Diese Anleitung ist für dich, wenn du einen Repeater **auf Akku oder Solar** betreibst und **Home Assistant
mit der Integration meshcore-ha** nutzt. Mit dem SaarMesh-Add-on für Home Assistant gibst du die Akkuwerte,
die meshcore-ha ohnehin abfragt, an live.saarmesh.de weiter. Danach siehst du den Akku-Verlauf deines Repeaters
auf der Live-Karte und im [Repeater-Wächter](../README.md), und die App warnt rechtzeitig, bevor der Akku leer ist.

**Das kostet keinen zusätzlichen Funkverkehr.** Das Add-on liest nur die Werte, die in Home Assistant schon
vorhanden sind, und schickt sie über das Internet weiter.

Repeater am Netzteil brauchen das nicht. Für sie reicht die Ausfall-Meldung.

## Was du brauchst

- Home Assistant mit der Integration **meshcore-ha**, verbunden mit einem Companion-Funkgerät
- deinen Repeater in meshcore-ha eingetragen, mit Passwort (Admin oder Gast), sodass Home Assistant für ihn
  Sensoren wie *Battery Voltage* anzeigt
- einen Zugang für live.saarmesh.de (Schritt 2)

## 1. Prüfen, ob Home Assistant die Akkuwerte schon hat

Öffne in Home Assistant *Einstellungen → Geräte & Dienste → Entitäten* und suche nach dem Namen deines
Repeaters. Es sollte einen Sensor geben wie

`MeshCore Repeater: <Name> (<Key-Anfang>) Battery Voltage`

mit einem aktuellen Wert. Fehlt er, trage den Repeater in meshcore-ha ein (Integration *MeshCore* →
*Konfigurieren* → Repeater mit Passwort hinzufügen) und warte, bis die ersten Werte da sind.

**Inhero MR2:** Hier gibt es zusätzlich Sensoren wie *Ch3 Voltage*, *Ch3 Percentage* und *Ch3 Current*. Das
Add-on nimmt automatisch die echte Zellspannung aus diesem Kanal.

## 2. Zugang anfragen

Schreib uns über [saarmesh.de](https://saarmesh.de) mit:

- Name deines Repeaters, wie er auf [live.saarmesh.de](https://live.saarmesh.de) erscheint
- seinem **vollständigen Public Key** (64 Zeichen). Du findest ihn auf live.saarmesh.de in der Detailansicht des
  Repeaters oder in Home Assistant in den Attributen des Repeater-Kontakts.
- Akku-Typ (Li-Ion, LiFePO4, …) und ob Solar dranhängt

Du bekommst einen **eigenen Zugang** (Benutzername und Passwort). Er darf ausschließlich Akkuwerte für die
genannten Repeater einliefern, sonst nichts. Bitte gib ihn nicht weiter. Weitere Repeater schalten wir auf
Nachfrage für denselben Zugang frei.

## 3. Add-on installieren oder aktualisieren

Das Add-on stammt aus dem SaarMesh-Repository. Ältere Anleitungen verweisen teilweise auf das ursprüngliche
Repository, dort fehlt diese Funktion.

1. *Einstellungen → Add-ons → Add-on-Store* → oben rechts *⋮* → *Repositories*.
2. Diese Adresse hinzufügen:
   `https://github.com/SaarMesh-Bot/Meshcore-bot-for-Home-Assistant`
3. Das Add-on **Meshcore-bot** installieren oder aktualisieren, mindestens auf Version **1.5.1-sm.21**.

Wie du das Add-on grundsätzlich einrichtest (Verbindung zum Funkgerät, gemeinsame Nutzung mit meshcore-ha),
steht in dessen [Installationsanleitung](https://github.com/SaarMesh-Bot/Meshcore-bot-for-Home-Assistant/blob/main/INSTALL.md).

## 4. CoreScopeTelemetry einschalten

Öffne die Oberfläche des Add-ons, gehe auf **Plugins** und dort auf die Karte **CoreScopeTelemetry**. Je nach Sprache der Oberfläche können die Felder auch deutsch beschriftet sein.

| Einstellung | Wert |
|---|---|
| eingeschaltet | ja |
| Broker | `mqtt.saarmesh.de` |
| Port | `8883` |
| TLS | an |
| User name / Password | dein Zugang aus Schritt 2 |
| Region (IATA) | `SCN` (in Luxemburg `LUX`) |
| Repeaters | die ersten Zeichen der Public Keys deiner Repeater, z. B. `5000e4`. Leer lassen schickt alle Repeater, die meshcore-ha kennt. Für fremde Repeater lehnt der Server das aber ab. |
| Read Home Assistant every | `10` Minuten (Standard) |
| Skip readings older than | `6` Stunden (Standard) |

Speichern und das **Add-on neu starten**. Die Karte weist darauf hin, weil es sich um einen Dienst handelt.

## 5. Prüfen

- Im **Protokoll des Add-ons** steht nach etwa einer Minute `CoreScopeTelemetry: verbunden mit mqtt.saarmesh.de`.
  Sobald ein neuer Messwert da ist, folgt eine Zeile wie `CoreScopeTelemetry: 5000e4b7… 3390 mV`.
- Auf live.saarmesh.de erscheint der Wert, z. B. unter
  `https://live.saarmesh.de/api/nodes/<dein Public Key>/battery?days=1`.
- Im Repeater-Wächter zeigt die Detailansicht deines Repeaters jetzt eine **Akku-Karte**. Tippe dort auf
  *Ändern* und wähle den Akku-Typ deiner Zellen, z. B. LiFePO4. Erst dann passen die Warnschwellen.

Neue Messwerte kommen so oft, wie meshcore-ha deinen Repeater abfragt (oft alle paar Stunden).

## Wenn es nicht klappt

| Meldung oder Beobachtung | Ursache und Abhilfe |
|---|---|
| `Broker mqtt.saarmesh.de lehnt ab` | Benutzername oder Passwort falsch, oder Port und TLS passen nicht zusammen (8883 nur mit TLS). |
| `nicht mit mqtt.saarmesh.de verbunden, Runde übersprungen` | keine Internetverbindung oder Port 8883 ausgehend gesperrt |
| verbunden, aber keine Zeile mit mV | Home Assistant hat seit Start des Add-ons keinen neuen Messwert, oder der letzte ist älter als 6 Stunden, oder der Repeater gilt als offline. Prüfe den Sensor aus Schritt 1. |
| Werte kommen an, aber nicht für jeden Repeater | Der Zugang ist nur für die gemeldeten Repeater freigeschaltet. Schreib uns, welcher fehlt. |
| Inhero MR2 zeigt im Repeater-Wächter einen viel zu niedrigen Ladestand | Das Add-on schickt die echte Zellspannung. Wähle in der App den Akku-Typ der Zellen (z. B. LiFePO4), nicht „Inhero MR2 (Ladestand)“. |

## Datenschutz

Gesendet werden nur der Public Key des Repeaters, seine Akkuspannung, die Temperatur (falls vorhanden) und der
Zeitpunkt der Messung. Das sind Werte, die auf live.saarmesh.de ohnehin öffentlich angezeigt werden. Über den
Zugang kann niemand Daten aus dem Mesh mitlesen oder etwas ins Mesh senden.
