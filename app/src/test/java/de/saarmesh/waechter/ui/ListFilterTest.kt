package de.saarmesh.waechter.ui

import de.saarmesh.waechter.domain.Region
import de.saarmesh.waechter.domain.Repeater
import de.saarmesh.waechter.domain.RepeaterStatus
import de.saarmesh.waechter.domain.ServerStatus
import de.saarmesh.waechter.ui.list.ListFilter
import de.saarmesh.waechter.ui.list.RepeaterItem
import de.saarmesh.waechter.ui.list.StatusFilter
import de.saarmesh.waechter.ui.list.applyFilter
import de.saarmesh.waechter.ui.settings.isValidServerUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListFilterTest {
    private fun item(name: String, status: RepeaterStatus, region: Region = Region.SAARLAND, fav: Boolean = false) = RepeaterItem(
        Repeater(
            name.lowercase().padEnd(64, '0').take(64).map { if (it in '0'..'9' || it in 'a'..'f') it else 'b' }.joinToString(""),
            name, ServerStatus.AKTIV, 0, null, null, 0, null, null, null, null, null, null, null, null, null, emptyList(),
        ),
        status, region, fav,
    )

    private val items = listOf(
        item("Bravo", RepeaterStatus.ONLINE),
        item("Alpha", RepeaterStatus.OFFLINE, Region.LUXEMBURG),
        item("Charlie", RepeaterStatus.UEBERFAELLIG, fav = true),
        item("Delta", RepeaterStatus.ONLINE, fav = true),
        item("Echo", RepeaterStatus.UNBEKANNT),
    )

    @Test fun sortsFavoritesThenSeverityThenName() {
        assertEquals(listOf("Charlie", "Delta", "Alpha", "Echo", "Bravo"), applyFilter(items, ListFilter()).map { it.repeater.name })
    }

    @Test fun filtersByStatus() {
        assertEquals(listOf("Alpha"), applyFilter(items, ListFilter(status = StatusFilter.OFFLINE)).map { it.repeater.name })
        assertEquals(listOf("Echo"), applyFilter(items, ListFilter(status = StatusFilter.SONSTIGE)).map { it.repeater.name })
    }

    @Test fun filtersByRegionFavoritesAndQuery() {
        assertEquals(listOf("Alpha"), applyFilter(items, ListFilter(region = Region.LUXEMBURG)).map { it.repeater.name })
        assertEquals(listOf("Charlie", "Delta"), applyFilter(items, ListFilter(onlyFavorites = true)).map { it.repeater.name })
        assertEquals(listOf("Delta"), applyFilter(items, ListFilter(query = " ELT ")).map { it.repeater.name })
    }

    @Test fun serverUrlValidation() {
        assertTrue(isValidServerUrl("https://live.saarmesh.de"))
        assertTrue(isValidServerUrl("https://live.saarmesh.de/"))
        assertTrue(isValidServerUrl("http://192.168.178.10:8080"))
        assertTrue(isValidServerUrl(""))
        assertFalse(isValidServerUrl("live.saarmesh.de"))
        assertFalse(isValidServerUrl("https://live.saarmesh.de/ausfall/"))
        assertFalse(isValidServerUrl("ftp://x"))
    }
}
