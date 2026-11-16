package de.saarmesh.waechter.domain

/**
 * Inhero MR2: Die Firmware meldet als "Akkuspannung" nicht die echte Zellspannung,
 * sondern den per Coulomb-Zaehler ermittelten Ladestand, umgerechnet in die Spannung,
 * die eine Li-Ion-Zelle bei diesem Ladestand haette (InheroMr2Board::getBattMilliVolts,
 * helpers/BatteryOcvMapping.cpp). Diese Tabelle rechnet das exakt zurueck.
 * Ohne gueltigen Ladestand (kurz nach dem Start) meldet das Board die echte Spannung;
 * das ist von aussen nicht unterscheidbar.
 */
object InheroSoc {
    /** Li-Ion-1S-OCV in mV fuer 0 %, 10 %, ... 100 % (identisch zur MR2-Firmware). */
    private val OCV = intArrayOf(3000, 3300, 3450, 3530, 3600, 3670, 3740, 3820, 3920, 4050, 4200)

    fun socToMv(soc: Double): Int {
        if (soc <= 0.0) return OCV.first()
        if (soc >= 100.0) return OCV.last()
        val idx = (soc / 10.0).toInt().coerceAtMost(9)
        val frac = soc / 10.0 - idx
        return Math.round(OCV[idx] + frac * (OCV[idx + 1] - OCV[idx])).toInt()
    }

    fun mvToSoc(mv: Int): Double {
        if (mv <= OCV.first()) return 0.0
        if (mv >= OCV.last()) return 100.0
        val idx = (0 until 10).first { mv <= OCV[it + 1] }
        val frac = (mv - OCV[idx]).toDouble() / (OCV[idx + 1] - OCV[idx])
        return (idx + frac) * 10.0
    }

    const val LOW_SOC = 25.0
    const val CRITICAL_SOC = 10.0
}
