package de.saarmesh.waechter.domain

/**
 * Reine Statuslogik ohne Android-Abhaengigkeiten (unit-getestet).
 *
 * Bezugszeit ist der Erzeugungszeitpunkt der Serverdaten, nicht die Uhr des Telefons:
 * Liegen veraltete Daten im Cache (kein Netz), altert der Status nicht kuenstlich und
 * es gibt keine Fehlalarme. Wie alt die Daten sind, zeigt die UI separat an.
 */
object StatusLogic {
    const val DEFAULT_QUIET_S = 3 * 3600L
    const val DEFAULT_DOWN_S = 6 * 3600L

    fun evaluate(r: Repeater, referenceTime: Long, offlineAfterHours: Int? = null): RepeaterStatus {
        if (r.serverStatus == ServerStatus.ERSETZT) return RepeaterStatus.ERSETZT
        val silence = (referenceTime - r.lastSeen).coerceAtLeast(0)
        val custom = offlineAfterHours?.takeIf { it > 0 }?.let { it * 3600L }

        if (custom == null) {
            when (r.serverStatus) {
                ServerStatus.WENIG -> return RepeaterStatus.WENIG_DATEN
                ServerStatus.UNBEKANNT -> return RepeaterStatus.UNBEKANNT
                ServerStatus.AUS, ServerStatus.WEG -> return RepeaterStatus.OFFLINE
                else -> Unit
            }
        }
        val offlineAfter = custom ?: r.downAfter ?: DEFAULT_DOWN_S
        val quiet = r.quietAfter ?: DEFAULT_QUIET_S
        val overdueAfter = if (custom == null) quiet else minOf(quiet, custom / 2)
        return when {
            silence > offlineAfter -> RepeaterStatus.OFFLINE
            silence > overdueAfter -> RepeaterStatus.UEBERFAELLIG
            else -> RepeaterStatus.ONLINE
        }
    }
}

/** Ergebnis der Statuswechsel-Erkennung fuer einen favorisierten Repeater. */
sealed interface StatusEvent {
    data object WentOffline : StatusEvent
    data object BackOnline : StatusEvent
}

data class Transition(val event: StatusEvent?, val wasDownNow: Boolean)

/**
 * Erkennt Wechsel online <-> offline. Gleiche Semantik wie die Telegram-Meldung des
 * Ausfall-Dienstes: "wieder da" erst, wenn der Repeater wieder online/ueberfaellig ist;
 * unklare Zustaende (Observer fehlen, ersetzt, zu wenig Daten) loesen nichts aus und
 * lassen den gemerkten Zustand unveraendert.
 */
object TransitionDetector {
    fun detect(wasDown: Boolean?, current: RepeaterStatus): Transition {
        val isDown = current == RepeaterStatus.OFFLINE
        val isUp = current == RepeaterStatus.ONLINE || current == RepeaterStatus.UEBERFAELLIG
        return when {
            wasDown == null -> Transition(null, isDown)          // erste Bewertung: nur merken
            !wasDown && isDown -> Transition(StatusEvent.WentOffline, true)
            wasDown && isUp -> Transition(StatusEvent.BackOnline, false)
            else -> Transition(null, wasDown)
        }
    }
}
