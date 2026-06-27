package com.example.airlinereservationsapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class WeekendDestination(
    val city: String,
    val code: String,
    val country: String,
    val emoji: String,
    val weatherCity: String
)

enum class WeatherMood(val label: String, val emoji: String) {
    SUNNY("Soare", "☀️"),
    BEACH("Plaja", "🏖️"),
    MILD("Placut", "🌤️"),
    RAINY("Ploaie", "🌧️"),
    COLD("Frig", "❄️"),
    SNOW("Zapada", "⛄")
}

class WeekendTripActivity : AppCompatActivity() {

    private val apiKey = "61a0f23f80899d64926b5cec453c2392"
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // Job activ — anulat la fiecare selectie noua de vreme
    private var searchJob: Job? = null

    private val destinations = listOf(
        WeekendDestination("Londra", "LHR", "UK", "🎡", "London"),
        WeekendDestination("Paris", "CDG", "Franta", "🗼", "Paris"),
        WeekendDestination("Viena", "VIE", "Austria", "🎶", "Vienna"),
        WeekendDestination("Frankfurt", "FRA", "Germania", "🏙️", "Frankfurt"),
        WeekendDestination("Barcelona", "BCN", "Spania", "🏖️", "Barcelona"),
        WeekendDestination("Roma", "FCO", "Italia", "🏛️", "Rome"),
        WeekendDestination("Amsterdam", "AMS", "Olanda", "🌷", "Amsterdam"),
        WeekendDestination("Atena", "ATH", "Grecia", "🏺", "Athens"),
        WeekendDestination("Dubai", "DXB", "UAE", "🌴", "Dubai"),
        WeekendDestination("Lisabona", "LIS", "Portugalia", "🌊", "Lisbon"),
        WeekendDestination("Madrid", "MAD", "Spania", "💃", "Madrid"),
        WeekendDestination("Praga", "PRG", "Cehia", "🏰", "Prague"),
        WeekendDestination("Budapesta", "BUD", "Ungaria", "🌉", "Budapest"),
        WeekendDestination("Copenhaga", "CPH", "Danemarca", "🧜", "Copenhagen"),
        WeekendDestination("Stockholm", "ARN", "Suedia", "❄️", "Stockholm"),
        WeekendDestination("Oslo", "OSL", "Norvegia", "🏔️", "Oslo"),
        WeekendDestination("Helsinki", "HEL", "Finlanda", "🌲", "Helsinki"),
        WeekendDestination("Zurich", "ZRH", "Elvetia", "🏔️", "Zurich"),
        WeekendDestination("Bruxelles", "BRU", "Belgia", "🍫", "Brussels"),
        WeekendDestination("Tenerife", "TFS", "Spania", "☀️", "Santa Cruz de Tenerife"),
        WeekendDestination("Mallorca", "PMI", "Spania", "🏄", "Palma"),
        WeekendDestination("Mykonos", "JMK", "Grecia", "🌅", "Mykonos"),
        WeekendDestination("Reykjavik", "KEF", "Islanda", "🌋", "Reykjavik"),
        WeekendDestination("Tokyo", "NRT", "Japonia", "🗾", "Tokyo"),
        WeekendDestination("New York", "JFK", "SUA", "🗽", "New York"),
        WeekendDestination("Bangkok", "BKK", "Tailanda", "🛕", "Bangkok"),
        WeekendDestination("Bali", "DPS", "Indonezia", "🌺", "Denpasar"),
        WeekendDestination("Cairo", "CAI", "Egipt", "🐫", "Cairo"),
        WeekendDestination("Marrakech", "RAK", "Maroc", "🕌", "Marrakesh"),
        WeekendDestination("Cancun", "CUN", "Mexic", "🌮", "Cancun")
    )

    private lateinit var rvDestinations: RecyclerView
    private lateinit var pbLoading: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var tvHeader: TextView

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weekend_trip)

        rvDestinations = findViewById(R.id.rvWeekendDestinations)
        pbLoading      = findViewById(R.id.pbWeekendLoading)
        tvStatus       = findViewById(R.id.tvWeekendStatus)
        tvHeader       = findViewById(R.id.tvWeekendHeader)

        rvDestinations.layoutManager = LinearLayoutManager(this)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        setupMoodButtons()
    }

    private fun setupMoodButtons() {
        val container = findViewById<LinearLayout>(R.id.llMoodButtons)
        val moods = WeatherMood.entries

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = 8.dp }
        }
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        moods.forEachIndexed { index, mood ->
            val card = CardView(this).apply {
                radius = 16.dp.toFloat()
                cardElevation = 4.dp.toFloat()
                setCardBackgroundColor(0xFFF5F5F5.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                ).also { it.marginEnd = if (index % 3 != 2) 8.dp else 0 }
            }
            val inner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setPadding(8.dp, 12.dp, 8.dp, 12.dp)
            }
            inner.addView(TextView(this).apply {
                text = mood.emoji
                textSize = 24f
                gravity = android.view.Gravity.CENTER
            })
            inner.addView(TextView(this).apply {
                text = mood.label
                textSize = 12f
                setTextColor(0xFF333333.toInt())
                gravity = android.view.Gravity.CENTER
            })
            card.addView(inner)
            card.setOnClickListener { onMoodSelected(mood, card, container) }
            if (index < 3) row1.addView(card) else row2.addView(card)
        }

        container.addView(row1)
        container.addView(row2)
    }

    private fun onMoodSelected(mood: WeatherMood, selectedCard: CardView, container: LinearLayout) {
        tvHeader.text = "Destinatii cu ${mood.label} ${mood.emoji}"

        // Reset culori carduri
        for (i in 0 until container.childCount) {
            val row = container.getChildAt(i) as? LinearLayout ?: continue
            for (j in 0 until row.childCount) {
                (row.getChildAt(j) as? CardView)?.setCardBackgroundColor(0xFFF5F5F5.toInt())
            }
        }
        selectedCard.setCardBackgroundColor(0xFF0E9AAB.toInt())

        // Anulează căutarea anterioară dacă e în curs
        searchJob?.cancel()
        searchDestinationsByMood(mood)
    }

    private fun searchDestinationsByMood(mood: WeatherMood) {
        pbLoading.visibility      = View.VISIBLE
        rvDestinations.visibility = View.GONE
        tvStatus.visibility       = View.GONE

        searchJob = lifecycleScope.launch {
            try {
                // 1. Zbor-uri disponibile din OTP — toate 8 zile în paralel
                val today = LocalDate.now()
                val flightJobs = (0..7).map { i ->
                    async(Dispatchers.IO) {
                        try {
                            val date = today.plusDays(i.toLong()).format(formatter)
                            val response = RetrofitInstance.api.searchFlights(
                                origin = "OTP",
                                destination = "",
                                date = date
                            )
                            if (response.isSuccessful) {
                                response.body()
                                    ?.flatMap { it.flights }
                                    ?.filter { it.origin == "OTP" }
                                    ?.map { it.destination }
                                    ?: emptyList()
                            } else emptyList()
                        } catch (_: Exception) { emptyList() }
                    }
                }
                val availableCodes = flightJobs.awaitAll().flatten().toSet()

                // Verifica daca job-ul a fost anulat intre timp
                if (!isActive) return@launch

                val toCheck = if (availableCodes.isNotEmpty())
                    destinations.filter { it.code in availableCodes }
                else
                    destinations

                // 2. Vreme — toate destinatiile în paralel
                val weatherJobs = toCheck.map { dest ->
                    async(Dispatchers.IO) {
                        try {
                            val response = WeatherRetrofit.api.getWeather(dest.weatherCity, apiKey)
                            if (response.isSuccessful) {
                                val weather = response.body() ?: return@async null
                                if (matchesMood(weather, mood)) Pair(dest, weather) else null
                            } else null
                        } catch (_: Exception) { null }
                    }
                }
                val matched = weatherJobs.awaitAll().filterNotNull()

                // Verifica din nou daca nu a fost anulat
                if (!isActive) return@launch

                pbLoading.visibility = View.GONE

                if (matched.isEmpty()) {
                    tvStatus.text = "Nu am gasit destinatii cu ${mood.label} acum. Incearca alta vreme!"
                    tvStatus.visibility = View.VISIBLE
                } else {
                    rvDestinations.adapter = WeekendDestinationAdapter(matched) { dest ->
                        openFlightSearch(dest)
                    }
                    rvDestinations.visibility = View.VISIBLE
                }

            } catch (_: CancellationException) {
                // Job anulat intentionat — nu afisa eroare
                pbLoading.visibility = View.GONE
            } catch (_: Exception) {
                pbLoading.visibility = View.GONE
                tvStatus.text = "Eroare la incarcarea destinatiilor."
                tvStatus.visibility = View.VISIBLE
            }
        }
    }

    private fun matchesMood(weather: WeatherResponse, mood: WeatherMood): Boolean {
        val main = weather.weather.firstOrNull()?.main?.lowercase() ?: ""
        val desc = weather.weather.firstOrNull()?.description?.lowercase() ?: ""
        val temp = weather.main.temp

        return when (mood) {
            WeatherMood.SUNNY -> main == "clear" && temp > 15
            WeatherMood.BEACH -> temp > 25 && (main == "clear" || (main == "clouds" && !desc.contains("overcast")))
            WeatherMood.MILD  -> temp in 12f..22f && main != "rain" && main != "snow"
            WeatherMood.RAINY -> main == "rain" || main == "drizzle" || desc.contains("rain")
            WeatherMood.COLD  -> temp < 5
            WeatherMood.SNOW  -> main == "snow" || desc.contains("snow")
        }
    }

    private fun openFlightSearch(dest: WeekendDestination) {
        val today = LocalDate.now()
        val daysUntilSaturday = ((6 - today.dayOfWeek.value) + 7) % 7
        val nextSaturday = today.plusDays(daysUntilSaturday.toLong().coerceAtLeast(1))
        val departureDate = nextSaturday.format(formatter)

        startActivity(Intent(this, GlobeActivity::class.java).apply {
            putExtra("FROM_CITY", "OTP")
            putExtra("TO_CITY", dest.code)
            putExtra("SELECTED_DATE", departureDate)
            putExtra("IS_ROUND_TRIP", false)
        })
    }
}