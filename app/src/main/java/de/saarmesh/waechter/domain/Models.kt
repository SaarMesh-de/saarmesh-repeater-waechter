package de.saarmesh.waechter.domain

/**
 * Status, wie die App ihn anzeigt. Abgeleitet aus dem Status des Ausfall-Dienstes
 * (live.saarmesh.de/ausfall/) und optional einem eigenen Schwellwert pro Repeater.
 */
enum class RepeaterStatus {
    /** In der ueblichen Zeit gesehen. */
    ONLINE,
    /** Laenger still als ueblich, aber noch unter der Ausfallschwelle. */
    UEBERFAELLIG,
    /** Vermutlich ausgefallen. */
    OFFLINE,
    /** Lange still, aber die Observer, die ihn sonst hoeren, liefern selbst keine Daten. */
    UNBEKANNT,
    /** Am gleichen Standort ist eine neue Identitaet aufgetaucht (neu geflasht, neuer Key). */
    ERSETZT,
    /** Zu wenige Sichtungen fuer eine Bewertung. */
    WENIG_DATEN,
}

/** Status-Text des Ausfall-Dienstes (Feld "status" in repeater.json). */
enum class ServerStatus(val wire: String) {
    AKTIV("aktiv"), RUHIG("ruhig"), AUS("aus"), WEG("weg"),
    UNBEKANNT("unbekannt"), ERSETZT("ersetzt"), WENIG("wenig"), UNBEKANNTER_WERT("");

    companion object {
        fun parse(s: String?): ServerStatus = entries.firstOrNull { it.wire == s && it.wire.isNotEmpty() } ?: UNBEKANNTER_WERT
    }
}

data class Repeater(
    val publicKey: String,
    val name: String,
    val serverStatus: ServerStatus,
    /** Letzte Sichtung, Unix-Sekunden. */
    val lastSeen: Long,
    val via: String?,
    val art: String?,
    val sightings: Int,
    val lat: Double?,
    val lon: Double?,
    val firstSeen: Long?,
    /** Schwellen des Servers in Sekunden (fehlen bei "wenig"). */
    val quietAfter: Long?,
    val downAfter: Long?,
    val typicalGap: Long?,
    val rareGap: Long?,
    val replacedByKey: String?,
    val replacedByName: String?,
    /** Sichtungen (5-min-Raster) je Tag, aeltester Tag zuerst. */
    val daily: List<Int>,
) {
    val hasPosition: Boolean get() = lat != null && lon != null
    val shortKey: String get() = publicKey.take(8)
}

/** Liste mit dem Zeitpunkt, an dem der Server sie erzeugt hat. */
data class RepeaterSnapshot(
    val generated: Long,
    val windowDays: Int,
    val dailyFrom: Long,
    val repeaters: List<Repeater>,
)

data class SightingHistory(
    val publicKey: String,
    /** Unix-Sekunden des ersten Werts. */
    val from: Long,
    val stepSeconds: Long,
    val values: List<Int>,
)

data class Favorite(
    val publicKey: String,
    /** Eigener Schwellwert "offline nach X Stunden ohne Sichtung"; null = Server-Schwelle. */
    val offlineAfterHours: Int?,
    val notify: Boolean,
    /** Zuletzt gemeldeter Zustand; null = noch nie bewertet. */
    val wasDown: Boolean?,
    /** Zuletzt bewertete Akku-Stufe; null = keine Werte oder noch nie bewertet. */
    val batteryLevel: BatteryLevel? = null,
    val batteryProfile: BatteryProfile = BatteryProfile.SERVER,
    val batteryLowMv: Int? = null,
    val batteryCriticalMv: Int? = null,
) {
    /** Wirksame Akku-Schwellen fuer diesen Favoriten. */
    fun batteryThresholds(server: BatteryThresholds) =
        BatteryThresholdLogic.effective(server, batteryProfile, batteryLowMv, batteryCriticalMv)
}
