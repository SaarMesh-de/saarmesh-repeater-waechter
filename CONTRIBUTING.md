# Mitarbeit am Repeater-Wächter

Danke, dass du mithelfen möchtest. Fehlerberichte und Ideen sind als
[Issue](https://github.com/SaarMesh-de/saarmesh-repeater-waechter/issues) willkommen, Änderungen als Pull Request.

## Bauen

Voraussetzungen: JDK 17 oder neuer, Android SDK mit Platform 36. Den SDK-Pfad trägst du in einer
`local.properties` im Projektordner ein (die Datei wird nicht eingecheckt):

```properties
sdk.dir=/pfad/zum/Android/sdk
```

```bash
./gradlew assembleDebug        # Debug-APK: app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # Unit-Tests
```

Die Debug-Variante hat die App-ID `de.saarmesh.waechter.debug` und lässt sich neben der Release-Version
installieren.

## Aufbau

Kotlin, Jetpack Compose (Material 3), Hilt, Room als Offline-Cache, WorkManager für den Abruf im
Hintergrund und Jetpack Glance für das Widget. Die Statuslogik liegt in `app/src/main/java/.../domain/`.
Sie hat keine Android-Abhängigkeiten und ist durch Unit-Tests abgedeckt. Datenquellen und Statuslogik
beschreibt [docs/ARCHITEKTUR.md](docs/ARCHITEKTUR.md).

Texte stehen in `app/src/main/res/values/strings.xml`. Eine Übersetzung legst du als
`values-<sprache>/strings.xml` an und schaltest sie in `res/xml/locales_config.xml` frei.

Grundsatz: Die App liest nur. Sie bekommt keine Funktionen, die etwas ins Mesh senden oder Repeater
konfigurieren.

## Releases

Ein Git-Tag `v*` (z. B. `v0.4.0`) startet den Workflow `.github/workflows/android.yml`. Er führt die Tests
aus, baut die Release-APK und hängt sie an ein GitHub-Release. Signiert wird die APK mit den
Repository-Secrets `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` und
`SIGNING_KEY_PASSWORD`. Fehlen sie, entsteht eine unsignierte APK.

Lokal kannst du stattdessen eine `keystore.properties` mit `storeFile`, `storePassword`, `keyAlias` und
`keyPassword` anlegen. Keystores und Passwörter gehören nie ins Repository.
