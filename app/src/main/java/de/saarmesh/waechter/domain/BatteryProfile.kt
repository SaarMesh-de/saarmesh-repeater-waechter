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
    ;

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

    /**
     * Bereich aus den Messwerten; Schwellen werden nur einbezogen, wenn sie nahe am
     * Verlauf liegen (sonst wuerde der Verlauf zu einem Strich zusammengedrueckt).
     */
    fun axis(values: List<Int>, thresholds: List<Int> = emptyList(), nearMv: Int = 300): VoltageAxis {
        require(values.isNotEmpty())
        var lo = values.min()
        var hi = values.max()
        thresholds.filter { it >= lo - nearMv && it <= hi + nearMv }.forEach { lo = minOf(lo, it); hi = maxOf(hi, it) }
        if (hi - lo < 40) { lo -= 20; hi += 20 }
        val span = hi - lo
        val step = STEPS.firstOrNull { span / it <= 4 } ?: STEPS.last()
        val min = Math.floorDiv(lo, step) * step
        val max = -Math.floorDiv(-hi, step) * step
        val ticks = (min..max step step).toList()
        return VoltageAxis(min, max, ticks, step)
    }
}

fun BatteryInfo.withThresholds(t: BatteryThresholds) = copy(lowMv = t.lowMv, criticalMv = t.criticalMv)
