package de.saarmesh.waechter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoltageAxisTest {
    private val A = VoltageAxisLogic

    private fun check(a: VoltageAxis, values: List<Int>) {
        assertTrue("min <= Werte", a.minMv <= values.min())
        assertTrue("max >= Werte", a.maxMv >= values.max())
        assertTrue("3 bis 6 Teilstriche: ${a.ticksMv}", a.ticksMv.size in 3..6)
        assertEquals(a.minMv, a.ticksMv.first())
        assertEquals(a.maxMv, a.ticksMv.last())
        assertTrue(a.ticksMv.zipWithNext().all { (x, y) -> y - x == a.stepMv })
        assertTrue("runde Werte", a.ticksMv.all { it % a.stepMv == 0 })
    }

    @Test fun solarLiIonDay() {
        // 66127-1: 4,04 .. 4,20 V
        val v = listOf(4040, 4100, 4200, 4160)
        val a = A.axis(v)
        check(a, v)
        assertEquals(50, a.stepMv)
        assertEquals(listOf(4000, 4050, 4100, 4150, 4200), a.ticksMv)
    }

    @Test fun farThresholdsAreNotIncluded() {
        // Li-Ion-Schwellen 3,5/3,3 V liegen weit unter 4,0-4,2 V -> Verlauf bleibt lesbar
        val v = listOf(4040, 4200)
        val a = A.axis(v, listOf(3500, 3300))
        assertTrue(a.minMv >= 3900)
    }

    @Test fun nearThresholdIsIncluded() {
        val v = listOf(3400, 3600)
        val a = A.axis(v, listOf(3300, 3000))
        check(a, v)
        assertTrue("schwach-Linie sichtbar", 3300 in a.minMv..a.maxMv)
        assertFalse("kritisch zu weit weg", 3000 in a.minMv..a.maxMv)
    }

    @Test fun flatLineGetsSpan() {
        val v = listOf(4160, 4160, 4160)
        val a = A.axis(v)
        check(a, v)
        assertTrue(a.maxMv > a.minMv)
    }

    @Test fun wideRange() {
        val v = listOf(11000, 13800)
        val a = A.axis(v)
        check(a, v)
        assertEquals(1000, a.stepMv)
    }

    @Test fun noneProfileHasNoAlerts() {
        val f = Favorite("a".repeat(64), null, true, null, null, BatteryProfile.NONE)
        assertFalse(f.batteryAlertsEnabled)
        assertFalse(BatteryThresholdLogic.implausible(BatteryProfile.NONE, 5000))
        assertTrue(Favorite("b".repeat(64), null, true, null, null, BatteryProfile.LI_ION).batteryAlertsEnabled)
        assertTrue(Favorite("c".repeat(64), null, true, null).batteryAlertsEnabled)
        assertEquals(BatteryProfile.NONE, BatteryProfile.parse("NONE"))
    }
}
