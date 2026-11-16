package de.saarmesh.waechter.data.remote

import de.saarmesh.waechter.domain.Repeater
import de.saarmesh.waechter.domain.RepeaterSnapshot
import de.saarmesh.waechter.domain.ServerStatus
import de.saarmesh.waechter.domain.SightingHistory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** live.saarmesh.de/ausfall/repeater.json (schema 1), erzeugt vom saarmesh-ausfall-Dienst. */
@Serializable
data class RepeaterListDto(
    val schema: Int = 0,
    val generated: Long,
    @SerialName("window_days") val windowDays: Int = 21,
    @SerialName("daily_from") val dailyFrom: Long = 0,
    val repeaters: List<RepeaterDto> = emptyList(),
)

@Serializable
data class RepeaterDto(
    val pk: String,
    val name: String? = null,
    val status: String? = null,
    val last: Long,
    val via: String? = null,
    val art: String? = null,
    val n: Int = 0,
    val lat: Double? = null,
    val lon: Double? = null,
    @SerialName("first_seen") val firstSeen: Long? = null,
    val quiet: Long? = null,
    val down: Long? = null,
    val g50: Long? = null,
    val g95: Long? = null,
    val watching: Int? = null,
    @SerialName("by_pk") val byPk: String? = null,
    val by: String? = null,
    val daily: List<Int> = emptyList(),
)

/** live.saarmesh.de/ausfall/verlauf/<pk>.json */
@Serializable
data class VerlaufDto(
    val schema: Int = 0,
    val pk: String,
    val from: Long,
    val step: Long = 3600,
    val hourly: List<Int> = emptyList(),
)

object AusfallParser {
    /** Schemaversion, die diese App versteht. Hoehere Versionen sind abwaertskompatibel (nur neue Felder). */
    const val SUPPORTED_SCHEMA = 1

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun parseList(text: String): RepeaterSnapshot = json.decodeFromString<RepeaterListDto>(text).toDomain()
    fun parseVerlauf(text: String): SightingHistory = json.decodeFromString<VerlaufDto>(text).toDomain()
}

fun RepeaterListDto.toDomain(): RepeaterSnapshot {
    require(schema >= 1) { "Unbekanntes Format (schema=$schema)" }
    return RepeaterSnapshot(
        generated = generated,
        windowDays = windowDays,
        dailyFrom = dailyFrom,
        repeaters = repeaters.mapNotNull { it.toDomainOrNull() },
    )
}

private val HEX_KEY = Regex("^[0-9a-f]{64}$")

fun RepeaterDto.toDomainOrNull(): Repeater? {
    val key = pk.lowercase()
    if (!HEX_KEY.matches(key)) return null
    val validPos = lat != null && lon != null && !(kotlin.math.abs(lat) < 0.01 && kotlin.math.abs(lon) < 0.01)
    return Repeater(
        publicKey = key,
        name = name?.takeIf { it.isNotBlank() } ?: key.take(8),
        serverStatus = ServerStatus.parse(status),
        lastSeen = last,
        via = via,
        art = art,
        sightings = n,
        lat = if (validPos) lat else null,
        lon = if (validPos) lon else null,
        firstSeen = firstSeen,
        quietAfter = quiet,
        downAfter = down,
        typicalGap = g50,
        rareGap = g95,
        replacedByKey = byPk?.lowercase(),
        replacedByName = by,
        daily = daily,
    )
}

fun VerlaufDto.toDomain() = SightingHistory(pk.lowercase(), from, step, hourly)
