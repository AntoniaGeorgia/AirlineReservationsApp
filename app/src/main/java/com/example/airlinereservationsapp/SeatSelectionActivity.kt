package com.example.airlinereservationsapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch

class SeatSelectionActivity : AppCompatActivity() {

    private lateinit var adapter: SeatAdapter
    private var lastSelectedSeat: Seat? = null
    private var allSeats: List<SeatResponse> = emptyList()

    private var currentFlightId: Int = -1
    private var secondFlightId: Int = -1
    private var totalPrice: Double = 0.0
    private var loggedInUserId: Int = -1
    private var isSecondFlight = false
    private var firstSeatId: Int = -1
    private var firstSeatLabel: String? = null
    private var selectedClass: String = "ECONOMY"
    private var baggageOption: String = "HAND_ONLY"
    private var isCheckIn: Boolean = false
    private var extraFee: Double = 0.0
    private var standaloneMode = false

    private val COLS = listOf("A", "B", "C", "D", "E", "F")
    private val AISLE_AFTER = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_seat_selection)

        currentFlightId = intent.getIntExtra("FLIGHT_ID", -1)
        secondFlightId  = intent.getIntExtra("SECOND_FLIGHT_ID", -1)
        totalPrice      = intent.getDoubleExtra("TOTAL_PRICE", 0.0)
        isSecondFlight  = intent.getBooleanExtra("IS_SECOND_FLIGHT", false)
        firstSeatId     = intent.getIntExtra("FIRST_SEAT_ID", -1)
        firstSeatLabel  = intent.getStringExtra("FIRST_SEAT_LABEL")
        selectedClass   = intent.getStringExtra("SEAT_CLASS") ?: "ECONOMY"
        baggageOption   = intent.getStringExtra("BAGGAGE_OPTION") ?: "HAND_ONLY"
        isCheckIn       = intent.getBooleanExtra("IS_CHECKIN", false)
        extraFee        = intent.getDoubleExtra("EXTRA_FEE", 0.0)
        standaloneMode = intent.getBooleanExtra("STANDALONE_MODE", false)

        loggedInUserId = getSharedPreferences("prefs", MODE_PRIVATE).getInt("user_id", -1)
        if (loggedInUserId == -1) {
            Toast.makeText(this, "Utilizator neautentificat!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val recyclerView = findViewById<RecyclerView>(R.id.rvSeats)
        val btnConfirm   = findViewById<Button>(R.id.btnConfirmReservation)
        val tvPrice      = findViewById<TextView>(R.id.tvPriceDisplay)
        val tvTitle      = findViewById<TextView>(R.id.tvSeatTitle)
        val chipGroup    = findViewById<ChipGroup>(R.id.chipGroupClass)
        val tvClassInfo  = findViewById<TextView>(R.id.tvClassInfo)
        val btnSkipSeat  = findViewById<Button>(R.id.btnSkipSeat)

        tvTitle.text = if (isCheckIn) "Alege locul — Check-in"
        else if (isSecondFlight) "Alege locul — Zbor Întoarcere"
        else "Alege locul — Zbor Dus"

        if (isCheckIn) {
            chipGroup.visibility = View.GONE
            tvClassInfo.visibility = View.GONE
            btnSkipSeat.visibility = View.GONE
            btnConfirm.text = "Confirmă loc"
        }

        val GRID_COLS = 8
        val layoutManager = GridLayoutManager(this, GRID_COLS)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (adapter.getItemViewType(position)) {
                    SeatAdapter.TYPE_ZONE   -> GRID_COLS
                    SeatAdapter.TYPE_HEADER -> 1
                    else -> 1
                }
            }
        }
        recyclerView.layoutManager = layoutManager

        adapter = SeatAdapter(emptyList()) { seat ->
            lastSelectedSeat = seat
            if (isCheckIn) {
                val checkInPrice = calculateCheckInSeatPrice(seat, allSeats)
                tvPrice.text = "Cost schimbare loc: %.2f €".format(checkInPrice)
            } else {
                val price = allSeats.find { it.id == seat.id }?.currentPrice ?: totalPrice
                tvPrice.text = when (selectedClass) {
                    "ECONOMY" -> "Preț bilet pentru locul ${seat.seatNumber}: %.2f €".format(price)
                    else -> "Loc ales: ${seat.seatNumber} — inclus în ${if (selectedClass == "BUSINESS") "Business" else "Premium Economy"} (%.2f €)".format(price)
                }
            }
        }
        recyclerView.adapter = adapter

        if (!isCheckIn) {
            tvClassInfo.text = getClassBenefits(selectedClass)
            chipGroup.isEnabled = false
            for (i in 0 until chipGroup.childCount) {
                (chipGroup.getChildAt(i) as? Chip)?.isEnabled = false
            }
            for (i in 0 until chipGroup.childCount) {
                val chip = chipGroup.getChildAt(i) as? Chip ?: continue
                val chipClass = when (chip.text.toString()) {
                    "Business"        -> "BUSINESS"
                    "Premium Economy" -> "PREMIUM_ECONOMY"
                    else              -> "ECONOMY"
                }
                if (chipClass == selectedClass) {
                    chipGroup.check(chip.id)
                    break
                }
            }
        }

        btnConfirm.setOnClickListener {
            val seat = lastSelectedSeat ?: run {
                Toast.makeText(this, "Selectează un loc!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isCheckIn) {
                val seatFee = calculateCheckInSeatPrice(seat, allSeats)
                val resultIntent = Intent().apply {
                    putExtra("SELECTED_SEAT_ID", seat.id)
                    putExtra("SEAT_FEE", seatFee)
                    putExtra("EXTRA_FEE", extraFee)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
                return@setOnClickListener
            }
            if (standaloneMode) {
                btnSkipSeat.setOnClickListener {
                    setResult(RESULT_CANCELED)
                    finish()
                }
            }


            if (standaloneMode) {
                val seatPrice = allSeats.find { it.id == seat.id }?.currentPrice ?: 0.0
                val resultIntent = Intent().apply {
                    putExtra("SELECTED_SEAT_ID", seat.id)
                    putExtra("SELECTED_SEAT_LABEL", seat.seatNumber)
                    putExtra("SELECTED_SEAT_PRICE", seatPrice)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
                return@setOnClickListener
            }

            if (!isSecondFlight && secondFlightId != -1) {
                startActivity(Intent(this, SeatSelectionActivity::class.java).apply {
                    putExtra("FLIGHT_ID", secondFlightId)
                    putExtra("ORIGINAL_FLIGHT_ID", currentFlightId)
                    putExtra("SECOND_FLIGHT_ID", -1)
                    putExtra("TOTAL_PRICE", totalPrice)
                    putExtra("IS_SECOND_FLIGHT", true)
                    putExtra("FIRST_SEAT_ID", seat.id)
                    putExtra("FIRST_SEAT_LABEL", seat.seatNumber)
                    putExtra("SEAT_CLASS", selectedClass)
                    putExtra("BAGGAGE_OPTION", baggageOption)
                })
                finish()
            } else {
                confirmReservation(seat)
            }
        }

        btnSkipSeat.setOnClickListener {
            if (standaloneMode) {
                setResult(RESULT_CANCELED)
                finish()
            } else if (!isSecondFlight && secondFlightId != -1) {
                startActivity(Intent(this, SeatSelectionActivity::class.java).apply {
                    putExtra("FLIGHT_ID", secondFlightId)
                    putExtra("ORIGINAL_FLIGHT_ID", currentFlightId)
                    putExtra("SECOND_FLIGHT_ID", -1)
                    putExtra("TOTAL_PRICE", totalPrice)
                    putExtra("IS_SECOND_FLIGHT", true)
                    putExtra("FIRST_SEAT_ID", -1)
                    putExtra("FIRST_SEAT_LABEL", "")
                    putExtra("SEAT_CLASS", selectedClass)
                    putExtra("BAGGAGE_OPTION", baggageOption)
                })
                finish()
            } else {
                confirmReservationWithoutSeat()
            }
        }

        loadSeats()
    }

    private fun calculateCheckInSeatPrice(seat: Seat, allSeats: List<SeatResponse>): Double {
        val basePrice = 15.0
        val col = seat.seatNumber.last().uppercaseChar()
        val typeMultiplier = when (col) {
            'C', 'D' -> 1.5
            'A', 'F' -> 1.3
            else     -> 1.0
        }
        val totalSeats = allSeats.filter { it.seatClass == selectedClass }.size
        val occupiedSeats = allSeats.filter { it.seatClass == selectedClass && it.isOccupied }.size
        val occupancyRate = if (totalSeats > 0) occupiedSeats.toDouble() / totalSeats else 0.0
        val occupancyMultiplier = when {
            occupancyRate >= 0.8 -> 1.5
            occupancyRate >= 0.5 -> 1.2
            else -> 1.0
        }
        return basePrice * typeMultiplier * occupancyMultiplier
    }

    private fun confirmReservationWithoutSeat() {
        lifecycleScope.launch {
            try {
                val request = BookingRequest(
                    userId         = loggedInUserId,
                    flightId       = intent.getIntExtra("ORIGINAL_FLIGHT_ID", currentFlightId),
                    secondFlightId = if (isSecondFlight) currentFlightId else null,
                    seatId         = -1,
                    seatLabel      = null,
                    firstSeatId    = if (isSecondFlight) firstSeatId else null,
                    firstSeatLabel = if (isSecondFlight) firstSeatLabel else null,
                    finalPrice     = totalPrice
                )
                val response = RetrofitInstance.api.createBooking(request)
                if (response.isSuccessful) {
                    val bookingResponse = response.body()!!
                    startActivity(Intent(this@SeatSelectionActivity, PaymentActivity::class.java).apply {
                        putExtra("BOOKING_ID", bookingResponse.bookingId)
                        putExtra("FINAL_PRICE", totalPrice)
                        putExtra("BAGGAGE_OPTION", baggageOption)
                        putExtra("SEAT_CLASS", selectedClass)
                    })
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SeatSelectionActivity, "Eroare: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun buildAircraftMap() {
        val filteredSeats = allSeats.filter { it.seatClass.orEmpty() == selectedClass }
        val cells = mutableListOf<SeatCell>()
        val (zoneText, zoneColor) = when (selectedClass) {
            "BUSINESS"        -> "✈ BUSINESS CLASS"  to ContextCompat.getColor(this, R.color.airline_gold)
            "PREMIUM_ECONOMY" -> "✈ PREMIUM ECONOMY" to ContextCompat.getColor(this, R.color.blue_light)
            else              -> "✈ ECONOMY CLASS"    to ContextCompat.getColor(this, R.color.blue_pale)
        }
        cells.add(SeatCell.ZoneLabel(zoneText, zoneColor))
        cells.add(SeatCell.Header(""))
        for (i in COLS.indices) {
            cells.add(SeatCell.Header(COLS[i]))
            if (i == AISLE_AFTER) cells.add(SeatCell.Aisle)
        }
        val rowNumbers = filteredSeats
            .map { it.seatNumber.dropLast(1).toIntOrNull() ?: 0 }
            .distinct().sorted()
        for (row in rowNumbers) {
            cells.add(SeatCell.Header(row.toString()))
            for (i in COLS.indices) {
                val seatResp = filteredSeats.find { it.seatNumber == "$row${COLS[i]}" }
                if (seatResp != null) {
                    cells.add(SeatCell.SeatItem(Seat(
                        id = seatResp.id,
                        seatNumber = seatResp.seatNumber,
                        isOccupied = seatResp.isOccupied,
                        seatClass  = seatResp.seatClass ?: "ECONOMY"
                    )))
                } else {
                    cells.add(SeatCell.Aisle)
                }
                if (i == AISLE_AFTER) cells.add(SeatCell.Aisle)
            }
        }
        adapter.updateCells(cells)
    }

    private fun loadSeats() {
        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.getSeats(currentFlightId)
                if (response.isSuccessful) {
                    allSeats = response.body() ?: emptyList()
                    buildAircraftMap()
                } else {
                    Toast.makeText(this@SeatSelectionActivity, "Eroare încărcare locuri", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SeatSelectionActivity, "Eroare conexiune", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getClassBenefits(seatClass: String) = when (seatClass) {
        "BUSINESS"        -> "✓ Loc extra-larg  ✓ Masă inclusă  ✓ Lounge acces  ✓ Prioritate îmbarcare"
        "PREMIUM_ECONOMY" -> "✓ Loc mai spațios  ✓ Bagaj inclus  ✓ Masă simplă"
        else              -> "✓ Loc standard  ✓ Bagaj de mână inclus"
    }

    private fun confirmReservation(secondSeat: Seat) {
        val seatPrice = allSeats.find { it.id == secondSeat.id }?.currentPrice ?: totalPrice
        lifecycleScope.launch {
            try {
                val request = BookingRequest(
                    userId         = loggedInUserId,
                    flightId       = intent.getIntExtra("ORIGINAL_FLIGHT_ID", currentFlightId),
                    secondFlightId = if (isSecondFlight) currentFlightId else null,
                    seatId         = secondSeat.id,
                    seatLabel      = secondSeat.seatNumber,
                    firstSeatId    = if (isSecondFlight) firstSeatId else null,
                    firstSeatLabel = if (isSecondFlight) firstSeatLabel else null,
                    finalPrice     = seatPrice
                )
                val response = RetrofitInstance.api.createBooking(request)
                if (response.isSuccessful) {
                    val bookingResponse = response.body()!!
                    Toast.makeText(this@SeatSelectionActivity, "Loc rezervat!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@SeatSelectionActivity, PassengerDetailsActivity::class.java).apply {
                        putExtra("BOOKING_ID", bookingResponse.bookingId)
                        putExtra("FINAL_PRICE", seatPrice)
                        putExtra("BAGGAGE_OPTION", baggageOption)
                        putExtra("SEAT_CLASS", selectedClass)
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    })
                    finish()
                } else if (response.code() == 409) {
                    Toast.makeText(this@SeatSelectionActivity, "Loc ocupat!", Toast.LENGTH_LONG).show()
                    loadSeats()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SeatSelectionActivity, "Eroare: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}