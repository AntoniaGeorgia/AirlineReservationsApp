package com.example.airlinereservationsapp

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import android.app.AlertDialog
import android.widget.EditText
import android.widget.Button
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class AdminActivity : AppCompatActivity() {

    private lateinit var tabUsers: TextView
    private lateinit var tabRequests: TextView
    private lateinit var tabAircrafts: TextView
    private lateinit var tabFlights: TextView
    private lateinit var rvUsers: RecyclerView
    private lateinit var rvRequests: RecyclerView
    private lateinit var rvAircrafts: RecyclerView
    private lateinit var rvFlights: RecyclerView
    private lateinit var pbLoading: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var btnAddAircraft: Button
    private lateinit var btnAddFlight: Button

    private lateinit var btnScanBoarding: Button

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) validateBoarding(result.contents)
    }

    private lateinit var etSearchFlights: EditText
    private var allFlights: List<FlightDTO> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val role = prefs.getString("user_role", "PASSENGER")
        if (role != "ADMIN") {
            Toast.makeText(this, "Acces interzis", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        tabUsers     = findViewById(R.id.tabUsers)
        tabRequests  = findViewById(R.id.tabRequests)
        tabAircrafts = findViewById(R.id.tabAircrafts)
        tabFlights   = findViewById(R.id.tabFlights)
        rvUsers      = findViewById(R.id.rvAdminUsers)
        rvRequests   = findViewById(R.id.rvAdminRequests)
        rvAircrafts  = findViewById(R.id.rvAdminAircrafts)
        rvFlights    = findViewById(R.id.rvAdminFlights)
        pbLoading    = findViewById(R.id.pbAdminLoading)
        tvEmpty      = findViewById(R.id.tvAdminEmpty)

        btnAddAircraft = findViewById(R.id.btnAddAircraft)
        btnAddFlight = findViewById(R.id.btnAddFlight)

        etSearchFlights = findViewById(R.id.etSearchFlights)
        etSearchFlights.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = applyFlightFilter()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        btnAddAircraft.setOnClickListener { showAddAircraftDialog() }
        btnAddFlight.setOnClickListener { showAddFlightDialog() }
        rvUsers.layoutManager     = LinearLayoutManager(this)
        rvRequests.layoutManager  = LinearLayoutManager(this)
        rvAircrafts.layoutManager = LinearLayoutManager(this)
        rvFlights.layoutManager   = LinearLayoutManager(this)

        findViewById<ImageButton>(R.id.btnBackAdmin).setOnClickListener { finish() }

        tabUsers.setOnClickListener     { showTab(0) }
        tabRequests.setOnClickListener  { showTab(1) }
        tabAircrafts.setOnClickListener { showTab(2) }
        tabFlights.setOnClickListener   { showTab(3) }

        btnScanBoarding = findViewById(R.id.btnScanBoarding)
        btnScanBoarding.setOnClickListener {
            barcodeLauncher.launch(ScanOptions().apply {
                setPrompt("Scanează boarding pass-ul")
                setBeepEnabled(true)
                setOrientationLocked(true)
            })
        }


        showTab(0)


    }

    private fun validateBoarding(token: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.scanBoarding(token)
                if (response.isSuccessful) {
                    AlertDialog.Builder(this@AdminActivity)
                        .setTitle("✅ Îmbarcat")
                        .setMessage(response.body()?.get("message") ?: "OK")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    val err = response.errorBody()?.string() ?: ""
                    AlertDialog.Builder(this@AdminActivity)
                        .setTitle("❌ Respins")
                        .setMessage(when {
                            "deja" in err -> "Pasager deja îmbarcat"
                            "check-in" in err -> "Pasagerul nu a făcut check-in"
                            else -> "Boarding pass invalid"
                        })
                        .setPositiveButton("OK", null)
                        .show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AdminActivity, "Eroare: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showTab(index: Int) {
        val active = ContextCompat.getColor(this, R.color.blue_main)
        val inactive = ContextCompat.getColor(this, R.color.blue_dark)

        tabUsers.setBackgroundColor(if (index == 0) active else inactive)
        tabRequests.setBackgroundColor(if (index == 1) active else inactive)
        tabAircrafts.setBackgroundColor(if (index == 2) active else inactive)
        tabFlights.setBackgroundColor(if (index == 3) active else inactive)

        rvUsers.visibility     = if (index == 0) View.VISIBLE else View.GONE
        rvRequests.visibility  = if (index == 1) View.VISIBLE else View.GONE
        rvAircrafts.visibility = if (index == 2) View.VISIBLE else View.GONE
        rvFlights.visibility   = if (index == 3) View.VISIBLE else View.GONE
        btnAddAircraft.visibility = if (index == 2) View.VISIBLE else View.GONE
        btnAddFlight.visibility = if (index == 3) View.VISIBLE else View.GONE
        etSearchFlights.visibility = if (index == 3) View.VISIBLE else View.GONE


        when (index) {
            0 -> loadUsers()
            1 -> loadRequests()
            2 -> loadAircrafts()
            3 -> loadFlights()
        }
    }

    private fun loadUsers() {
        pbLoading.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.getAllUsers()
                pbLoading.visibility = View.GONE
                if (response.isSuccessful) {
                    val users = response.body() ?: emptyList()
                    if (users.isEmpty()) {
                        tvEmpty.visibility = View.VISIBLE
                    } else {
                        rvUsers.adapter = AdminUsersAdapter(users)
                    }
                }
            } catch (_: Exception) {
                pbLoading.visibility = View.GONE
                tvEmpty.text = "Eroare la incarcare"
                tvEmpty.visibility = View.VISIBLE
            }
        }
    }

    private fun loadRequests() {
        pbLoading.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.getAllRequests()
                pbLoading.visibility = View.GONE
                if (response.isSuccessful) {
                    val requests = response.body() ?: emptyList()
                    if (requests.isEmpty()) {
                        tvEmpty.text = "Nu sunt requesturi"
                        tvEmpty.visibility = View.VISIBLE
                    } else {
                        rvRequests.adapter = AdminRequestsAdapter(requests) {
                            loadRequests()
                        }
                    }
                }
            } catch (_: Exception) {
                pbLoading.visibility = View.GONE
                tvEmpty.text = "Eroare la incarcare"
                tvEmpty.visibility = View.VISIBLE
            }
        }
    }

    private fun loadAircrafts() {
        pbLoading.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.getAdminAircrafts()
                pbLoading.visibility = View.GONE
                if (response.isSuccessful) {
                    val aircrafts = response.body() ?: emptyList()
                    if (aircrafts.isEmpty()) {
                        tvEmpty.text = "Nu sunt aeronave"
                        tvEmpty.visibility = View.VISIBLE
                    } else {
                        rvAircrafts.adapter = AdminAircraftsAdapter(aircrafts) {
                            loadAircrafts()
                        }
                    }
                }
            } catch (_: Exception) {
                pbLoading.visibility = View.GONE
                tvEmpty.text = "Eroare la incarcare"
                tvEmpty.visibility = View.VISIBLE
            }
        }
    }

    private fun loadFlights() {
        pbLoading.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.getAdminFlights()
                pbLoading.visibility = View.GONE
                if (response.isSuccessful) {
                    allFlights = response.body() ?: emptyList()
                    if (allFlights.isEmpty()) {
                        tvEmpty.text = "Nu sunt zboruri"
                        tvEmpty.visibility = View.VISIBLE
                    } else {
                        applyFlightFilter()
                    }
                }
            } catch (_: Exception) {
                pbLoading.visibility = View.GONE
                tvEmpty.text = "Eroare la incarcare"
                tvEmpty.visibility = View.VISIBLE
            }
        }
    }

    private fun applyFlightFilter() {
        val q = etSearchFlights.text.toString().trim().uppercase()
        val filtered = if (q.isEmpty()) allFlights else allFlights.filter {
            it.flightNumber.uppercase().contains(q) ||
                    it.origin.uppercase().contains(q) ||
                    it.destination.uppercase().contains(q)
        }
        tvEmpty.text = "Niciun zbor găsit"
        tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        rvFlights.adapter = AdminFlightsAdapter(filtered) { loadFlights() }
    }
    private fun showAddAircraftDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }
        val etModel = EditText(this).apply { hint = "Model (ex: Boeing 737)" }
        val etRows = EditText(this).apply {
            hint = "Număr rânduri"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val etCols = EditText(this).apply {
            hint = "Număr coloane"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        layout.addView(etModel)
        layout.addView(etRows)
        layout.addView(etCols)

        AlertDialog.Builder(this)
            .setTitle("Adaugă aeronavă")
            .setView(layout)
            .setPositiveButton("Adaugă") { _, _ ->
                val model = etModel.text.toString()
                val rows = etRows.text.toString().toIntOrNull() ?: return@setPositiveButton
                val cols = etCols.text.toString().toIntOrNull() ?: return@setPositiveButton

                lifecycleScope.launch {
                    try {
                        RetrofitInstance.api.createAircraft(
                            CreateAircraftRequest(model, rows, cols)
                        )
                        Toast.makeText(this@AdminActivity, "Aeronavă adăugată!", Toast.LENGTH_SHORT).show()
                        loadAircrafts()
                    } catch (e: Exception) {
                        Toast.makeText(this@AdminActivity, "Eroare: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Anulează", null)
            .show()
    }

    private fun showAddFlightDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }
        val etNumber = EditText(this).apply { hint = "Număr zbor (ex: RO123)" }
        val etOrigin = EditText(this).apply { hint = "Origine IATA (ex: OTP)" }
        val etDest = EditText(this).apply { hint = "Destinație IATA (ex: LHR)" }

        val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
        var departure: java.time.LocalDateTime? = null
        var arrival: java.time.LocalDateTime? = null

        val tvDep = TextView(this).apply {
            text = "📅 Apasă pentru data și ora plecării"
            textSize = 16f
            setPadding(0, 28, 0, 28)
        }
        val etDurH = EditText(this).apply {
            hint = "Durată zbor — ore (ex: 3)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val etDurM = EditText(this).apply {
            hint = "Durată zbor — minute (ex: 10)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val tvArr = TextView(this).apply {
            text = "Sosire: —"
            textSize = 15f
            setPadding(0, 16, 0, 16)
        }

        fun updateArrival() {
            val dep = departure
            val h = etDurH.text.toString().toLongOrNull() ?: 0L
            val m = etDurM.text.toString().toLongOrNull() ?: 0L
            arrival = if (dep != null && (h > 0 || m > 0)) dep.plusHours(h).plusMinutes(m) else null
            tvArr.text = arrival?.let { "Sosire: ${it.format(fmt).replace("T", "  ")}" } ?: "Sosire: —"
        }

        val durationWatcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = updateArrival()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        }
        etDurH.addTextChangedListener(durationWatcher)
        etDurM.addTextChangedListener(durationWatcher)

        tvDep.setOnClickListener {
            val now = java.util.Calendar.getInstance()
            android.app.DatePickerDialog(this, { _, year, month, day ->
                android.app.TimePickerDialog(this, { _, hour, minute ->
                    departure = java.time.LocalDateTime.of(year, month + 1, day, hour, minute)
                    tvDep.text = "Plecare: ${departure!!.format(fmt).replace("T", "  ")}"
                    updateArrival()
                }, now.get(java.util.Calendar.HOUR_OF_DAY), 0, true).show()
            }, now.get(java.util.Calendar.YEAR), now.get(java.util.Calendar.MONTH),
                now.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }

        val etPrice = EditText(this).apply {
            hint = "Preț de bază (€)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val etAircraftId = EditText(this).apply {
            hint = "ID aeronavă"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        layout.addView(etNumber)
        layout.addView(etOrigin)
        layout.addView(etDest)
        layout.addView(tvDep)
        layout.addView(etDurH)
        layout.addView(etDurM)
        layout.addView(tvArr)
        layout.addView(etPrice)
        layout.addView(etAircraftId)

        AlertDialog.Builder(this)
            .setTitle("Adaugă zbor")
            .setView(layout)
            .setPositiveButton("Adaugă") { _, _ ->
                val dep = departure
                val arr = arrival
                if (dep == null) {
                    Toast.makeText(this, "Alege data și ora plecării", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (arr == null) {
                    Toast.makeText(this, "Completează durata zborului", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    try {
                        val response = RetrofitInstance.api.createFlight(
                            CreateFlightRequest(
                                flightNumber = etNumber.text.toString().trim(),
                                origin = etOrigin.text.toString().trim().uppercase(),
                                destination = etDest.text.toString().trim().uppercase(),
                                departureTime = dep.format(fmt),
                                arrivalTime = arr.format(fmt),
                                basePrice = etPrice.text.toString().toDoubleOrNull() ?: 0.0,
                                aircraftId = etAircraftId.text.toString().toIntOrNull() ?: 0
                            )
                        )
                        if (response.isSuccessful) {
                            Toast.makeText(this@AdminActivity, "Zbor adăugat!", Toast.LENGTH_SHORT).show()
                            loadFlights()
                        } else {
                            Toast.makeText(this@AdminActivity,
                                "Eroare server (${response.code()})", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@AdminActivity, "Eroare: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Anulează", null)
            .show()
    }
}