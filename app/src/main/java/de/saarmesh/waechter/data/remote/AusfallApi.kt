package de.saarmesh.waechter.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * Nur lesende Zugriffe auf statische JSON-Dateien des Ausfall-Dienstes.
 * Die Server-URL ist einstellbar, daher werden volle URLs uebergeben.
 * Die App sendet nichts ins Mesh und kennt keine schreibenden Endpunkte.
 */
interface AusfallApi {
    @GET
    suspend fun get(@Url url: String): ResponseBody
}

object Endpoints {
    fun normalize(base: String): String = base.trim().trimEnd('/').ifEmpty { "https://live.saarmesh.de" }
    fun repeaterList(base: String) = "${normalize(base)}/ausfall/repeater.json"
    fun verlauf(base: String, pk: String) = "${normalize(base)}/ausfall/verlauf/${pk.lowercase()}.json"

    // Deeplinks auf die Webseiten
    fun node(base: String, pk: String) = "${normalize(base)}/#/nodes/${pk.lowercase()}"
    fun ausfall(base: String) = "${normalize(base)}/ausfall/"
    fun relevanz(base: String) = "${normalize(base)}/relevanz/"
    fun clock(base: String) = "${normalize(base)}/clock/"
    fun positionen(base: String) = "${normalize(base)}/positionen/"
    fun statistik(base: String) = "${normalize(base)}/statistik/"
    fun werkzeuge(base: String) = "${normalize(base)}/werkzeuge/"
}
