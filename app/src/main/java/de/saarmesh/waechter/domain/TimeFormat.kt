package de.saarmesh.waechter.domain

/** Zerlegt eine Dauer fuer "vor 3 h"-Anzeigen; die Texte selbst kommen aus den Ressourcen. */
sealed interface Ago {
    data object JustNow : Ago
    data class Minutes(val n: Long) : Ago
    data class Hours(val n: Long) : Ago
    data class Days(val n: Long) : Ago
}

object AgoFormat {
    fun of(seconds: Long): Ago {
        val s = seconds.coerceAtLeast(0)
        return when {
            s < 60 -> Ago.JustNow
            s < 3600 -> Ago.Minutes(s / 60)
            s < 48 * 3600 -> Ago.Hours(s / 3600)
            else -> Ago.Days(s / 86400)
        }
    }
}
