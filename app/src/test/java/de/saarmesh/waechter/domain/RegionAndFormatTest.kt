package de.saarmesh.waechter.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RegionAndFormatTest {
    @Test fun regionFromNamePrefix() {
        assertEquals(Region.SAARLAND, RegionResolver.resolve("DE-SL-66127-1", null, null))
        assertEquals(Region.RHEINLAND_PFALZ, RegionResolver.resolve("DE-RP-DAU-OBSERVER-2-1", null, null))
        assertEquals(Region.RHEINLAND_PFALZ, RegionResolver.resolve("DE-RLP-54295 Olewig", null, null))
        assertEquals(Region.LUXEMBURG, RegionResolver.resolve("LX7JC - Observer", null, null))
        assertEquals(Region.LUXEMBURG, RegionResolver.resolve("LX-MQTT-HeLe-Observer", null, null))
    }

    @Test fun regionFromPostalCode() {
        assertEquals(Region.RHEINLAND_PFALZ, RegionResolver.resolve("D-54298 Igel Rep. V4", null, null))
        assertEquals(Region.SAARLAND, RegionResolver.resolve("D-66111 Irgendwas", null, null))
    }

    @Test fun regionFromPosition() {
        assertEquals(Region.SAARLAND, RegionResolver.resolve("SaarMesh.de RoofRPT", 49.23091, 6.88944))
        assertEquals(Region.LUXEMBURG, RegionResolver.resolve("Bascharage Observer MNL", 49.567, 5.91))
        assertEquals(Region.LOTHRINGEN, RegionResolver.resolve("Metz Repeater", 49.119, 6.175))
        assertEquals(Region.RHEINLAND_PFALZ, RegionResolver.resolve("Trier Rep", 49.75, 6.64))
        assertEquals(Region.SONSTIGE, RegionResolver.resolve("Hausach Repeater4", 48.339, 8.153))
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
