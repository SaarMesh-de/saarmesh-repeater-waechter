package de.saarmesh.waechter.domain

/**
 * Akku-Typ eines Repeaters, waehlbar pro Favorit. Die Schwellen beziehen sich auf die
 * Spannung, die CoreScope meldet (bei den ueblichen Boards: Spannung einer Zelle bzw.
 * parallel geschalteter Zellen).
 */
enum class BatteryProfile(val lowMv: Int?, val criticalMv: Int?, val maxPlausibleMv: Int?) {
    /** Schwellen des Servers (CoreScope). */
    SERVER(null, null, null),
    /** Li-Ion / Li-Po, 3,6-3,7 V nominal, 4,2 V voll. */
    LI_ION(3500, 3300, 4250),
    /** LiFePO4, 3,2 V nominal, 3,65 V voll, sehr flache Entladekurve. */
    LIFEPO4(3100, 2900, 3650),
    /** Eigene Werte. */
    CUSTOM(null, null, null),
    /** Kein Akku, Netzteil/USB: Spannung wird nur angezeigt, keine Akku-Warnungen. */
    NONE(null, null, null),
    /**
     * Inhero MR2: gemeldeter Wert ist der Ladestand als Li-Ion-Ersatzspannung
     * (siehe [InheroSoc]). Schwellen 25 % / 10 % Ladestand.
     */
    INHERO_MR2(3490, 3300, 4250),
    ;

    /** Anzeige als Ladestand in Prozent statt als Spannung. */
    val showsSoc: Boolean get() = this == INHERO_MR2

    /** Akku-Stufen und Benachrichtigungen nur fuer echte Akkus. */
    val hasBattery: Boolean get() = this != NONE

    companion object {
        fun parse(s: String?): BatteryProfile = entries.firstOrNull { it.name == s } ?: SERVER
        const val PLAUSIBILITY_MARGIN_MV = 100
        const val MIN_MV = 2000
        const val MAX_MV = 15000
    }
}

data class BatteryThresholds(val lowMv: Int, val criticalMv: Int)

object BatteryThresholdLogic {
    /** Gueltig: beide im sinnvollen Bereich und kritisch echt unter schwach. */
    fun isValid(lowMv: Int?, criticalMv: Int?): Boolean =
        lowMv != null && criticalMv != null &&
            lowMv in BatteryProfile.MIN_MV..BatteryProfile.MAX_MV &&
            criticalMv in BatteryProfile.MIN_MV..BatteryProfile.MAX_MV &&
            criticalMv < lowMv

    /**
     * Wirksame Schwellen: Profil-Werte, eigene Werte (wenn gueltig) oder die des Servers.
     * Ungueltige eigene Werte fallen auf den Server zurueck, statt falsch zu alarmieren.
     */
    fun effective(server: BatteryThresholds, profile: BatteryProfile, customLowMv: Int?, customCriticalMv: Int?): BatteryThresholds =
        when (profile) {
            BatteryProfile.SERVER, BatteryProfile.NONE -> server
            BatteryProfile.CUSTOM ->
                if (isValid(customLowMv, customCriticalMv)) BatteryThresholds(customLowMv!!, customCriticalMv!!) else server
            else -> BatteryThresholds(profile.lowMv!!, profile.criticalMv!!)
        }

    /**
     * Akku-Stufe und Benachrichtigung sind nur sinnvoll, wenn ein Akku verbaut ist und der
     * Messwert zum gewaehlten Typ passt. Sonst: "nicht bewertbar", keine Warnungen.
     */
    fun assessable(profile: BatteryProfile, mv: Int?): Boolean = profile.hasBattery && !implausible(profile, mv)

    /** true, wenn der Messwert nicht zum gewaehlten Akku-Typ passen kann (z. B. 4,16 V bei LiFePO4). */
    fun implausible(profile: BatteryProfile, mv: Int?): Boolean {
        val max = profile.maxPlausibleMv ?: return false
        return mv != null && mv > max + BatteryProfile.PLAUSIBILITY_MARGIN_MV
    }

    /** "3,2" / "3.20" / "3200" -> 3200 mV; null bei Unsinn. Werte < 100 werden als Volt gelesen. */
    fun parseVoltsOrMv(input: String): Int? {
        val t = input.trim().replace(',', '.').removeSuffix("V").removeSuffix("v").removeSuffix("mV").trim()
        val d = t.toDoubleOrNull() ?: return null
        val mv = if (d < 100) Math.round(d * 1000).toInt() else Math.round(d).toInt()
        return mv.takeIf { it in BatteryProfile.MIN_MV..BatteryProfile.MAX_MV }
    }
}

/** Achsen-Skala fuer Spannungsdiagramme: runde Schritte, 3 bis 6 Teilstriche. */
data class VoltageAxis(val minMv: Int, val maxMv: Int, val ticksMv: List<Int>, val stepMv: Int)

object VoltageAxisLogic {
    private val STEPS = listOf(10, 20, 25, 50, 100, 200, 250, 500, 1000, 2000)
    private val SOC_STEPS = listOf(1, 2, 5, 10, 20, 25)

    /**
     * Bereich aus den Messwerten; Schwellen werden nur einbezogen, wenn sie nahe am
     * Verlauf liegen (sonst wuerde der Verlauf zu einem Strich zusammengedrueckt).
     */
    fun axis(values: List<Int>, thresholds: List<Int> = emptyList(), nearMv: Int = 300): VoltageAxis =
        axis(values, thresholds, nearMv, STEPS, minSpan = 40, bounds = null)

    /** Achse fuer Ladestand in Prozent (0..100). */
    fun socAxis(values: List<Int>, thresholds: List<Int> = emptyList()): VoltageAxis =
        axis(values, thresholds, near = 15, steps = SOC_STEPS, minSpan = 4, bounds = 0..100)

    private fun axis(values: List<Int>, thresholds: List<Int>, near: Int, steps: List<Int>, minSpan: Int, bounds: IntRange?): VoltageAxis {
        require(values.isNotEmpty())
        var lo = values.min()
        var hi = values.max()
        thresholds.filter { it >= lo - near && it <= hi + near }.forEach { lo = minOf(lo, it); hi = maxOf(hi, it) }
        if (hi - lo < minSpan) { lo -= minSpan / 2; hi += minSpan / 2 }
        if (bounds != null) {
            if (lo < bounds.first) { hi += bounds.first - lo; lo = bounds.first }
            if (hi > bounds.last) { lo -= hi - bounds.last; hi = bounds.last }
            lo = lo.coerceAtLeast(bounds.first)
        }
        val span = hi - lo
        val step = steps.firstOrNull { span / it <= 4 } ?: steps.last()
        var min = Math.floorDiv(lo, step) * step
        var max = -Math.floorDiv(-hi, step) * step
        if (bounds != null) { min = min.coerceAtLeast(bounds.first); max = max.coerceAtMost(bounds.last) }
        val ticks = (min..max step step).toList()
        return VoltageAxis(min, max, ticks, step)
    }
}

fun BatteryInfo.withThresholds(t: BatteryThresholds) = copy(lowMv = t.lowMv, criticalMv = t.criticalMv)
