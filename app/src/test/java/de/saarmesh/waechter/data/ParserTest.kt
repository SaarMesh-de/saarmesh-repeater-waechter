package de.saarmesh.waechter.data

import de.saarmesh.waechter.data.remote.AusfallParser
import de.saarmesh.waechter.data.remote.Endpoints
import de.saarmesh.waechter.domain.ServerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserTest {
    private fun sample() = javaClass.classLoader!!.getResource("repeater_sample.json")!!.readText()

    @Test
    fun parsesRealSample() {
        val snap = AusfallParser.parseList(sample())
        assertEquals(1794809042L, snap.generated)
        assertEquals(21, snap.windowDays)
        // Eintrag mit ungueltigem Key wird verworfen
        assertEquals(6, snap.repeaters.size)
        val cycas = snap.repeaters.first { it.publicKey.startsWith("0c4ca55c") }
        assertEquals("📶 Cycas Rep SB", cycas.name)
        assertEquals(ServerStatus.AKTIV, cycas.serverStatus)
        assertEquals(21, cycas.daily.size)
        assertEquals(10800L, cycas.quietAfter)
        assertEquals(21600L, cycas.downAfter)
        assertEquals(49.21848, cycas.lat!!, 1e-9)
    }

    @Test
    fun replacedAndFewData() {
        val snap = AusfallParser.parseList(sample())
        val igel = snap.repeaters.first { it.publicKey.startsWith("20876491") }
        assertEquals(ServerStatus.ERSETZT, igel.serverStatus)
        assertEquals("06406a7181baee4758e3cc76f9e54ae9fb10fbaf49c2910b4d063d02830d8a08", igel.replacedByKey)
        val olewig = snap.repeaters.first { it.publicKey.startsWith("93e219be") }
        assertEquals(ServerStatus.WENIG, olewig.serverStatus)
        assertNull(olewig.downAfter)
        assertNull(olewig.quietAfter)
    }

    @Test
    fun toleratesUnknownValuesAndNormalizes() {
        val snap = AusfallParser.parseList(sample())
        val odd = snap.repeaters.first { it.publicKey.startsWith("aabbccdd") }
        assertEquals("Key wird kleingeschrieben", "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899", odd.publicKey)
        assertEquals("leerer Name -> Key-Praefix", "aabbccdd", odd.name)
        assertEquals(ServerStatus.UNBEKANNTER_WERT, odd.serverStatus)
        assertFalse("0/0 ist keine Position", odd.hasPosition)
        assertTrue(odd.daily.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMissingSchema() {
        AusfallParser.parseList("""{"generated": 1, "repeaters": []}""")
    }

    @Test
    fun parsesVerlauf() {
        val h = AusfallParser.parseVerlauf(
            """{"schema":1,"pk":"1F869D80948DCB3274A235ACBE4739235CE8C2144BFD3FF08E9EC0A737D80152","from":1792993965,"step":3600,"hourly":[9,12,0,11]}""",
        )
        assertEquals("1f869d80948dcb3274a235acbe4739235ce8c2144bfd3ff08e9ec0a737d80152", h.publicKey)
        assertEquals(1792993965L, h.from)
        assertEquals(3600L, h.stepSeconds)
        assertEquals(listOf(9, 12, 0, 11), h.values)
    }

    @Test
    fun endpointsNormalizeBaseUrl() {
        assertEquals("https://live.saarmesh.de/ausfall/repeater.json", Endpoints.repeaterList("https://live.saarmesh.de/"))
        assertEquals("https://live.saarmesh.de/ausfall/repeater.json", Endpoints.repeaterList("  "))
        assertEquals("http://10.0.0.5:8080/ausfall/verlauf/abc.json", Endpoints.verlauf("http://10.0.0.5:8080", "ABC"))
        assertEquals("https://live.saarmesh.de/#/nodes/abc", Endpoints.node("https://live.saarmesh.de", "abc"))
    }
}
