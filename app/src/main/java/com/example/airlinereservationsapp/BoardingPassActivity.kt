package com.example.airlinereservationsapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class BoardingPassActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_boarding_pass)

        val passengerName = intent.getStringExtra("PASSENGER_NAME") ?: "PASAGER"
        val seatLabel     = intent.getStringExtra("SEAT_LABEL") ?: "N/A"
        val bookingId     = intent.getIntExtra("BOOKING_ID", 0)
        val seatClass     = intent.getStringExtra("SEAT_CLASS") ?: "ECONOMY"
        val baggageOption = intent.getStringExtra("BAGGAGE_OPTION") ?: "HAND_ONLY"
        val origin        = intent.getStringExtra("ORIGIN") ?: "OTP"
        val destination   = intent.getStringExtra("DESTINATION") ?: "?"

        val classDisplay = when (seatClass) {
            "BUSINESS"        -> "Business Class"
            "PREMIUM_ECONOMY" -> "Premium Economy"
            else              -> "Economy Class"
        }

        val baggageDisplay = when (baggageOption) {
            "CABIN_20KG"  -> "Bagaj cabina 20kg"
            "HOLD_23KG"   -> "Bagaj cala 23kg"
            "HOLD_2X23KG" -> "2x Bagaj cala 23kg"
            else          -> "Bagaj de mana"
        }

        findViewById<TextView>(R.id.tvPassengerNameBP).text = passengerName.uppercase()
        findViewById<TextView>(R.id.tvRouteBP).text = "$origin -> $destination"
        findViewById<TextView>(R.id.tvSeatBP).text = "$seatLabel  |  $classDisplay"
        findViewById<TextView>(R.id.tvBookingRefBP).text = "Booking REF: #$bookingId  |  $baggageDisplay"

        findViewById<Button>(R.id.btnBackToHome).setOnClickListener {
            startActivity(Intent(this, FlightSearchActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }

        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.generateBoardingPass(bookingId)
                if (response.isSuccessful) {
                    val token = response.body()?.get("token") ?: return@launch
                    val qrBitmap = generateQRCode(token)
                    qrBitmap?.let { findViewById<ImageView>(R.id.ivQrCode).setImageBitmap(it) }
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(this@BoardingPassActivity, "Eroare generare QR: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun generateQRCode(text: String): Bitmap? {
        return try {
            val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 512, 512)
            val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565)
            for (x in 0 until 512) {
                for (y in 0 until 512) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }
}