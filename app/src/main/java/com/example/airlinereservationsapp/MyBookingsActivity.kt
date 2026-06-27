package com.example.airlinereservationsapp

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class MyBookingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_bookings)

        val prefs  = getSharedPreferences("prefs", MODE_PRIVATE)
        val userId = prefs.getInt("user_id", -1)
        val role   = prefs.getString("user_role", "PASSENGER")

        if (userId == -1) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val rvBookings  = findViewById<RecyclerView>(R.id.rvBookings)
        val pbLoading   = findViewById<ProgressBar>(R.id.pbBookingsLoading)
        val tvEmpty     = findViewById<TextView>(R.id.tvNoBookings)
        val btnBack     = findViewById<ImageButton>(R.id.btnBackBookings)
        val btnLogout   = findViewById<Button>(R.id.btnLogout)
        val tvUserName  = findViewById<TextView>(R.id.tvUserName)
        val btnAdmin    = findViewById<Button>(R.id.btnAdminPanel)

        val userName = prefs.getString("user_name", "") ?: ""
        if (userName.isNotEmpty()) {
            tvUserName.text = getString(R.string.user_greeting, userName)
        }

        if (role == "ADMIN") {
            btnAdmin.visibility = View.VISIBLE
            btnAdmin.setOnClickListener {
                startActivity(Intent(this, AdminActivity::class.java))
            }
        } else {
            btnAdmin.visibility = View.GONE
        }

        btnBack.setOnClickListener {
            startActivity(Intent(this, FlightSearchActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
            finish()
        }

        btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.logout_title)
                .setMessage(R.string.logout_message)
                .setPositiveButton(R.string.logout_confirm) { _, _ ->
                    prefs.edit { clear() }
                    startActivity(Intent(this, FlightSearchActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                }
                .setNegativeButton(R.string.logout_cancel, null)
                .show()
        }

        rvBookings.layoutManager = LinearLayoutManager(this)
        pbLoading.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.getUserBookings(userId)
                pbLoading.visibility = View.GONE

                if (response.isSuccessful) {
                    val bookings = response.body() ?: emptyList()
                    if (bookings.isEmpty()) {
                        tvEmpty.visibility = View.VISIBLE
                    } else {
                        rvBookings.adapter = BookingCardAdapter(bookings) { booking ->
                            startActivity(
                                Intent(this@MyBookingsActivity, BookingDetailActivity::class.java).apply {
                                    putExtra("BOOKING_ID", booking.bookingId)
                                }
                            )
                        }
                    }
                } else {
                    pbLoading.visibility = View.GONE
                    tvEmpty.visibility = View.VISIBLE
                }
            } catch (_: Exception) {
                pbLoading.visibility = View.GONE
                tvEmpty.visibility = View.VISIBLE
            }
        }
    }
}