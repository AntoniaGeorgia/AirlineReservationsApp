package com.example.airlinereservationsapp

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat

class BaggageSelectionActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_SEAT = 2001
    }

    private var selectedBaggage = "HAND_ONLY"
    private var baggagePrice = 0.0
    private var seatClass = "ECONOMY"
    private val baggageCards = mutableListOf<Triple<CardView, String, Double>>()

    private var flightId = -1
    private var secondFlightId = -1
    private var isRoundTrip = false
    private var isConnecting = false
    private var isSelectingSecondLeg = false

    private var outboundBaggage = "HAND_ONLY"
    private var outboundBaggagePrice = 0.0

    private var outboundBasePrice = 0.0
    private var returnBasePrice = 0.0
    private var classFee = 0.0

    private var outboundSeatId = -1
    private var outboundSeatLabel: String? = null
    private var outboundSeatPrice = 0.0
    private var returnSeatId = -1
    private var returnSeatLabel: String? = null
    private var returnSeatPrice = 0.0

    private lateinit var tvTotal: TextView
    private lateinit var btnCont: Button
    private lateinit var btnChooseSeat: Button
    private lateinit var container: LinearLayout
    private lateinit var nestedScroll: androidx.core.widget.NestedScrollView
    private lateinit var tvHeaderTitle: TextView
    private lateinit var tvHeaderSubtitle: TextView

    data class BaggageItem(
        val code: String,
        val emoji: String,
        val title: String,
        val subtitle: String,
        val details: String,
        val price: Double
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_baggage_selection)

        flightId          = intent.getIntExtra("FLIGHT_ID", -1)
        secondFlightId    = intent.getIntExtra("SECOND_FLIGHT_ID", -1)
        outboundBasePrice = intent.getDoubleExtra("OUTBOUND_BASE_PRICE", 0.0)
        returnBasePrice   = intent.getDoubleExtra("RETURN_BASE_PRICE", 0.0)
        classFee          = intent.getDoubleExtra("CLASS_FEE", 0.0)
        seatClass         = intent.getStringExtra("SEAT_CLASS") ?: "ECONOMY"
        isRoundTrip       = intent.getBooleanExtra("IS_ROUND_TRIP", false)
        isConnecting      = intent.getBooleanExtra("IS_CONNECTING", false)

        tvTotal          = findViewById(R.id.tvBaggageTotal)
        btnCont          = findViewById(R.id.btnContinueBaggage)
        btnChooseSeat    = findViewById(R.id.btnChooseSpecificSeat)
        container        = findViewById(R.id.llBaggageCards)
        nestedScroll     = findViewById(R.id.nestedScrollBaggage)
        tvHeaderTitle    = findViewById(R.id.tvBaggageHeaderTitle)
        tvHeaderSubtitle = findViewById(R.id.tvBaggageHeaderSubtitle)

        buildBaggageStep()
    }

    private fun buildBaggageStep() {
        val hasSecondLeg = isRoundTrip || isConnecting
        val skipBaggageThisStep = isConnecting && isSelectingSecondLeg

        tvHeaderTitle.text = when {
            isConnecting && isSelectingSecondLeg -> "Loc — Segment 2"
            isConnecting -> "Bagaj & Loc — Segment 1"
            isSelectingSecondLeg -> "Bagaj — Zbor Retur"
            isRoundTrip -> "Bagaj — Zbor Dus"
            else -> "Bagaje"
        }
        tvHeaderSubtitle.text = when {
            isConnecting && isSelectingSecondLeg -> "Alege locul pentru al doilea segment al zborului"
            isConnecting -> "Bagajul se aplică pentru tot traseul (ambele segmente)"
            isSelectingSecondLeg -> "Alege bagajul pentru zborul de întoarcere"
            isRoundTrip -> "Alege bagajul pentru zborul de dus"
            else -> "Poți modifica bagajul și mai târziu"
        }

        nestedScroll.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                nestedScroll.scrollTo(0, 0)
                nestedScroll.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })

        container.removeAllViews()
        baggageCards.clear()

        val dp = resources.displayMetrics.density
        val colorSelected   = ContextCompat.getColor(this, R.color.blue_main)
        val colorUnselected = Color.parseColor("#E0E0E0")

        fun makeBorder(color: Int, width: Int): GradientDrawable {
            return GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(width, color)
                cornerRadius = 16 * dp
            }
        }

        fun updateTotalText() {
            val outboundFare = if (outboundSeatId != -1) outboundSeatPrice else outboundBasePrice
            val returnFare = if (isSelectingSecondLeg) {
                if (returnSeatId != -1) returnSeatPrice else returnBasePrice
            } else 0.0
            val committedOutboundBaggage = if (isSelectingSecondLeg) outboundBaggagePrice else 0.0
            val currentBaggage = if (skipBaggageThisStep) 0.0 else baggagePrice
            tvTotal.text = "Total: %.2f €".format(
                outboundFare + returnFare + classFee + committedOutboundBaggage + currentBaggage
            )
        }

        if (!skipBaggageThisStep) {
            selectedBaggage = "HAND_ONLY"
            baggagePrice = 0.0

            val options = when (seatClass) {
                "BUSINESS" -> listOf(
                    BaggageItem("HAND_ONLY",   "🎒", "1 × Bagaj mic de mână",
                        "40 × 55 × 23 cm, 10 kg", "Inclus în Business", 0.0),
                    BaggageItem("HOLD_23KG",   "🧳", "1 × Bagaj cală 23kg",
                        "119 × 149 × 171 cm, 23 kg", "Inclus în Business\n+ Priority boarding", 0.0),
                    BaggageItem("HOLD_2X23KG", "🧳", "2 × Bagaj cală 23kg",
                        "119 × 149 × 171 cm, 23 kg fiecare", "+ Priority boarding", 35.0)
                )
                else -> listOf(
                    BaggageItem("HAND_ONLY",   "🎒", "1 × Bagaj mic de mână",
                        "30 × 40 × 20 cm, 10 kg", "Inclus", 0.0),
                    BaggageItem("CABIN_20KG",  "🧳", "1 × Bagaj mare de mână",
                        "40 × 55 × 23 cm, 10 kg", "Îmbarcare prioritară", 15.0),
                    BaggageItem("HOLD_23KG",   "🧳", "1 × Bagaj de cală",
                        "119 × 149 × 171 cm, 23 kg", "Se lasă la check-in", 30.0),
                    BaggageItem("HOLD_2X23KG", "🧳", "2 × Bagaj de cală",
                        "119 × 149 × 171 cm, 23 kg", "Se lasă la check-in", 55.0)
                )
            }

            options.forEach { item ->
                val card = CardView(this).apply {
                    radius = 16 * dp
                    cardElevation = 0f
                    setCardBackgroundColor(Color.TRANSPARENT)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.bottomMargin = (12 * dp).toInt() }
                }

                val inner = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(
                        (16 * dp).toInt(), (16 * dp).toInt(),
                        (16 * dp).toInt(), (16 * dp).toInt()
                    )
                    background = makeBorder(colorUnselected, (1 * dp).toInt())
                }

                val row1 = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }

                val tvEmoji = TextView(this).apply {
                    text = item.emoji
                    textSize = 28f
                    setPadding(0, 0, (12 * dp).toInt(), 0)
                }

                val colText = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val tvTitle = TextView(this).apply {
                    text = item.title
                    textSize = 15f
                    setTextColor(Color.parseColor("#111111"))
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }

                val tvSubtitle = TextView(this).apply {
                    text = item.subtitle
                    textSize = 12f
                    setTextColor(Color.parseColor("#666666"))
                    setPadding(0, (2 * dp).toInt(), 0, 0)
                }

                colText.addView(tvTitle)
                colText.addView(tvSubtitle)

                row1.addView(tvEmoji)
                row1.addView(colText)

                inner.addView(row1)

                if (item.details.isNotEmpty()) {
                    val tvDetails = TextView(this).apply {
                        text = item.details
                        textSize = 13f
                        setTextColor(Color.parseColor("#444444"))
                        setPadding(0, (8 * dp).toInt(), 0, 0)
                    }
                    inner.addView(tvDetails)
                }

                val separator = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
                    ).also { it.topMargin = (12 * dp).toInt(); it.bottomMargin = (8 * dp).toInt() }
                    setBackgroundColor(Color.parseColor("#EEEEEE"))
                }
                inner.addView(separator)

                val tvPrice = TextView(this).apply {
                    text = if (item.price > 0) "%.2f €\npentru toate zborurile".format(item.price)
                    else "Inclus"
                    textSize = if (item.price > 0) 16f else 15f
                    setTextColor(
                        if (item.price > 0) Color.parseColor("#111111")
                        else Color.parseColor("#4CAF50")
                    )
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                inner.addView(tvPrice)

                card.addView(inner)
                container.addView(card)
                baggageCards.add(Triple(card, item.code, item.price))

                card.setOnClickListener {
                    selectedBaggage = item.code
                    baggagePrice    = item.price
                    baggageCards.forEach { (c, code, _) ->
                        val sel = code == selectedBaggage
                        (c.getChildAt(0) as? LinearLayout)?.background = makeBorder(
                            if (sel) colorSelected else colorUnselected,
                            if (sel) (2 * dp).toInt() else (1 * dp).toInt()
                        )
                    }
                    updateTotalText()
                }
            }

            (baggageCards.firstOrNull()?.first?.getChildAt(0) as? LinearLayout)
                ?.background = makeBorder(colorSelected, (2 * dp).toInt())
        } else {
            val tvInfo = TextView(this).apply {
                text = "Bagaj ales: ${baggageLabel(outboundBaggage)} (se aplică pentru tot traseul)"
                textSize = 14f
                setTextColor(Color.parseColor("#666666"))
                setPadding(0, 0, 0, (12 * dp).toInt())
            }
            container.addView(tvInfo)
        }

        updateTotalText()

        btnChooseSeat.visibility = View.VISIBLE
        btnChooseSeat.text = if (seatClass == "ECONOMY") "Alege loc specific (+15 €)" else "Alege loc specific (inclus)"

        btnChooseSeat.setOnClickListener {
            val targetFlightId = if (isSelectingSecondLeg) secondFlightId else flightId
            startActivityForResult(Intent(this, SeatSelectionActivity::class.java).apply {
                putExtra("FLIGHT_ID", targetFlightId)
                putExtra("SECOND_FLIGHT_ID", -1)
                putExtra("STANDALONE_MODE", true)
                putExtra("SEAT_CLASS", seatClass)
            }, REQUEST_SEAT)
        }

        btnCont.text = when {
            isConnecting && !isSelectingSecondLeg -> "Continuă → Loc Segment 2"
            isRoundTrip && !isSelectingSecondLeg -> "Continuă → Bagaj retur"
            else -> "Continuă → Loc (aleatoriu)"
        }
        btnCont.setOnClickListener {
            proceedAfterLeg()
        }
    }

    private fun baggageLabel(code: String) = when (code) {
        "HAND_ONLY" -> "Bagaj de mână"
        "CABIN_20KG" -> "Bagaj cabină 20kg"
        "HOLD_23KG" -> "Bagaj cală 23kg"
        "HOLD_2X23KG" -> "2× Bagaj cală 23kg"
        else -> code
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SEAT && resultCode == RESULT_OK && data != null) {
            val seatId    = data.getIntExtra("SELECTED_SEAT_ID", -1)
            val seatLabel = data.getStringExtra("SELECTED_SEAT_LABEL")
            val seatPrice = data.getDoubleExtra("SELECTED_SEAT_PRICE", 0.0)

            if (isSelectingSecondLeg) {
                returnSeatId = seatId
                returnSeatLabel = seatLabel
                returnSeatPrice = seatPrice
            } else {
                outboundSeatId = seatId
                outboundSeatLabel = seatLabel
                outboundSeatPrice = seatPrice
            }
            proceedAfterLeg()
        }
    }

    private fun proceedAfterLeg() {
        val hasSecondLeg = isRoundTrip || isConnecting
        if (hasSecondLeg && !isSelectingSecondLeg) {
            outboundBaggage = selectedBaggage
            outboundBaggagePrice = baggagePrice
            isSelectingSecondLeg = true
            buildBaggageStep()
        } else {
            val finalOutboundBaggage = if (hasSecondLeg) outboundBaggage else selectedBaggage
            val returnBaggage = when {
                isConnecting -> finalOutboundBaggage   // același bagaj, ambele segmente
                isRoundTrip -> selectedBaggage          // bagaj ales separat la pasul 2
                else -> null
            }

            val outboundFare = if (outboundSeatId != -1) outboundSeatPrice else outboundBasePrice
            val returnFare = if (hasSecondLeg) {
                if (returnSeatId != -1) returnSeatPrice else returnBasePrice
            } else 0.0
            val outboundBagP = if (hasSecondLeg) outboundBaggagePrice else baggagePrice
            val returnBagP = if (isRoundTrip) baggagePrice else 0.0  // escală: nu se taxează a doua oară

            val finalPrice = outboundFare + returnFare + classFee + outboundBagP + returnBagP

            startActivity(Intent(this, RandomSeatBookingActivity::class.java).apply {
                putExtra("FLIGHT_ID", flightId)
                putExtra("SECOND_FLIGHT_ID", secondFlightId)
                putExtra("TOTAL_PRICE", finalPrice)
                putExtra("SEAT_CLASS", seatClass)
                putExtra("BAGGAGE_OPTION", finalOutboundBaggage)
                putExtra("SECOND_BAGGAGE_OPTION", returnBaggage)
                putExtra("OUTBOUND_SEAT_ID", outboundSeatId)
                putExtra("OUTBOUND_SEAT_LABEL", outboundSeatLabel)
                putExtra("RETURN_SEAT_ID", returnSeatId)
            })
        }
    }
}