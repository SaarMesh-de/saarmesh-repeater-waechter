package de.saarmesh.waechter.domain

/** Grobe Region fuer den Listenfilter (SaarLorLux). Heuristik aus Name und Position. */
enum class Region { SAARLAND, RHEINLAND_PFALZ, LUXEMBURG, LOTHRINGEN, SONSTIGE }

object RegionResolver {
    private val prefixes = listOf(
        Regex("^(DE|D)[-_ ]?SL\\b", RegexOption.IGNORE_CASE) to Region.SAARLAND,
        Regex("^(DE|D)[-_ ]?(RP|RLP)\\b", RegexOption.IGNORE_CASE) to Region.RHEINLAND_PFALZ,
        Regex("^(LU|LX)\\b|^LX\\d", RegexOption.IGNORE_CASE) to Region.LUXEMBURG,
        Regex("^FR?[-_ ]?(57|54|55|88|LOR)\\b|^FR\\b", RegexOption.IGNORE_CASE) to Region.LOTHRINGEN,
    )
    private val postal = Regex("\\bD(?:E)?[-_ ]?(?:[A-Z]{2,3}[-_ ])?(\\d{5})\\b", RegexOption.IGNORE_CASE)

    fun resolve(name: String, lat: Double?, lon: Double?): Region {
        val n = name.trim()
        prefixes.firstOrNull { it.first.containsMatchIn(n) }?.let { return it.second }
        postal.find(n)?.groupValues?.get(1)?.toIntOrNull()?.let { plz ->
            fromPostalCode(plz)?.let { return it }
        }
        if (lat != null && lon != null) return fromPosition(lat, lon)
        return Region.SONSTIGE
    }

    /** Sehr grobe Zuordnung deutscher PLZ-Bereiche. */
    fun fromPostalCode(plz: Int): Region? = when (plz) {
        in 66001..66459, in 66501..66509, in 66538..66649, in 66661..66839 -> Region.SAARLAND
        in 54000..57999, in 66460..66500, in 66840..67999, in 76700..76899 -> Region.RHEINLAND_PFALZ
        else -> null
    }

    fun fromPosition(lat: Double, lon: Double): Region = when {
        lat in 49.44..50.19 && lon in 5.73..6.53 -> Region.LUXEMBURG
        lat in 49.11..49.64 && lon in 6.36..7.41 -> Region.SAARLAND
        lat < 49.55 && lon in 5.4..7.7 && !(lat > 49.1 && lon > 7.4) -> Region.LOTHRINGEN
        lat in 48.9..50.95 && lon in 6.1..8.6 -> Region.RHEINLAND_PFALZ
        else -> Region.SONSTIGE
    }
}
