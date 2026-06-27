package com.example.airlinereservationsapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.airlinereservationsapp.databinding.ActivityPassengerDetailsBinding
import kotlinx.coroutines.launch
import java.util.*

class PassengerDetailsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPassengerDetailsBinding
    private var bookingId: Int = -1
    private var seatClass: String = "ECONOMY"
    private var baggageOption: String = "HAND_ONLY"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPassengerDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bookingId     = intent.getIntExtra("BOOKING_ID", -1)
        seatClass     = intent.getStringExtra("SEAT_CLASS") ?: "ECONOMY"
        baggageOption = intent.getStringExtra("BAGGAGE_OPTION") ?: "HAND_ONLY"

        binding.tvMealInfo.text = "Meniu inclus: ${getMealDescription()}"

        binding.btnFinalizeBooking.setOnClickListener {
            val docNumber        = binding.etDocNumber.text.toString().trim()
            val firstName        = binding.etFirstName.text.toString().trim()
            val lastName         = binding.etLastName.text.toString().trim()
            val selectedGenderId = binding.rgGender.checkedRadioButtonId

            if (docNumber.isEmpty() || firstName.isEmpty() || lastName.isEmpty() || selectedGenderId == -1) {
                Toast.makeText(this, "Completați toate datele!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val genderText = findViewById<RadioButton>(selectedGenderId).text.toString()
            finalizeBooking(firstName, lastName, docNumber, genderText)
        }
    }

    private fun getMealDescription(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..10  -> "Mic Dejun (Omletă și Cafea)"
            in 11..18 -> "Prânz (Pui cu legume)"
            else      -> "Cină (Paste și Vin)"
        }
    }

    private fun finalizeBooking(firstName: String, lastName: String, doc: String, gender: String) {
        lifecycleScope.launch {
            try {
                val request = PassengerDetailsRequest(
                    bookingId      = bookingId,
                    firstName      = firstName,
                    lastName       = lastName,
                    documentType   = "PASSPORT",
                    documentNumber = doc,
                    gender         = gender,
                    nationality    = "RO"
                )

                val response = RetrofitInstance.api.updatePassengerDetails(request)

                if (response.isSuccessful) {
                    Toast.makeText(this@PassengerDetailsActivity, "Date salvate!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@PassengerDetailsActivity, PaymentActivity::class.java).apply {
                        putExtra("BOOKING_ID", bookingId)
                        putExtra("FINAL_PRICE", intent.getDoubleExtra("FINAL_PRICE", 0.0))
                        putExtra("BAGGAGE_OPTION", baggageOption)
                        putExtra("SEAT_CLASS", seatClass)
                    })
                    finish()
                } else {
                    val errorMsg = response.errorBody()?.string()
                    Toast.makeText(this@PassengerDetailsActivity, "Eroare: $errorMsg", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("DEBUG_APP", "Eroare rețea: ${e.message}")
                Toast.makeText(this@PassengerDetailsActivity, "Eroare conexiune", Toast.LENGTH_SHORT).show()
            }
        }
    }
}