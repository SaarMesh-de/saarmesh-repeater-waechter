package de.saarmesh.waechter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransitionDetectorTest {
    private fun d(was: Boolean?, s: RepeaterStatus) = TransitionDetector.detect(was, s)

    @Test fun firstEvaluationOnlyRemembers() {
        assertEquals(Transition(null, true), d(null, RepeaterStatus.OFFLINE))
        assertEquals(Transition(null, false), d(null, RepeaterStatus.ONLINE))
        assertEquals(Transition(null, false), d(null, RepeaterStatus.UNBEKANNT))
    }

    @Test fun goesOffline() = assertEquals(Transition(StatusEvent.WentOffline, true), d(false, RepeaterStatus.OFFLINE))

    @Test fun comesBackOnline() {
        assertEquals(Transition(StatusEvent.BackOnline, false), d(true, RepeaterStatus.ONLINE))
        assertEquals("ueberfaellig zaehlt als wieder da", Transition(StatusEvent.BackOnline, false), d(true, RepeaterStatus.UEBERFAELLIG))
    }

    @Test fun noRepeatWhileStillDown() = assertEquals(Transition(null, true), d(true, RepeaterStatus.OFFLINE))
    @Test fun noRepeatWhileStillUp() = assertEquals(Transition(null, false), d(false, RepeaterStatus.ONLINE))
    @Test fun overdueIsNoAlarm() = assertEquals(Transition(null, false), d(false, RepeaterStatus.UEBERFAELLIG))

    @Test fun unclearStatesKeepMemory() {
        for (s in listOf(RepeaterStatus.UNBEKANNT, RepeaterStatus.ERSETZT, RepeaterStatus.WENIG_DATEN)) {
            assertNull(d(true, s).event)
            assertEquals(true, d(true, s).wasDownNow)
            assertNull(d(false, s).event)
            assertEquals(false, d(false, s).wasDownNow)
        }
    }

    @Test fun fullCycleProducesExactlyTwoEvents() {
        val seq = listOf(
            RepeaterStatus.ONLINE, RepeaterStatus.UEBERFAELLIG, RepeaterStatus.OFFLINE, RepeaterStatus.OFFLINE,
            RepeaterStatus.UNBEKANNT, RepeaterStatus.OFFLINE, RepeaterStatus.UEBERFAELLIG, RepeaterStatus.ONLINE,
        )
        var was: Boolean? = null
        val events = mutableListOf<StatusEvent>()
        for (s in seq) {
            val t = d(was, s)
            t.event?.let { events += it }
            was = t.wasDownNow
        }
        assertEquals(listOf(StatusEvent.WentOffline, StatusEvent.BackOnline), events)
    }
}
