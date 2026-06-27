package com.example.airlinereservationsapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class PaymentActivity : AppCompatActivity() {

    private lateinit var etCardNumber: EditText
    private lateinit var etExpiry: EditText
    private lateinit var etCVV: EditText
    private lateinit var etCardHolder: EditText
    private lateinit var btnPayNow: Button
    private lateinit var tvFinalPrice: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment)

        etCardNumber = findViewById(R.id.etCardNumber)
        etExpiry     = findViewById(R.id.etExpiry)
        etCVV        = findViewById(R.id.etCVV)
        etCardHolder = findViewById(R.id.etCardHolder)
        btnPayNow    = findViewById(R.id.btnPayNow)
        tvFinalPrice = findViewById(R.id.tvFinalPrice)

        val bookingId     = intent.getIntExtra("BOOKING_ID", -1)
        val finalPrice    = intent.getDoubleExtra("FINAL_PRICE", 0.0)
        val baggageOption = intent.getStringExtra("BAGGAGE_OPTION") ?: "HAND_ONLY"
        val seatClass     = intent.getStringExtra("SEAT_CLASS") ?: "ECONOMY"

        tvFinalPrice.text = "Total de plată: %.2f €".format(finalPrice)

        if (bookingId == -1) {
            Toast.makeText(this, "Eroare: Rezervare invalidă", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        btnPayNow.setOnClickListener {
            if (validateCardInputs()) {
                executePayment(bookingId, baggageOption, seatClass)
            }
        }
    }

    private fun validateCardInputs(): Boolean {
        val cardNumber = etCardNumber.text.toString().trim()
        val expiry     = etExpiry.text.toString().trim()
        val cvv        = etCVV.text.toString().trim()
        val holder     = etCardHolder.text.toString().trim()

        if (cardNumber.length < 16) {
            etCardNumber.error = "Număr de card incomplet (16 cifre)"
            return false
        }
        if (!expiry.contains("/") || expiry.length < 5) {
            etExpiry.error = "Format invalid (MM/YY)"
            return false
        }
        if (cvv.length < 3) {
            etCVV.error = "CVV invalid"
            return false
        }
        if (holder.isEmpty()) {
            etCardHolder.error = "Introduceți numele de pe card"
            return false
        }
        return true
    }

    private fun executePayment(bookingId: Int, baggageOption: String, seatClass: String) {
        btnPayNow.isEnabled = false
        btnPayNow.text = "Se procesează..."

        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.payBooking(bookingId)
                Log.d("PAY", "pay pentru bookingId=$bookingId")

                if (response.isSuccessful) {
                    Toast.makeText(this@PaymentActivity, "Plată Reușită!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@PaymentActivity, MyBookingsActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    })
                    finish()
                } else {
                    btnPayNow.isEnabled = true
                    btnPayNow.text = "Plătește Acum"
                    Toast.makeText(this@PaymentActivity,
                        "Eroare server: ${response.code()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                btnPayNow.isEnabled = true
                btnPayNow.text = "Plătește Acum"
                Toast.makeText(this@PaymentActivity,
                    "Eroare rețea: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}