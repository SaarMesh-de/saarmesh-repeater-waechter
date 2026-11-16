package de.saarmesh.waechter.domain

/**
 * Akku-Telemetrie aus CoreScope (/api/nodes/<pk>/battery). Die Werte holt nicht die App
 * aus dem Mesh, sondern Abfrage-Stationen der Betreiber, die sie an CoreScope schicken.
 */
data class BatterySample(val ts: Long, val mv: Int)

data class BatteryInfo(
    val publicKey: String,
    val latestMv: Int?,
    val latestTs: Long?,
    val lowMv: Int,
    val criticalMv: Int,
    val samples: List<BatterySample>,
) {
    val hasData: Boolean get() = latestMv != null && latestTs != null
}

enum class BatteryLevel { OK, LOW, CRITICAL }

object BatteryLogic {
    const val DEFAULT_LOW_MV = 3300
    const val DEFAULT_CRITICAL_MV = 3000
    /** Erst so weit ueber der Schwelle gilt der Akku wieder als ok (gegen Flattern bei Solar). */
    const val HYSTERESIS_MV = 100
    /** Aelter als das: Wert wird angezeigt, loest aber keine Benachrichtigung aus. */
    const val STALE_AFTER_S = 48 * 3600L
    /** Mindestens so viele Werte und so viel Zeitspanne fuer eine Trendangabe. */
    const val TREND_MIN_SAMPLES = 6
    const val TREND_MIN_SPAN_S = 24 * 3600L

    fun level(mv: Int, lowMv: Int, criticalMv: Int): BatteryLevel = when {
        mv <= criticalMv -> BatteryLevel.CRITICAL
        mv <= lowMv -> BatteryLevel.LOW
        else -> BatteryLevel.OK
    }

    /**
     * Stufe mit Hysterese: Wer schon LOW/CRITICAL war, wird erst wieder besser eingestuft,
     * wenn die Spannung die jeweilige Schwelle um [HYSTERESIS_MV] ueberschreitet.
     */
    fun levelWithHysteresis(mv: Int, lowMv: Int, criticalMv: Int, previous: BatteryLevel?): BatteryLevel = when (previous) {
        BatteryLevel.CRITICAL -> when {
            mv <= criticalMv + HYSTERESIS_MV -> BatteryLevel.CRITICAL
            mv <= lowMv + HYSTERESIS_MV -> BatteryLevel.LOW
            else -> BatteryLevel.OK
        }
        BatteryLevel.LOW -> when {
            mv <= criticalMv -> BatteryLevel.CRITICAL
            mv <= lowMv + HYSTERESIS_MV -> BatteryLevel.LOW
            else -> BatteryLevel.OK
        }
        else -> level(mv, lowMv, criticalMv)
    }

    fun isStale(info: BatteryInfo, now: Long): Boolean =
        info.latestTs == null || now - info.latestTs > STALE_AFTER_S

    /**
     * Trend in mV pro Tag (lineare Regression) ueber die Werte der letzten [windowS] Sekunden.
     * null, wenn zu wenige Werte oder eine zu kurze Zeitspanne vorliegen.
     */
    fun trendMvPerDay(samples: List<BatterySample>, now: Long, windowS: Long = 7 * 86400L): Double? {
        val s = samples.filter { it.ts >= now - windowS }
        if (s.size < TREND_MIN_SAMPLES) return null
        val span = s.maxOf { it.ts } - s.minOf { it.ts }
        if (span < TREND_MIN_SPAN_S) return null
        val t0 = s.first().ts
        val xs = s.map { (it.ts - t0) / 86400.0 }
        val ys = s.map { it.mv.toDouble() }
        val mx = xs.average()
        val my = ys.average()
        var num = 0.0
        var den = 0.0
        for (i in xs.indices) {
            num += (xs[i] - mx) * (ys[i] - my)
            den += (xs[i] - mx) * (xs[i] - mx)
        }
        return if (den == 0.0) null else num / den
    }
}

/** Benachrichtigung bei Akku-Verschlechterung. */
sealed interface BatteryEvent {
    data object BecameLow : BatteryEvent
    data object BecameCritical : BatteryEvent
}

data class BatteryTransition(val event: BatteryEvent?, val levelNow: BatteryLevel?)

object BatteryTransitionDetector {
    /**
     * Meldet nur Verschlechterungen (OK -> LOW, OK/LOW -> CRITICAL). Erholung wird still gemerkt.
     * Veraltete oder fehlende Werte aendern nichts. Erste Bewertung: nur merken.
     */
    fun detect(previous: BatteryLevel?, info: BatteryInfo, now: Long): BatteryTransition {
        val mv = info.latestMv
        if (mv == null || BatteryLogic.isStale(info, now)) return BatteryTransition(null, previous)
        val cur = BatteryLogic.levelWithHysteresis(mv, info.lowMv, info.criticalMv, previous)
        if (previous == null) return BatteryTransition(null, cur)
        val event = when {
            cur == BatteryLevel.CRITICAL && previous != BatteryLevel.CRITICAL -> BatteryEvent.BecameCritical
            cur == BatteryLevel.LOW && previous == BatteryLevel.OK -> BatteryEvent.BecameLow
            else -> null
        }
        return BatteryTransition(event, cur)
    }
}
