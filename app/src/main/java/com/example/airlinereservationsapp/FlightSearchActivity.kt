package com.example.airlinereservationsapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip
import android.os.Build
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
class FlightSearchActivity : AppCompatActivity() {

    private lateinit var globeBg: GlobeView
    private lateinit var fromSearch: AutoCompleteTextView
    private lateinit var toSearch: AutoCompleteTextView
    private lateinit var tvSelectedDates: TextView
    private lateinit var tvDepartureDate: TextView
    private lateinit var tvReturnDate: TextView
    private lateinit var layoutReturnDate: LinearLayout
    private lateinit var btnPickDates: Button
    private lateinit var btnSearch: Button
    private lateinit var btnLoginTop: Button
    private lateinit var btnAdminPanel: ImageButton
    private lateinit var chipOneWay: Chip
    private lateinit var chipRoundTrip: Chip
    private lateinit var rvExplore: androidx.recyclerview.widget.RecyclerView
    private var currentExploreOrigin = "OTP"

    private var departureDate: String? = null
    private var returnDate: String? = null
    private var isRoundTrip = false


    override fun onCreate(savedInstanceState: Bundle?) {
        RetrofitInstance.init(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 2001)
        }
        CheckInScheduler.schedule(this)
        val immediateCheck = OneTimeWorkRequestBuilder<CheckInWorker>().build()
        WorkManager.getInstance(this).enqueue(immediateCheck)
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flight_search)

        findViewById<View>(R.id.btnExploreDestinations).setOnClickListener {
            startActivity(Intent(this, ExploreActivity::class.java))
        }

        globeBg = findViewById(R.id.globeViewBg)
        fromSearch = findViewById(R.id.autoCompleteFrom)
        toSearch = findViewById(R.id.autoCompleteTo)
        tvSelectedDates = findViewById(R.id.tvSelectedDates)
        tvSelectedDates.text = "Nicio dată selectată"
        tvDepartureDate = findViewById(R.id.tvDepartureDate)
        tvReturnDate = findViewById(R.id.tvReturnDate)
        layoutReturnDate = findViewById(R.id.layoutReturnDate)
        btnPickDates = findViewById(R.id.btnPickDates)
        btnSearch = findViewById(R.id.btnSearchFlights)
        btnLoginTop = findViewById(R.id.btnLoginTop)
        btnAdminPanel = findViewById(R.id.btnAdminPanel)
        chipOneWay = findViewById(R.id.chipOneWay)
        chipRoundTrip = findViewById(R.id.chipRoundTrip)

        val cities = arrayOf(
            "București (OTP)", "Londra Heathrow (LHR)", "Paris (CDG)", "Viena (VIE)", 
            "Frankfurt (FRA)", "Madrid (MAD)", "Barcelona (BCN)", "Roma (FCO)",
            "Amsterdam (AMS)", "München (MUC)", "Dubai (DXB)", "New York JFK (JFK)"
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, cities)
        fromSearch.setAdapter(adapter)
        toSearch.setAdapter(adapter)

        btnLoginTop.setOnClickListener {
            val userId = getSharedPreferences("prefs", MODE_PRIVATE).getInt("user_id", -1)
            if (userId != -1) {
                startActivity(Intent(this, MyBookingsActivity::class.java))
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
            }
        }

        btnAdminPanel.setOnClickListener {
            startActivity(Intent(this, AdminActivity::class.java))
        }

        chipOneWay.setOnClickListener {
            isRoundTrip = false
            returnDate = null
            updateDatesDisplay()
        }

        chipRoundTrip.setOnClickListener {
            isRoundTrip = true
            updateDatesDisplay()
        }

        btnPickDates.setOnClickListener {
            val from = extractCode(fromSearch.text.toString())
            val to = extractCode(toSearch.text.toString())
            if (from.isEmpty() || to.isEmpty()) {
                Toast.makeText(this, "Alege mai întâi orașele!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val sheet = CalendarSheet.newInstance(from, to, isRoundTrip)
            sheet.onDatesSelected = { departure, returnD ->
                departureDate = departure
                returnDate = returnD
                updateDatesDisplay()
            }
            sheet.show(supportFragmentManager, "calendar")
        }

        btnSearch.setOnClickListener {
            val fromText = fromSearch.text.toString().trim()
            val toText = toSearch.text.toString().trim()
            if (fromText.isEmpty() || toText.isEmpty() || departureDate == null) {
                Toast.makeText(this, "Completează toate datele!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, GlobeActivity::class.java).apply {
                putExtra("FROM_CITY", extractCode(fromText))
                putExtra("TO_CITY", extractCode(toText))
                putExtra("SELECTED_DATE", departureDate)
                putExtra("RETURN_DATE", returnDate)
                putExtra("IS_ROUND_TRIP", isRoundTrip)
            })
        }

        findViewById<View>(R.id.cardWeekendTrip).setOnClickListener {
            startActivity(Intent(this, WeekendTripActivity::class.java))
        }
        findViewById<View>(R.id.btnInbox).setOnClickListener {
            startActivity(Intent(this, InboxActivity::class.java))
        }
        findViewById<View>(R.id.btnTravelerProfile).setOnClickListener {
            startActivity(Intent(this, TravelerProfileActivity::class.java))
        }
        rvExplore = findViewById(R.id.rvExplore)
        rvExplore.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
            this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false
        )
        loadExploreDestinations(currentExploreOrigin)

        fromSearch.setOnItemClickListener { _, _, _, _ ->
            val code = extractCode(fromSearch.text.toString())
            if (code.isNotEmpty() && code != currentExploreOrigin) {
                currentExploreOrigin = code
                loadExploreDestinations(code)
            }
        }

    }


    override fun onResume() {
        super.onResume()
        globeBg.onResume()
        globeBg.startAutoRotate()

        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val userId = prefs.getInt("user_id", -1)
        val role = prefs.getString("user_role", "PASSENGER")

        btnLoginTop.text = if (userId != -1) "Profilul meu" else "Conectează-te"

        btnAdminPanel.visibility = if (role == "ADMIN") View.VISIBLE else View.GONE
    }

    override fun onPause() {
        super.onPause()
        globeBg.onPause()
    }

    private fun updateDatesDisplay() {
        tvDepartureDate.text = departureDate ?: "Alege data"
        if (isRoundTrip) {
            layoutReturnDate.visibility = View.VISIBLE
            tvReturnDate.text = returnDate ?: "Alege data"
        } else {
            layoutReturnDate.visibility = View.GONE
        }
        tvSelectedDates.text = when {
            departureDate == null -> "Nicio dată selectată"
            isRoundTrip && returnDate != null -> "✈ $departureDate  ↩ $returnDate"
            else -> "✈ $departureDate"
        }
    }

    private fun loadExploreDestinations(origin: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.getCheapDestinations(origin)
                android.util.Log.d("DEBUG_EXPLORE", "origin=$origin code=${response.code()} body=${response.body()}")
                if (response.isSuccessful) {
                    val items = response.body() ?: emptyList()
                    rvExplore.adapter = ExploreDestinationAdapter(items) { dest ->
                        startActivity(Intent(this@FlightSearchActivity, GlobeActivity::class.java).apply {
                            putExtra("FROM_CITY", origin)
                            putExtra("TO_CITY", dest.destination)
                            putExtra("SELECTED_DATE", dest.departureDate)
                            putExtra("RETURN_DATE", null as String?)
                            putExtra("IS_ROUND_TRIP", false)
                        })
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DEBUG_EXPLORE", "Eroare la incarcare destinatii: ${e.message}", e)
            }
        }
    }
    private fun extractCode(city: String): String {
        return if (city.contains("(") && city.contains(")"))
            city.substringAfter("(").substringBefore(")")
        else city
    }
}