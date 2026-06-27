package com.example.airlinereservationsapp

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SendModificationRequestActivity : AppCompatActivity() {

    private var bookingId: Int = -1
    private var userId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_send_request)

        bookingId = intent.getIntExtra("BOOKING_ID", -1)
        userId    = getSharedPreferences("prefs", MODE_PRIVATE).getInt("user_id", -1)

        val tvBookingInfo  = findViewById<TextView>(R.id.tvRequestBookingInfo)
        val spinnerType    = findViewById<Spinner>(R.id.spinnerRequestType)
        val etDescription  = findViewById<EditText>(R.id.etRequestDescription)
        val btnSend        = findViewById<Button>(R.id.btnSendRequest)
        val pbSending      = findViewById<ProgressBar>(R.id.pbSendingRequest)

        tvBookingInfo.text = "Rezervare #$bookingId"

        val types = listOf(
            "Schimbare destinatie",
            "Schimbare ora zbor",
            "Anulare si Refund",
            "Altele"
        )
        val typeCodes = listOf("DESTINATION", "TIME", "CANCEL", "OTHER")

        spinnerType.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item, types)

        findViewById<ImageButton>(R.id.btnBackRequest).setOnClickListener { finish() }

        btnSend.setOnClickListener {
            val description = etDescription.text.toString().trim()
            if (description.isEmpty()) {
                etDescription.error = "Descrie requestul tau"
                return@setOnClickListener
            }

            val selectedType = typeCodes[spinnerType.selectedItemPosition]

            pbSending.visibility = View.VISIBLE
            btnSend.isEnabled = false

            lifecycleScope.launch {
                try {
                    val response = RetrofitInstance.api.createModificationRequest(
                        ModificationRequestBody(
                            userId = userId,
                            bookingId = bookingId,
                            requestType = selectedType,
                            description = description
                        )
                    )
                    pbSending.visibility = View.GONE
                    btnSend.isEnabled = true

                    if (response.isSuccessful) {
                        Toast.makeText(this@SendModificationRequestActivity,
                            "Request trimis! Adminul va reveni cu un raspuns.",
                            Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        android.util.Log.e("DEBUG_REQUEST", "code=${response.code()} error=${response.errorBody()?.string()}")
                        Toast.makeText(this@SendModificationRequestActivity,
                            "Eroare la trimitere", Toast.LENGTH_SHORT).show()
                    }
                } catch (_: Exception) {
                    pbSending.visibility = View.GONE
                    btnSend.isEnabled = true
                    Toast.makeText(this@SendModificationRequestActivity,
                        "Eroare server", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}