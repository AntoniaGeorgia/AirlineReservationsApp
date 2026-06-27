package com.example.airlinereservationsapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

object FlightDataRepository {

    suspend fun getLiveFlights(): List<GlobeRenderer.Airplane> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://opensky-network.org/api/states/all?lamin=35.0&lomin=-10.0&lamax=70.0&lomax=40.0")
            val json = JSONObject(url.readText())
            val states = json.getJSONArray("states")
            val planes = mutableListOf<GlobeRenderer.Airplane>()

            for (i in 0 until minOf(states.length(), 200)) {
                val state = states.getJSONArray(i)
                if (state.isNull(5) || state.isNull(6)) continue
                val lon = state.getDouble(5).toFloat()
                val lat = state.getDouble(6).toFloat()
                val heading = if (!state.isNull(10)) state.getDouble(10).toFloat() else 0f
                val callsign = if (!state.isNull(1)) state.getString(1).trim() else "???"
                planes.add(GlobeRenderer.Airplane(lat, lon, heading, callsign))
            }
            planes
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getFlights(
        from: String,
        to: String,
        date: String
    ): List<Flight> = withContext(Dispatchers.IO) {
        try {
            val response = RetrofitInstance.api.searchFlights(
                origin = from,
                destination = to,
                date = date.ifBlank { null }
            )
            if (response.isSuccessful) {
                response.body()?.flatMap { it.flights } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}