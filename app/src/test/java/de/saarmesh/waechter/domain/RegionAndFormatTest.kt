package de.saarmesh.waechter.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RegionAndFormatTest {
    @Test fun regionFromNamePrefix() {
        assertEquals(Region.SAARLAND, RegionResolver.resolve("DE-SL-66127-1", null, null))
        assertEquals(Region.RHEINLAND_PFALZ, RegionResolver.resolve("DE-RP-TEST-OBSERVER-1", null, null))
        assertEquals(Region.RHEINLAND_PFALZ, RegionResolver.resolve("DE-RLP-54295 Testort", null, null))
        assertEquals(Region.LUXEMBURG, RegionResolver.resolve("LX9AA - Observer", null, null))
        assertEquals(Region.LUXEMBURG, RegionResolver.resolve("LX-MQTT-Test-Observer", null, null))
    }

    @Test fun regionFromPostalCode() {
        assertEquals(Region.RHEINLAND_PFALZ, RegionResolver.resolve("D-54298 Testort Rep", null, null))
        assertEquals(Region.SAARLAND, RegionResolver.resolve("D-66111 Testort", null, null))
    }

    @Test fun regionFromPosition() {
        assertEquals(Region.SAARLAND, RegionResolver.resolve("SaarMesh.de RoofRPT", 49.23091, 6.88944))
        assertEquals(Region.LUXEMBURG, RegionResolver.resolve("Observer Luxemburg", 49.567, 5.91))
        assertEquals(Region.LOTHRINGEN, RegionResolver.resolve("Repeater Metz", 49.119, 6.175))
        assertEquals(Region.RHEINLAND_PFALZ, RegionResolver.resolve("Repeater Trier", 49.75, 6.64))
        assertEquals(Region.SONSTIGE, RegionResolver.resolve("Repeater Schwarzwald", 48.339, 8.153))
        assertEquals(Region.SONSTIGE, RegionResolver.resolve("ohne Position", null, null))
    }

    @Test fun agoBuckets() {
        assertEquals(Ago.JustNow, AgoFormat.of(30))
        assertEquals(Ago.Minutes(5), AgoFormat.of(5 * 60 + 10))
        assertEquals(Ago.Hours(3), AgoFormat.of(3 * 3600 + 59 * 60))
        assertEquals(Ago.Hours(47), AgoFormat.of(47 * 3600))
        assertEquals(Ago.Days(2), AgoFormat.of(48 * 3600))
        assertEquals(Ago.JustNow, AgoFormat.of(-100))
    }
}
