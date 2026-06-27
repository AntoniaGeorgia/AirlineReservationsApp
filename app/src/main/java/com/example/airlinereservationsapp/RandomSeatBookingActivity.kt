package com.example.airlinereservationsapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class RandomSeatBookingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_random_seat)

        val flightId            = intent.getIntExtra("FLIGHT_ID", -1)
        val secondFlightId      = intent.getIntExtra("SECOND_FLIGHT_ID", -1)
        val totalPrice          = intent.getDoubleExtra("TOTAL_PRICE", 0.0)
        val seatClass           = intent.getStringExtra("SEAT_CLASS") ?: "ECONOMY"
        val baggageOption       = intent.getStringExtra("BAGGAGE_OPTION") ?: "HAND_ONLY"
        val secondBaggageOption = intent.getStringExtra("SECOND_BAGGAGE_OPTION")
        val outboundSeatId      = intent.getIntExtra("OUTBOUND_SEAT_ID", -1)
        val outboundSeatLabel   = intent.getStringExtra("OUTBOUND_SEAT_LABEL")
        val returnSeatId        = intent.getIntExtra("RETURN_SEAT_ID", -1)
        val userId              = getSharedPreferences("prefs", MODE_PRIVATE).getInt("user_id", -1)
        android.util.Log.d("DEBUG_RANDOM_BOOKING", "outboundSeatId=$outboundSeatId returnSeatId=$returnSeatId secondFlightId=$secondFlightId")

        val pbLoading = findViewById<ProgressBar>(R.id.pbRandomLoading)
        val tvInfo    = findViewById<TextView>(R.id.tvRandomInfo)

        val isRoundTrip = secondFlightId != -1
        tvInfo.text = "Se creează rezervarea..."
        pbLoading.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val bookingRequest = BookingRequest(
                    userId              = userId,
                    flightId            = flightId,
                    secondFlightId      = if (isRoundTrip) secondFlightId else null,
                    seatId              = if (isRoundTrip) returnSeatId else outboundSeatId,
                    seatLabel           = null,
                    firstSeatId         = if (isRoundTrip && outboundSeatId != -1) outboundSeatId else null,
                    firstSeatLabel      = if (isRoundTrip && outboundSeatId != -1) outboundSeatLabel else null,
                    finalPrice          = totalPrice,
                    seatClass           = seatClass,
                    baggageOption       = baggageOption,
                    secondBaggageOption = secondBaggageOption
                )

                val bookingResponse = RetrofitInstance.api.createBooking(bookingRequest)
                if (!bookingResponse.isSuccessful) {
                    val err = bookingResponse.errorBody()?.string() ?: ""
                    if (err.contains("complet vândut")) {
                        showError("Zborul este complet vândut!")
                    } else {
                        showError("Eroare la rezervare: ${bookingResponse.code()}")
                    }
                    return@launch
                }

                val booking = bookingResponse.body()!!
                pbLoading.visibility = View.GONE

                startActivity(Intent(this@RandomSeatBookingActivity, PassengerDetailsActivity::class.java).apply {
                    putExtra("BOOKING_ID", booking.bookingId)
                    putExtra("FINAL_PRICE", totalPrice)
                    putExtra("BAGGAGE_OPTION", baggageOption)
                    putExtra("SEAT_CLASS", seatClass)
                })
                finish()

            } catch (e: Exception) {
                showError("Eroare: ${e.message}")
            }
        }
    }

    private fun showError(msg: String) {
        findViewById<ProgressBar>(R.id.pbRandomLoading).visibility = View.GONE
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        finish()
    }
}