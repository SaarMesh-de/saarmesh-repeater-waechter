package de.saarmesh.waechter.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
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

    /** Wie [get], aber ohne Exception bei HTTP-Fehlern (z. B. 404 fuer unbekannte Knoten). */
    @GET
    suspend fun getResponse(@Url url: String): Response<ResponseBody>
}

object Endpoints {
    fun normalize(base: String): String = base.trim().trimEnd('/').ifEmpty { "https://live.saarmesh.de" }
    fun repeaterList(base: String) = "${normalize(base)}/ausfall/repeater.json"
    fun verlauf(base: String, pk: String) = "${normalize(base)}/ausfall/verlauf/${pk.lowercase()}.json"
    /** CoreScope-API: Akku-Verlauf. Die Werte liefern Abfrage-Stationen der Betreiber, nicht die App. */
    fun battery(base: String, pk: String, days: Int = 30) = "${normalize(base)}/api/nodes/${pk.lowercase()}/battery?days=$days"

    // Deeplinks auf die Webseiten
    fun node(base: String, pk: String) = "${normalize(base)}/#/nodes/${pk.lowercase()}"
    fun ausfall(base: String) = "${normalize(base)}/ausfall/"
    fun relevanz(base: String) = "${normalize(base)}/relevanz/"
    fun clock(base: String) = "${normalize(base)}/clock/"
    fun positionen(base: String) = "${normalize(base)}/positionen/"
    fun statistik(base: String) = "${normalize(base)}/statistik/"
    fun werkzeuge(base: String) = "${normalize(base)}/werkzeuge/"
}
