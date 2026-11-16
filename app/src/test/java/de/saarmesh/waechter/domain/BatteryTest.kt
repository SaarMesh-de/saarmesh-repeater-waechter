package de.saarmesh.waechter.domain

import de.saarmesh.waechter.domain.BatteryLevel.CRITICAL
import de.saarmesh.waechter.domain.BatteryLevel.LOW
import de.saarmesh.waechter.domain.BatteryLevel.OK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryTest {
    private val now = 1_800_000_000L
    private fun info(mv: Int?, age: Long = 600, samples: List<BatterySample> = emptyList()) =
        BatteryInfo("a".repeat(64), mv, mv?.let { now - age }, 3300, 3000, samples)

    @Test fun levels() {
        assertEquals(OK, BatteryLogic.level(3301, 3300, 3000))
        assertEquals(LOW, BatteryLogic.level(3300, 3300, 3000))
        assertEquals(LOW, BatteryLogic.level(3001, 3300, 3000))
        assertEquals(CRITICAL, BatteryLogic.level(3000, 3300, 3000))
    }

    @Test fun hysteresisPreventsFlapping() {
        // war LOW, steigt knapp ueber die Schwelle -> bleibt LOW
        assertEquals(LOW, BatteryLogic.levelWithHysteresis(3350, 3300, 3000, LOW))
        assertEquals(OK, BatteryLogic.levelWithHysteresis(3401, 3300, 3000, LOW))
        // war CRITICAL, steigt knapp ueber kritisch -> bleibt CRITICAL
        assertEquals(CRITICAL, BatteryLogic.levelWithHysteresis(3050, 3300, 3000, CRITICAL))
        assertEquals(LOW, BatteryLogic.levelWithHysteresis(3150, 3300, 3000, CRITICAL))
        assertEquals(OK, BatteryLogic.levelWithHysteresis(3500, 3300, 3000, CRITICAL))
        // Verschlechterung wirkt sofort
        assertEquals(CRITICAL, BatteryLogic.levelWithHysteresis(2990, 3300, 3000, LOW))
        assertEquals(LOW, BatteryLogic.levelWithHysteresis(3290, 3300, 3000, OK))
    }

    @Test fun staleness() {
        assertTrue(BatteryLogic.isStale(info(4000, age = 49 * 3600), now))
        assertEquals(false, BatteryLogic.isStale(info(4000, age = 3600), now))
        assertTrue(BatteryLogic.isStale(info(null), now))
    }

    @Test fun trendFromLinearDecline() {
        // 7 Tage, alle 6 h, -50 mV/Tag
        val s = (0..28).map { i -> BatterySample(now - 7 * 86400 + i * 6 * 3600L, 4000 - (i * 6 * 50 / 24)) }
        assertEquals(-50.0, BatteryLogic.trendMvPerDay(s, now)!!, 1.0)
    }

    @Test fun trendNeedsEnoughData() {
        val few = (0..4).map { BatterySample(now - it * 3600L, 4000) }
        assertNull(BatteryLogic.trendMvPerDay(few, now))
        val shortSpan = (0..20).map { BatterySample(now - it * 600L, 4000) }
        assertNull("nur 3,3 h Spanne", BatteryLogic.trendMvPerDay(shortSpan, now))
    }

    @Test fun trendIgnoresOldSamples() {
        val old = (0..10).map { BatterySample(now - 20 * 86400 + it * 3600L, 3000) }
        val recent = (0..24).map { BatterySample(now - 2 * 86400 + it * 7200L, 4000) }
        assertEquals(0.0, BatteryLogic.trendMvPerDay(old + recent, now)!!, 0.01)
    }

    @Test fun transitionsOnlyOnWorsening() {
        val d = BatteryTransitionDetector
        assertEquals(BatteryTransition(null, OK), d.detect(null, info(4000), now))
        assertEquals(BatteryTransition(null, LOW), d.detect(null, info(3200), now))
        assertEquals(BatteryTransition(BatteryEvent.BecameLow, LOW), d.detect(OK, info(3200), now))
        assertEquals(BatteryTransition(BatteryEvent.BecameCritical, CRITICAL), d.detect(LOW, info(2900), now))
        assertEquals(BatteryTransition(BatteryEvent.BecameCritical, CRITICAL), d.detect(OK, info(2900), now))
        assertEquals("Erholung still", BatteryTransition(null, OK), d.detect(LOW, info(4000), now))
        assertEquals(BatteryTransition(null, LOW), d.detect(LOW, info(3250), now))
    }

    @Test fun staleOrMissingDataKeepsState() {
        val d = BatteryTransitionDetector
        assertEquals(BatteryTransition(null, OK), d.detect(OK, info(2900, age = 3 * 86400), now))
        assertEquals(BatteryTransition(null, LOW), d.detect(LOW, info(null), now))
        assertEquals(BatteryTransition(null, null), d.detect(null, info(null), now))
    }

    @Test fun solarDayCycleProducesSingleAlert() {
        // Spannung pendelt um die Schwelle: nur eine Meldung
        var lvl: BatteryLevel? = OK
        var alerts = 0
        for (mv in listOf(3320, 3290, 3330, 3280, 3360, 3295, 3380, 3310)) {
            val t = BatteryTransitionDetector.detect(lvl, info(mv), now)
            if (t.event != null) alerts++
            lvl = t.levelNow
        }
        assertEquals(1, alerts)
    }
}
