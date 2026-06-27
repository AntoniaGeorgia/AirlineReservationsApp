package com.example.airlinereservationsapp

import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class BookingDetailActivity : AppCompatActivity() {
    companion object {
        const val REQUEST_CHECKIN_SEAT = 1001
        const val REQUEST_REBOOK = 1002
    }
    private var bookingId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_booking_detail)

        bookingId = intent.getIntExtra("BOOKING_ID", -1)
        findViewById<ImageButton>(R.id.btnBackDetail).setOnClickListener { finish() }
        loadBookingDetails()
    }

    private fun loadBookingDetails() {
        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.getBookingDetails(bookingId)
                if (!response.isSuccessful) return@launch
                val booking = response.body() ?: return@launch
                bindUI(booking)
            } catch (e: Exception) {
                Toast.makeText(this@BookingDetailActivity, "Eroare incarcare", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindUI(booking: BookingResponse) {
        android.util.Log.d("DEBUG_STATUS", "Status primit: ${booking.status}")
        android.util.Log.d("DEBUG_STATUS", "Status raw string: ${booking.status.name}")
        findViewById<TextView>(R.id.tvDetailRoute).text = "${booking.origin} → ${booking.destination}"
        findViewById<TextView>(R.id.tvDetailFlight).text = booking.flightNumber ?: ""
        findViewById<TextView>(R.id.tvDetailDate).text = booking.departureTime ?: ""
        findViewById<TextView>(R.id.tvDetailSeat).text = "Loc: ${booking.seatLabel}"
        findViewById<TextView>(R.id.tvDetailClass).text = booking.seatClass ?: "ECONOMY"
        findViewById<TextView>(R.id.tvDetailPrice).text = "%.2f €".format(booking.finalPrice)

        val btnDiscover = findViewById<Button>(R.id.btnDiscoverCity)
        val cityNames = mapOf(
            // România
            "OTP" to "București",
            // Europa
            "LHR" to "Londra Heathrow", "LGW" to "Londra Gatwick",
            "CDG" to "Paris", "VIE" to "Viena", "FRA" to "Frankfurt",
            "MAD" to "Madrid", "BCN" to "Barcelona", "FCO" to "Roma",
            "AMS" to "Amsterdam", "MUC" to "München", "ZRH" to "Zürich",
            "BRU" to "Bruxelles", "CPH" to "Copenhaga", "OSL" to "Oslo",
            "ARN" to "Stockholm", "HEL" to "Helsinki", "WAW" to "Varșovia",
            "PRG" to "Praga", "BUD" to "Budapesta", "ATH" to "Atena",
            "IST" to "Istanbul", "LIS" to "Lisabona", "DUB" to "Dublin",
            "MAN" to "Manchester",
            // Asia
            "DXB" to "Dubai", "DOH" to "Doha", "AUH" to "Abu Dhabi",
            "BKK" to "Bangkok", "SIN" to "Singapore", "KUL" to "Kuala Lumpur",
            "NRT" to "Tokyo", "ICN" to "Seoul", "PEK" to "Beijing",
            "BOM" to "Mumbai", "DEL" to "Delhi", "CMB" to "Colombo",
            // America
            "JFK" to "New York JFK", "EWR" to "New York Newark",
            "YYZ" to "Toronto", "ORD" to "Chicago", "MIA" to "Miami",
            "LAX" to "Los Angeles", "GRU" to "São Paulo", "EZE" to "Buenos Aires",
            "BOG" to "Bogotá", "LIM" to "Lima",
            // Africa
            "CAI" to "Cairo", "CMN" to "Casablanca", "JNB" to "Johannesburg",
            "NBO" to "Nairobi", "LOS" to "Lagos", "ACC" to "Accra",
            "TUN" to "Tunis", "ALG" to "Alger",
            // Australia
            "SYD" to "Sydney", "MEL" to "Melbourne", "BNE" to "Brisbane",
            "AKL" to "Auckland"
        )
        val cityName = cityNames[booking.destination] ?: booking.destination
        btnDiscover.text = "🌍 Descoperă $cityName"
        btnDiscover.visibility = View.VISIBLE
        btnDiscover.setOnClickListener {
            startActivity(Intent(this, CityDiscoveryActivity::class.java).apply {
                putExtra("CITY_IATA", booking.destination ?: "")
                putExtra("CITY_NAME", booking.destination ?: "")
            })
        }

        val tvStatus = findViewById<TextView>(R.id.tvDetailStatus)
        tvStatus.text = booking.status.name
        tvStatus.setBackgroundColor(when (booking.status) {
            BookingStatus.PAID       -> 0xFF4CAF50.toInt()
            BookingStatus.CHECKED_IN -> 0xFF2196F3.toInt()
            BookingStatus.BOARDED    -> 0xFF9C27B0.toInt()
            BookingStatus.CANCELLED  -> 0xFFF44336.toInt()
            BookingStatus.REFUND_PENDING -> 0xFFD32F2F.toInt()
            else                     -> 0xFFFF9800.toInt()
        })

        val tvBaggage = findViewById<TextView>(R.id.tvDetailBaggage)
        tvBaggage.text = when (booking.baggageOption) {
            "HAND_ONLY"   -> "✓ Bagaj de mana (inclus)"
            "CABIN_20KG"  -> "✓ Bagaj cabina 20kg"
            "HOLD_23KG"   -> "✓ Bagaj cala 23kg"
            "HOLD_2X23KG" -> "✓ 2x Bagaj cala 23kg"
            else           -> "✓ Bagaj de mana (inclus)"
        }

        val ivQr = findViewById<ImageView>(R.id.ivBoardingQr)
        if (!booking.qrCode.isNullOrEmpty()) {
            val bytes = Base64.decode(booking.qrCode, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ivQr.setImageBitmap(bmp)
            ivQr.visibility = View.VISIBLE
        }

        setupCheckInButton(booking)
        setupBaggageSelector(booking)
        setupDownloadButton(booking)
        setupModificationButton()
        checkApprovedModificationAndSetupRebook(booking)
    }

    private fun checkApprovedModificationAndSetupRebook(booking: BookingResponse) {
        val userId = getSharedPreferences("prefs", MODE_PRIVATE).getInt("user_id", -1)
        if (userId == -1) {
            setupRebookButton(booking, false)
            return
        }
        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.getUserRequests(userId)
                val hasApproved = if (response.isSuccessful) {
                    response.body()?.any {
                        it.bookingId == bookingId &&
                                it.status == "APPROVED" &&
                                (it.requestType == "DESTINATION" || it.requestType == "TIME")
                    } ?: false
                } else false
                setupRebookButton(booking, hasApproved)
            } catch (_: Exception) {
                setupRebookButton(booking, false)
            }
        }
    }

    private fun setupRebookButton(booking: BookingResponse, hasApprovedModRequest: Boolean) {
        val btnRebook = findViewById<Button>(R.id.btnRebook)
        if (booking.status == BookingStatus.REFUND_PENDING || hasApprovedModRequest) {
            btnRebook.visibility = View.VISIBLE
            btnRebook.text = if (hasApprovedModRequest && booking.status != BookingStatus.REFUND_PENDING)
                "✓ Alege noul zbor (aprobat de admin)"
            else
                "Alege zbor alternativ"
            btnRebook.setOnClickListener {
                startActivityForResult(
                    Intent(this, RebookingActivity::class.java).apply {
                        putExtra("BOOKING_ID", bookingId)
                    },
                    REQUEST_REBOOK
                )
            }
        } else {
            btnRebook.visibility = View.GONE
        }
    }

    private fun setupModificationButton() {
        val btnMod = findViewById<Button>(R.id.btnRequestModification) ?: return
        btnMod.setOnClickListener {
            startActivity(
                Intent(this, SendModificationRequestActivity::class.java).apply {
                    putExtra("BOOKING_ID", bookingId)
                }
            )
        }
    }

    private fun setupCheckInButton(booking: BookingResponse) {
        val btnCheckIn    = findViewById<Button>(R.id.btnCheckIn)
        val tvCheckInInfo = findViewById<TextView>(R.id.tvCheckInInfo)
        btnCheckIn.visibility = View.VISIBLE
        btnCheckIn.isEnabled = true
        if (booking.status == BookingStatus.CHECKED_IN || booking.status == BookingStatus.BOARDED) {
            btnCheckIn.text = "✓ Check-in efectuat"
            btnCheckIn.isEnabled = false
            tvCheckInInfo.visibility = View.GONE
            return
        }

        if (booking.status != BookingStatus.PAID) {
            btnCheckIn.visibility = View.GONE
            tvCheckInInfo.visibility = View.GONE
            return
        }

        val departureTime = try {
            val text = booking.departureTime ?: throw Exception("null")
            val normalized = when {
                text.contains("T") && text.length == 16 -> text.replace("T", " ") + ":00"
                text.contains("T") -> text.replace("T", " ")
                else -> text
            }
            LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        } catch (_: Exception) { null }

        val hoursUntilFlight = departureTime?.let {
            ChronoUnit.HOURS.between(LocalDateTime.now(), it)
        } ?: 999L

        when {
            hoursUntilFlight > 48 -> {
                btnCheckIn.text = "Check-in anticipat (9.99 €)"
                tvCheckInInfo.text = "Check-in gratuit disponibil cu 24-48h inainte de zbor"
                tvCheckInInfo.visibility = View.VISIBLE
                btnCheckIn.setOnClickListener { showEarlyCheckInDialog(9.99, booking) }
            }
            hoursUntilFlight in 24..48 -> {
                btnCheckIn.text = "Check-in gratuit"
                tvCheckInInfo.text = "Esti in fereastra de check-in gratuita!"
                tvCheckInInfo.visibility = View.VISIBLE
                btnCheckIn.setOnClickListener { showCheckInOptionsDialog(booking, 0.0) }
            }
            hoursUntilFlight in 1..23 -> {
                btnCheckIn.text = "Check-in online"
                tvCheckInInfo.text = "Check-in disponibil — zbor in ${hoursUntilFlight}h"
                tvCheckInInfo.visibility = View.VISIBLE
                btnCheckIn.setOnClickListener { showCheckInOptionsDialog(booking, 0.0) }
            }
            else -> {
                btnCheckIn.text = "Check-in indisponibil"
                btnCheckIn.isEnabled = false
                tvCheckInInfo.text = "Check-in-ul s-a inchis"
                tvCheckInInfo.visibility = View.VISIBLE
            }
        }
    }

    private fun showEarlyCheckInDialog(fee: Double, booking: BookingResponse) {
        AlertDialog.Builder(this)
            .setTitle("Check-in anticipat")
            .setMessage("Check-in-ul gratuit e disponibil cu 24-48h înainte de zbor.\n\nDorești să faci check-in anticipat acum pentru %.2f €?".format(fee))
            .setPositiveButton("Da, platesc %.2f €".format(fee)) { _, _ ->
                showCheckInOptionsDialog(booking, fee)
            }
            .setNegativeButton("Anulează", null)
            .show()
    }
    private fun showCheckInOptionsDialog(booking: BookingResponse, extraFee: Double) {
        val hasExistingSeat = !booking.seatLabel.isNullOrEmpty()

        if (hasExistingSeat) {
            AlertDialog.Builder(this)
                .setTitle("Check-in")
                .setMessage("Locul tău este ${booking.seatLabel}. Vrei să îl păstrezi sau să îl schimbi?")
                .setPositiveButton("Păstrează locul") { _, _ ->
                    performCheckIn(extraFee > 0, extraFee)  // ← fix aici
                }
                .setNegativeButton("Schimbă locul (+15€)") { _, _ ->
                    val intent = Intent(this, SeatSelectionActivity::class.java).apply {
                        putExtra("FLIGHT_ID", booking.flightId ?: -1)
                        putExtra("BOOKING_ID", bookingId)
                        putExtra("SEAT_CLASS", booking.seatClass ?: "ECONOMY")
                        putExtra("IS_CHECKIN", true)
                        putExtra("EXTRA_FEE", extraFee)
                    }
                    startActivityForResult(intent, REQUEST_CHECKIN_SEAT)
                }
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Alege locul")
                .setMessage("Dorești să alegi un loc specific contra cost sau check-in automat?")
                .setPositiveButton("Aleg locul (+15€)") { _, _ ->  // ← doar UN setPositiveButton
                    val intent = Intent(this, SeatSelectionActivity::class.java).apply {
                        putExtra("FLIGHT_ID", booking.flightId ?: -1)
                        putExtra("BOOKING_ID", bookingId)
                        putExtra("SEAT_CLASS", booking.seatClass ?: "ECONOMY")
                        putExtra("IS_CHECKIN", true)
                        putExtra("EXTRA_FEE", extraFee)
                    }
                    startActivityForResult(intent, REQUEST_CHECKIN_SEAT)
                }
                .setNegativeButton("Check-in automat") { _, _ ->  // ← fix aici
                    performCheckIn(extraFee > 0, extraFee)
                }
                .show()
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CHECKIN_SEAT && resultCode == RESULT_OK) {
            val seatId = data?.getIntExtra("SELECTED_SEAT_ID", -1) ?: -1
            val seatFee = data?.getDoubleExtra("SEAT_FEE", 0.0) ?: 0.0
            val extraFee = data?.getDoubleExtra("EXTRA_FEE", 0.0) ?: 0.0
            if (seatId > 0) {
                performCheckInWithSeat(seatId, seatFee + extraFee)
            }
        }
        if (requestCode == REQUEST_REBOOK && resultCode == RESULT_OK) {
            loadBookingDetails() // reîncarcă — zbor nou, loc nou, status PAID
        }
    }

    private fun performCheckInWithSeat(seatId: Int, totalFee: Double) {
        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.performCheckIn(
                    bookingId = bookingId,
                    isPaid = totalFee > 0,
                    fee = totalFee,
                    seatId = seatId
                )
                if (response.isSuccessful) {
                    Toast.makeText(this@BookingDetailActivity, "Check-in efectuat!", Toast.LENGTH_SHORT).show()
                    loadBookingDetails()
                } else {
                    Toast.makeText(this@BookingDetailActivity, "Eroare check-in", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@BookingDetailActivity, "Eroare: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun performCheckIn(isPaid: Boolean, fee: Double) {
        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.performCheckIn(bookingId, isPaid, fee)
                if (response.isSuccessful) {
                    Toast.makeText(this@BookingDetailActivity, "Check-in efectuat cu succes!", Toast.LENGTH_SHORT).show()
                    loadBookingDetails()
                }  else {
            val errorBody = response.errorBody()?.string() ?: ""
            if (response.code() == 409 && errorBody.contains("voucherCode")) {
                val json = org.json.JSONObject(errorBody)
                showOverbookedDialog(json.optString("voucherCode"), json.optString("voucherAmount"))
            } else {
                Toast.makeText(this@BookingDetailActivity, "Eroare check-in", Toast.LENGTH_SHORT).show()
            }
        }
            } catch (e: Exception) {
                Toast.makeText(this@BookingDetailActivity, "Eroare: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun showOverbookedDialog(code: String, amount: String) {
        AlertDialog.Builder(this)
            .setTitle("✈ Zbor supravândut")
            .setMessage(
                "Ne pare rău — zborul a fost supravândut și nu mai sunt locuri disponibile.\n\n" +
                        "Primești un voucher de compensație de $amount € (cod: $code) " +
                        "și poți alege gratuit un zbor alternativ."
            )
            .setPositiveButton("Vezi zboruri alternative") { _, _ ->
                startActivityForResult(
                    Intent(this, RebookingActivity::class.java).apply {
                        putExtra("BOOKING_ID", bookingId)
                    },
                    REQUEST_REBOOK
                )
            }
            .setNegativeButton("OK") { _, _ -> loadBookingDetails() }
            .show()
    }

    private fun setupBaggageSelector(booking: BookingResponse) {
        val layoutBaggage = findViewById<LinearLayout>(R.id.layoutBaggageSelector)

        if (booking.status == BookingStatus.CHECKED_IN ||
            booking.status == BookingStatus.BOARDED ||
            booking.status == BookingStatus.CANCELLED) {
            layoutBaggage.visibility = View.GONE
            return
        }

        layoutBaggage.visibility = View.VISIBLE

        val options = when (booking.seatClass) {
            "BUSINESS" -> listOf(
                BaggageOption("HAND_ONLY",   "Bagaj de mana (inclus)", 0.0),
                BaggageOption("HOLD_23KG",   "1x Bagaj cala 23kg (inclus Business)", 0.0),
                BaggageOption("HOLD_2X23KG", "2x Bagaj cala 23kg", 35.0)
            )
            "PREMIUM_ECONOMY" -> listOf(
                BaggageOption("HAND_ONLY",   "Bagaj de mana (inclus)", 0.0),
                BaggageOption("HOLD_23KG",   "1x Bagaj cala 23kg (inclus)", 0.0),
                BaggageOption("HOLD_2X23KG", "2x Bagaj cala 23kg", 35.0)
            )
            else -> listOf(
                BaggageOption("HAND_ONLY",   "Doar bagaj de mana (inclus)", 0.0),
                BaggageOption("CABIN_20KG",  "Bagaj cabina 20kg", 15.0),
                BaggageOption("HOLD_23KG",   "Bagaj cala 23kg", 30.0),
                BaggageOption("HOLD_2X23KG", "2x Bagaj cala 23kg", 55.0)
            )
        }

        val radioGroup = findViewById<RadioGroup>(R.id.rgBaggageOptions)
        radioGroup.setOnCheckedChangeListener(null)
        radioGroup.removeAllViews()

        options.forEach { opt ->
            val rb = RadioButton(this).apply {
                text = if (opt.price > 0) "${opt.label} (+%.2f €)".format(opt.price) else opt.label
                tag = opt.code
                isChecked = opt.code == booking.baggageOption
                setPadding(8, 12, 8, 12)
            }
            radioGroup.addView(rb)
        }

        radioGroup.setOnCheckedChangeListener { group, checkedId ->
            val rb = group.findViewById<RadioButton>(checkedId) ?: return@setOnCheckedChangeListener
            val selectedCode = rb.tag as? String ?: return@setOnCheckedChangeListener
            val selectedOpt = options.find { it.code == selectedCode } ?: return@setOnCheckedChangeListener
            updateBaggageOption(selectedCode, selectedOpt.price)
        }
    }

    private fun updateBaggageOption(code: String, extraPrice: Double) {
        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.updateBaggage(bookingId, code, extraPrice)
                if (response.isSuccessful) {
                    Toast.makeText(this@BookingDetailActivity,
                        if (extraPrice > 0) "Bagaj adaugat! +%.2f €".format(extraPrice)
                        else "Optiune bagaj actualizata",
                        Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(this@BookingDetailActivity, "Eroare actualizare bagaj", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupDownloadButton(booking: BookingResponse) {
        val btnDownload = findViewById<Button>(R.id.btnDownloadPass)

        if (booking.status != BookingStatus.CHECKED_IN && booking.status != BookingStatus.BOARDED) {
            btnDownload.visibility = View.GONE
            return
        }

        btnDownload.visibility = View.VISIBLE
        btnDownload.setOnClickListener {
            startActivity(Intent(this, BoardingPassActivity::class.java).apply {
                putExtra("BOOKING_ID", bookingId)
                putExtra("PASSENGER_NAME", "${booking.firstNamePassenger ?: ""} ${booking.lastNamePassenger ?: ""}".trim())
                putExtra("SEAT_LABEL", booking.seatLabel ?: "N/A")
                putExtra("QR_CODE", booking.boardingToken ?: "")
                putExtra("SEAT_CLASS", booking.seatClass ?: "ECONOMY")
                putExtra("BAGGAGE_OPTION", booking.baggageOption ?: "HAND_ONLY")
                putExtra("ORIGIN", booking.origin ?: "?")
                putExtra("DESTINATION", booking.destination ?: "?")
            })
        }
    }

    data class BaggageOption(val code: String, val label: String, val price: Double)
}