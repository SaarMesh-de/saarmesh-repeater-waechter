package de.saarmesh.waechter.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusLogicTest {
    private val now = 1_800_000_000L
    private val h = 3600L

    private fun rep(
        silence: Long,
        server: ServerStatus = ServerStatus.AKTIV,
        quiet: Long? = 3 * h,
        down: Long? = 6 * h,
    ) = Repeater(
        publicKey = "a".repeat(64), name = "Test", serverStatus = server, lastSeen = now - silence,
        via = null, art = null, sightings = 100, lat = null, lon = null, firstSeen = null,
        quietAfter = quiet, downAfter = down, typicalGap = 600, rareGap = 1200,
        replacedByKey = null, replacedByName = null, daily = emptyList(),
    )

    @Test fun onlineBelowQuiet() = assertEquals(RepeaterStatus.ONLINE, StatusLogic.evaluate(rep(2 * h), now))
    @Test fun onlineExactlyAtQuiet() = assertEquals(RepeaterStatus.ONLINE, StatusLogic.evaluate(rep(3 * h), now))
    @Test fun overdueBetweenQuietAndDown() = assertEquals(RepeaterStatus.UEBERFAELLIG, StatusLogic.evaluate(rep(4 * h), now))
    @Test fun overdueExactlyAtDown() = assertEquals(RepeaterStatus.UEBERFAELLIG, StatusLogic.evaluate(rep(6 * h), now))
    @Test fun offlineAboveDown() = assertEquals(RepeaterStatus.OFFLINE, StatusLogic.evaluate(rep(6 * h + 1), now))

    @Test fun usesServerSpecificThresholds() {
        // Ruhiger Standort: Server-Schwelle 23 h -> 10 h Stille ist nur ueberfaellig
        val r = rep(10 * h, quiet = 8 * h, down = 23 * h)
        assertEquals(RepeaterStatus.UEBERFAELLIG, StatusLogic.evaluate(r, now))
    }

    @Test fun defaultsWhenServerThresholdsMissing() {
        assertEquals(RepeaterStatus.ONLINE, StatusLogic.evaluate(rep(2 * h, quiet = null, down = null), now))
        assertEquals(RepeaterStatus.UEBERFAELLIG, StatusLogic.evaluate(rep(4 * h, quiet = null, down = null), now))
        assertEquals(RepeaterStatus.OFFLINE, StatusLogic.evaluate(rep(7 * h, quiet = null, down = null), now))
    }

    @Test fun serverAusIsOfflineEvenIfAppThresholdDiffers() =
        assertEquals(RepeaterStatus.OFFLINE, StatusLogic.evaluate(rep(5 * h, ServerStatus.AUS), now))

    @Test fun serverWegIsOffline() =
        assertEquals(RepeaterStatus.OFFLINE, StatusLogic.evaluate(rep(8 * 24 * h, ServerStatus.WEG), now))

    @Test fun serverUnknownStaysUnknown() =
        assertEquals(RepeaterStatus.UNBEKANNT, StatusLogic.evaluate(rep(10 * h, ServerStatus.UNBEKANNT), now))

    @Test fun replacedAlwaysReplaced() {
        assertEquals(RepeaterStatus.ERSETZT, StatusLogic.evaluate(rep(10 * 24 * h, ServerStatus.ERSETZT), now))
        assertEquals(RepeaterStatus.ERSETZT, StatusLogic.evaluate(rep(10 * 24 * h, ServerStatus.ERSETZT), now, offlineAfterHours = 2))
    }

    @Test fun fewDataWithoutCustomThreshold() =
        assertEquals(RepeaterStatus.WENIG_DATEN, StatusLogic.evaluate(rep(30 * h, ServerStatus.WENIG, null, null), now))

    @Test fun customThresholdOverridesServer() {
        // eigener Wert 2 h: nach 3 h offline, obwohl Server noch "aktiv"
        assertEquals(RepeaterStatus.OFFLINE, StatusLogic.evaluate(rep(3 * h), now, offlineAfterHours = 2))
        // ueberfaellig ab der Haelfte des eigenen Werts
        assertEquals(RepeaterStatus.UEBERFAELLIG, StatusLogic.evaluate(rep(90 * 60), now, offlineAfterHours = 2))
        assertEquals(RepeaterStatus.ONLINE, StatusLogic.evaluate(rep(30 * 60), now, offlineAfterHours = 2))
    }

    @Test fun customThresholdLongerThanServer() {
        // Server wuerde nach 6 h "aus" sagen, eigener Wert 24 h -> nach 10 h nur ueberfaellig
        assertEquals(RepeaterStatus.UEBERFAELLIG, StatusLogic.evaluate(rep(10 * h, ServerStatus.AUS), now, offlineAfterHours = 24))
        assertEquals(RepeaterStatus.OFFLINE, StatusLogic.evaluate(rep(25 * h, ServerStatus.AUS), now, offlineAfterHours = 24))
    }

    @Test fun customThresholdAppliesToFewData() =
        assertEquals(RepeaterStatus.OFFLINE, StatusLogic.evaluate(rep(30 * h, ServerStatus.WENIG, null, null), now, offlineAfterHours = 24))

    @Test fun zeroOrNegativeCustomIgnored() =
        assertEquals(RepeaterStatus.ONLINE, StatusLogic.evaluate(rep(2 * h), now, offlineAfterHours = 0))

    @Test fun futureLastSeenCountsAsJustSeen() =
        assertEquals(RepeaterStatus.ONLINE, StatusLogic.evaluate(rep(-600), now, offlineAfterHours = 1))
}
