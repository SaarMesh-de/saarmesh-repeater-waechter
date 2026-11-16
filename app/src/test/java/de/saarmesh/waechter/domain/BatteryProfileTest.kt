package de.saarmesh.waechter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryProfileTest {
    private val server = BatteryThresholds(3300, 3000)
    private val L = BatteryThresholdLogic

    @Test fun presets() {
        assertEquals(server, L.effective(server, BatteryProfile.SERVER, null, null))
        assertEquals(BatteryThresholds(3500, 3300), L.effective(server, BatteryProfile.LI_ION, null, null))
        assertEquals(BatteryThresholds(3100, 2900), L.effective(server, BatteryProfile.LIFEPO4, null, null))
    }

    @Test fun presetIgnoresStaleCustomValues() =
        assertEquals(BatteryThresholds(3100, 2900), L.effective(server, BatteryProfile.LIFEPO4, 4000, 3800))

    @Test fun customValues() {
        assertEquals(BatteryThresholds(3900, 3700), L.effective(server, BatteryProfile.CUSTOM, 3900, 3700))
        assertEquals("kritisch >= schwach -> Server", server, L.effective(server, BatteryProfile.CUSTOM, 3500, 3500))
        assertEquals("fehlend -> Server", server, L.effective(server, BatteryProfile.CUSTOM, null, 3000))
        assertEquals("ausserhalb -> Server", server, L.effective(server, BatteryProfile.CUSTOM, 20000, 3000))
    }

    @Test fun lifepo4AtNominalIsNotLowButWouldBeWithServerThresholds() {
        // 3,25 V ist fuer LiFePO4 voellig normal, mit Server-Schwellen waere es "schwach"
        assertEquals(BatteryLevel.LOW, BatteryLogic.level(3250, server.lowMv, server.criticalMv))
        val t = L.effective(server, BatteryProfile.LIFEPO4, null, null)
        assertEquals(BatteryLevel.OK, BatteryLogic.level(3250, t.lowMv, t.criticalMv))
    }

    @Test fun plausibility() {
        assertTrue("4,16 V bei LiFePO4 unmoeglich", L.implausible(BatteryProfile.LIFEPO4, 4160))
        assertFalse(L.implausible(BatteryProfile.LIFEPO4, 3650))
        assertFalse(L.implausible(BatteryProfile.LI_ION, 4200))
        assertTrue(L.implausible(BatteryProfile.LI_ION, 5000))
        assertFalse(L.implausible(BatteryProfile.SERVER, 13000))
        assertFalse(L.implausible(BatteryProfile.CUSTOM, 13000))
        assertFalse(L.implausible(BatteryProfile.LIFEPO4, null))
    }

    @Test fun parsesVoltsAndMillivolts() {
        assertEquals(3200, L.parseVoltsOrMv("3,2"))
        assertEquals(3200, L.parseVoltsOrMv("3.20 V"))
        assertEquals(3150, L.parseVoltsOrMv(" 3150 "))
        assertEquals(12800, L.parseVoltsOrMv("12,8"))
        assertNull(L.parseVoltsOrMv("abc"))
        assertNull(L.parseVoltsOrMv("1,5"))
        assertNull(L.parseVoltsOrMv("99999"))
    }

    @Test fun favoriteAppliesProfile() {
        val f = Favorite("a".repeat(64), null, true, null, null, BatteryProfile.LIFEPO4)
        assertEquals(BatteryThresholds(3100, 2900), f.batteryThresholds(server))
        assertEquals(server, Favorite("b".repeat(64), null, true, null).batteryThresholds(server))
    }

    @Test fun parseProfileName() {
        assertEquals(BatteryProfile.LI_ION, BatteryProfile.parse("LI_ION"))
        assertEquals(BatteryProfile.SERVER, BatteryProfile.parse(null))
        assertEquals(BatteryProfile.SERVER, BatteryProfile.parse("unbekannt"))
    }
}
