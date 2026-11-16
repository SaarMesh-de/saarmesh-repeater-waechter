package de.saarmesh.waechter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InheroSocTest {
    @Test fun matchesFirmwareTable() {
        // Werte aus variants/inhero_mr2/helpers/BatteryOcvMapping.cpp
        val fw = mapOf(0 to 3000, 10 to 3300, 20 to 3450, 30 to 3530, 40 to 3600, 50 to 3670,
            60 to 3740, 70 to 3820, 80 to 3920, 90 to 4050, 100 to 4200)
        fw.forEach { (soc, mv) ->
            assertEquals(mv, InheroSoc.socToMv(soc.toDouble()))
            assertEquals(soc.toDouble(), InheroSoc.mvToSoc(mv), 1e-9)
        }
    }

    @Test fun roundTrip() {
        for (tenth in 0..1000) {
            val soc = tenth / 10.0
            assertEquals(soc, InheroSoc.mvToSoc(InheroSoc.socToMv(soc)), 0.15)
        }
    }

    @Test fun clampsOutOfRange() {
        assertEquals(0.0, InheroSoc.mvToSoc(2500), 0.0)
        assertEquals(100.0, InheroSoc.mvToSoc(4300), 0.0)
        assertEquals(3000, InheroSoc.socToMv(-5.0))
        assertEquals(4200, InheroSoc.socToMv(120.0))
    }

    @Test fun realValuesFrom66127_2() {
        // CoreScope-Saegezahn 4,14..4,20 V = ca. 96..100 % Ladestand
        assertEquals(100.0, InheroSoc.mvToSoc(4200), 0.01)
        assertEquals(96.0, InheroSoc.mvToSoc(4140), 0.01)
    }

    @Test fun mr2ProfileThresholdsMatchSoc() {
        val p = BatteryProfile.INHERO_MR2
        assertEquals(InheroSoc.socToMv(InheroSoc.LOW_SOC), p.lowMv)
        assertEquals(InheroSoc.socToMv(InheroSoc.CRITICAL_SOC), p.criticalMv)
        assertTrue(p.showsSoc)
        assertTrue(p.hasBattery)
        val t = BatteryThresholdLogic.effective(BatteryThresholds(3300, 3000), p, null, null)
        assertEquals(BatteryLevel.OK, BatteryLogic.level(4200, t.lowMv, t.criticalMv))
        assertEquals(BatteryLevel.LOW, BatteryLogic.level(InheroSoc.socToMv(20.0), t.lowMv, t.criticalMv))
        assertEquals(BatteryLevel.CRITICAL, BatteryLogic.level(InheroSoc.socToMv(8.0), t.lowMv, t.criticalMv))
    }

    @Test fun implausibleIsNotAssessable() {
        assertFalse("4,16 V bei LiFePO4", BatteryThresholdLogic.assessable(BatteryProfile.LIFEPO4, 4160))
        assertTrue(BatteryThresholdLogic.assessable(BatteryProfile.LIFEPO4, 3300))
        assertTrue("MR2 mit Ersatzwert ist bewertbar", BatteryThresholdLogic.assessable(BatteryProfile.INHERO_MR2, 4160))
        assertFalse(BatteryThresholdLogic.assessable(BatteryProfile.NONE, 4000))
        val f = Favorite("a".repeat(64), null, true, null, null, BatteryProfile.LIFEPO4)
        assertFalse(f.batteryAssessable(4160))
        assertTrue(f.batteryAssessable(3200))
    }

    @Test fun socAxis() {
        val a = VoltageAxisLogic.socAxis(listOf(96, 100, 97))
        assertTrue(a.minMv <= 96 && a.maxMv == 100)
        assertTrue(a.ticksMv.size in 3..6)
        val b = VoltageAxisLogic.socAxis(listOf(100, 100))
        assertTrue("bleibt in 0..100", b.maxMv <= 100 && b.minMv >= 0 && b.maxMv > b.minMv)
        val c = VoltageAxisLogic.socAxis(listOf(20, 35), listOf(25, 10))
        assertTrue("10 % nahe -> sichtbar", 10 in c.minMv..c.maxMv)
        val d = VoltageAxisLogic.socAxis(listOf(0, 1))
        assertTrue(d.minMv == 0 && d.maxMv > 1)
    }
}
