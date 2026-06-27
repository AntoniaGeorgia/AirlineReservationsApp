package com.example.airlinereservationsapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.launch

class GlobeActivity : AppCompatActivity() {

    private lateinit var globeView: GlobeView
    private lateinit var bottomSheet: FrameLayout
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>
    private lateinit var rvFlights: RecyclerView
    private lateinit var pbLoading: ProgressBar
    private lateinit var tvHint: TextView
    private var flightsLoaded = false

    private var fromCity = ""
    private var toCity = ""
    private var selectedDate = ""
    private var returnDate: String? = null
    private var isRoundTrip = false

    private val cityCoords = mapOf(
        "OTP" to Pair(44.5f, 26.1f),
        "LHR" to Pair(51.5f, -0.5f),
        "CDG" to Pair(49.0f, 2.5f),
        "VIE" to Pair(48.1f, 16.6f),
        "FRA" to Pair(50.0f, 8.6f),
        "BCN" to Pair(41.3f, 2.1f),
        "FCO" to Pair(41.8f, 12.5f),
        "AMS" to Pair(52.3f, 4.9f),
        "ATH" to Pair(37.9f, 23.7f),
        "DXB" to Pair(25.2f, 55.4f),
        "LIS" to Pair(38.8f, -9.1f),
        "MAD" to Pair(40.5f, -3.6f),
        "PRG" to Pair(50.1f, 14.3f),
        "BUD" to Pair(47.4f, 19.3f),
        "CPH" to Pair(55.6f, 12.7f),
        "ARN" to Pair(59.7f, 17.9f),
        "ZRH" to Pair(47.5f, 8.5f),
        "BRU" to Pair(50.9f, 4.5f),
        "TFS" to Pair(28.0f, -16.6f),
        "PMI" to Pair(39.6f, 2.7f),
        "KEF" to Pair(63.9f, -22.6f),
        "OSL" to Pair(60.2f, 11.1f),
        "HEL" to Pair(60.3f, 25.0f),
        "RAK" to Pair(31.6f, -8.0f),
        "JMK" to Pair(37.4f, 25.3f),
        "CAI" to Pair(30.1f, 31.4f)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fromCity    = intent.getStringExtra("FROM_CITY") ?: "OTP"
        toCity      = intent.getStringExtra("TO_CITY") ?: "LHR"
        selectedDate = intent.getStringExtra("SELECTED_DATE") ?: ""
        returnDate  = intent.getStringExtra("RETURN_DATE")
        isRoundTrip = intent.getBooleanExtra("IS_ROUND_TRIP", false)

        val root = CoordinatorLayout(this)
        setContentView(root)

        val starBackground = View(this).apply {
            setBackgroundResource(R.drawable.bg_stars)
        }
        root.addView(starBackground, CoordinatorLayout.LayoutParams(
            CoordinatorLayout.LayoutParams.MATCH_PARENT,
            CoordinatorLayout.LayoutParams.MATCH_PARENT
        ))

        globeView = GlobeView(this)
        root.addView(globeView, CoordinatorLayout.LayoutParams(
            CoordinatorLayout.LayoutParams.MATCH_PARENT,
            CoordinatorLayout.LayoutParams.MATCH_PARENT
        ))

        bottomSheet = FrameLayout(this)
        val bsParams = CoordinatorLayout.LayoutParams(
            CoordinatorLayout.LayoutParams.MATCH_PARENT,
            CoordinatorLayout.LayoutParams.MATCH_PARENT
        ).apply { behavior = BottomSheetBehavior<FrameLayout>() }
        root.addView(bottomSheet, bsParams)

        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.peekHeight = 160
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheetBehavior.isHideable = false

        val sheetContent = LayoutInflater.from(this)
            .inflate(R.layout.layout_globe_bottom_sheet, bottomSheet, false)
        bottomSheet.addView(sheetContent)

        sheetContent.findViewById<TextView>(R.id.tvGlobeRoute).text =
            getString(R.string.route_format, fromCity, toCity)

        tvHint    = sheetContent.findViewById(R.id.tvTapHint)
        rvFlights = sheetContent.findViewById(R.id.rvSheetFlights)
        pbLoading = sheetContent.findViewById(R.id.pbSheetLoading)

        sheetContent.findViewById<View?>(R.id.btnGlobeSearch)?.visibility = View.GONE
        rvFlights.layoutManager = LinearLayoutManager(this)

        bottomSheet.setOnClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        bottomSheetBehavior.addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(view: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        if (!flightsLoaded) loadFlights()
                        tvHint.visibility = View.GONE
                    }
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        tvHint.visibility = View.VISIBLE
                    }
                    else -> {}
                }
            }

            override fun onSlide(view: View, slideOffset: Float) {
                val scale = 1f - slideOffset * 0.5f
                globeView.scaleX = scale
                globeView.scaleY = scale
                val screenHeight = root.height.toFloat()
                globeView.translationY = -slideOffset * (screenHeight * 0.30f)
            }
        })

        val fromCoords = cityCoords[fromCity]
        val toCoords   = cityCoords[toCity]
        if (fromCoords != null && toCoords != null) {
            globeView.focusOnRoute(
                fromCoords.first, fromCoords.second,
                toCoords.first,   toCoords.second
            )
        } else {
            globeView.startAutoRotate()
        }
    }

    private fun loadFlights() {
        flightsLoaded = true
        pbLoading.visibility = View.VISIBLE
        rvFlights.visibility = View.GONE
        tvHint.visibility = View.GONE


        lifecycleScope.launch {
            try {
                val directResponse = RetrofitInstance.api.searchFlights(fromCity, toCity, selectedDate)
                val connectingResponse = RetrofitInstance.api.searchConnectingFlights(fromCity, toCity, selectedDate)

                if (isFinishing || isDestroyed) return@launch
                pbLoading.visibility = View.GONE

                val directFlights = if (directResponse.isSuccessful) directResponse.body() ?: emptyList() else emptyList()
                val connectingFlights = if (connectingResponse.isSuccessful) connectingResponse.body() ?: emptyList() else emptyList()
                Log.d("FLIGHTS", "Direct: ${directFlights.size}, Connecting: ${connectingFlights.size}")
                Log.d("FLIGHTS", "Connecting response code: ${connectingResponse.code()}")
                val allFlights = (directFlights + connectingFlights).sortedBy { it.totalPrice }

                if (allFlights.isEmpty()) {
                    tvHint.text = getString(R.string.no_flights_available)
                    tvHint.visibility = View.VISIBLE
                } else {
                    rvFlights.adapter = FlightAdapter(allFlights) { itinerary ->
                        if (isRoundTrip) {
                            showReturnFlights(itinerary)
                        } else {
                            goToClassUpgrade(itinerary)
                        }
                    }
                    rvFlights.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                if (isFinishing || isDestroyed) return@launch
                pbLoading.visibility = View.GONE
                Toast.makeText(this@GlobeActivity, "Verifică conexiunea!", Toast.LENGTH_SHORT).show()
            }

        }
    }
    private fun showReturnFlights(outboundItinerary: FlightItinerary) {
        pbLoading.visibility = View.VISIBLE
        rvFlights.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val returnDirectResponse = RetrofitInstance.api.searchFlights(toCity, fromCity, returnDate ?: "")
                val returnConnectingResponse = RetrofitInstance.api.searchConnectingFlights(toCity, fromCity, returnDate ?: "")

                pbLoading.visibility = View.GONE

                val directReturn = if (returnDirectResponse.isSuccessful) returnDirectResponse.body() ?: emptyList() else emptyList()
                val connectingReturn = if (returnConnectingResponse.isSuccessful) returnConnectingResponse.body() ?: emptyList() else emptyList()

                val allReturn = (directReturn + connectingReturn).sortedBy { it.totalPrice }

                if (allReturn.isEmpty()) {
                    Toast.makeText(this@GlobeActivity, "Nu există zboruri de întoarcere!", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                tvHint.text = "Selectează zborul de întoarcere ($toCity → $fromCity)"
                tvHint.visibility = View.VISIBLE

                rvFlights.adapter = FlightAdapter(allReturn) { returnItinerary ->
                    goToClassUpgradeRoundTrip(outboundItinerary, returnItinerary)
                }
                rvFlights.visibility = View.VISIBLE
            } catch (e: Exception) {
                pbLoading.visibility = View.GONE
                Toast.makeText(this@GlobeActivity, "Eroare la încărcare!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun goToClassUpgradeRoundTrip(outbound: FlightItinerary, returnFlight: FlightItinerary) {
        val userId = getSharedPreferences("prefs", MODE_PRIVATE).getInt("user_id", -1)
        if (userId == -1) {
            startActivity(Intent(this, LoginActivity::class.java))
            return
        }
        startActivity(Intent(this, ClassUpgradeActivity::class.java).apply {
            putExtra("FLIGHT_ID", outbound.flights[0].id)
            putExtra("SECOND_FLIGHT_ID", returnFlight.flights[0].id)
            putExtra("BASE_PRICE", outbound.totalPrice + returnFlight.totalPrice)
            putExtra("OUTBOUND_BASE_PRICE", outbound.totalPrice)
            putExtra("RETURN_BASE_PRICE", returnFlight.totalPrice)
            putExtra("IS_ROUND_TRIP", true)
            putExtra("IS_CONNECTING", false)
            putExtra("LAYOVER_AIRPORT", "")
        })
    }

    private fun goToClassUpgrade(itinerary: FlightItinerary) {
        val userId = getSharedPreferences("prefs", MODE_PRIVATE).getInt("user_id", -1)
        if (userId == -1) {
            Toast.makeText(this, "Trebuie să te conectezi pentru a rezerva!", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            return
        }

        val flightId = itinerary.flights.getOrNull(0)?.id ?: -1
        val isConnecting = !itinerary.isDirect
        val secondFlightId = if (isConnecting) itinerary.flights.getOrNull(1)?.id ?: -1 else -1

        val firstLegPrice  = itinerary.flights.getOrNull(0)?.currentPrice ?: itinerary.totalPrice
        val secondLegPrice = if (isConnecting) itinerary.flights.getOrNull(1)?.currentPrice ?: 0.0 else 0.0

        startActivity(Intent(this, ClassUpgradeActivity::class.java).apply {
            putExtra("FLIGHT_ID", flightId)
            putExtra("SECOND_FLIGHT_ID", secondFlightId)
            putExtra("BASE_PRICE", itinerary.totalPrice)
            putExtra("OUTBOUND_BASE_PRICE", firstLegPrice)
            putExtra("RETURN_BASE_PRICE", secondLegPrice)
            putExtra("IS_ROUND_TRIP", false)
            putExtra("IS_CONNECTING", isConnecting)
            putExtra("LAYOVER_AIRPORT", itinerary.layoverAirport ?: "")
        })
    }

    override fun onResume() {
        super.onResume()
        globeView.onResume()
        globeView.resumeRotation()
    }

    override fun onPause() {
        super.onPause()
        globeView.pauseRotation()
        globeView.onPause()
    }
}