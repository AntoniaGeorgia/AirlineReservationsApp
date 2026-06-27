package com.example.airlinereservationsapp

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat

class ClassUpgradeActivity : AppCompatActivity() {

    private var basePrice: Double = 0.0
    private var outboundBasePrice: Double = 0.0
    private var returnBasePrice: Double = 0.0
    private var selectedClass = "ECONOMY"
    private var flightId: Int = -1
    private var secondFlightId: Int = -1
    private var isRoundTrip: Boolean = false
    private var isConnecting: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_class_upgrade)

        flightId          = intent.getIntExtra("FLIGHT_ID", -1)
        secondFlightId    = intent.getIntExtra("SECOND_FLIGHT_ID", -1)
        basePrice         = intent.getDoubleExtra("BASE_PRICE", 0.0)
        outboundBasePrice = intent.getDoubleExtra("OUTBOUND_BASE_PRICE", basePrice)
        returnBasePrice   = intent.getDoubleExtra("RETURN_BASE_PRICE", 0.0)
        isRoundTrip       = intent.getBooleanExtra("IS_ROUND_TRIP", false)
        isConnecting = intent.getBooleanExtra("IS_CONNECTING", false)

        val cardEconomy        = findViewById<CardView>(R.id.cardEconomy)
        val cardPremiumEconomy = findViewById<CardView>(R.id.cardPremiumEconomy)
        val cardBusiness       = findViewById<CardView>(R.id.cardBusiness)
        val btnContinue        = findViewById<Button>(R.id.btnContinueClass)
        val tvTotal             = findViewById<TextView>(R.id.tvClassTotal)

        val colorSelected   = ContextCompat.getColor(this, R.color.blue_main)
        val colorUnselected = ContextCompat.getColor(this, R.color.bg_card)

        fun classFeeFor(cls: String) = when (cls) {
            "BUSINESS" -> 250.0
            "PREMIUM_ECONOMY" -> 100.0
            else -> 0.0
        }

        fun updateUI() {
            tvTotal.text = "Total: %.2f €".format(basePrice + classFeeFor(selectedClass))

            cardEconomy.setCardBackgroundColor(
                if (selectedClass == "ECONOMY") colorSelected else colorUnselected
            )
            cardPremiumEconomy.setCardBackgroundColor(
                if (selectedClass == "PREMIUM_ECONOMY") colorSelected else colorUnselected
            )

            cardEconomy.alpha        = if (selectedClass == "ECONOMY") 1.0f else 0.6f
            cardPremiumEconomy.alpha = if (selectedClass == "PREMIUM_ECONOMY") 1.0f else 0.6f
            cardBusiness.alpha       = if (selectedClass == "BUSINESS") 1.0f else 0.6f
        }

        updateUI()

        cardEconomy.setOnClickListener {
            selectedClass = "ECONOMY"
            updateUI()
        }

        cardPremiumEconomy.setOnClickListener {
            selectedClass = "PREMIUM_ECONOMY"
            updateUI()
        }

        cardBusiness.setOnClickListener {
            selectedClass = "BUSINESS"
            updateUI()
        }

        btnContinue.setOnClickListener {
            startActivity(Intent(this, BaggageSelectionActivity::class.java).apply {
                putExtra("FLIGHT_ID", flightId)
                putExtra("SECOND_FLIGHT_ID", secondFlightId)
                putExtra("OUTBOUND_BASE_PRICE", outboundBasePrice)
                putExtra("RETURN_BASE_PRICE", returnBasePrice)
                putExtra("CLASS_FEE", classFeeFor(selectedClass))
                putExtra("SEAT_CLASS", selectedClass)
                putExtra("IS_ROUND_TRIP", isRoundTrip)
                putExtra("IS_CONNECTING", isConnecting)
            })
        }
    }
}