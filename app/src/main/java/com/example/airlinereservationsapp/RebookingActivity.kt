package com.example.airlinereservationsapp

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class RebookingActivity : AppCompatActivity() {

    private var bookingId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rebooking)

        bookingId = intent.getIntExtra("BOOKING_ID", -1)
        if (bookingId == -1) {
            Toast.makeText(this, "Rezervare invalidă", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadAlternatives()
    }

    private fun loadAlternatives() {
        val progress = findViewById<ProgressBar>(R.id.pbRebooking)
        val tvEmpty = findViewById<TextView>(R.id.tvNoAlternatives)
        val recycler = findViewById<RecyclerView>(R.id.recyclerAlternatives)

        recycler.layoutManager = LinearLayoutManager(this)
        progress.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.getAlternatives(bookingId)
                progress.visibility = View.GONE

                if (response.isSuccessful) {
                    val alternatives = response.body() ?: emptyList()
                    if (alternatives.isEmpty()) {
                        tvEmpty.visibility = View.VISIBLE
                    } else {
                        tvEmpty.visibility = View.GONE
                        recycler.adapter = AlternativesAdapter(alternatives) { flight ->
                            confirmRebook(flight)
                        }
                    }
                } else {
                    tvEmpty.visibility = View.VISIBLE
                    tvEmpty.text = "Nu s-au putut încărca alternativele."
                }
            } catch (e: Exception) {
                progress.visibility = View.GONE
                tvEmpty.visibility = View.VISIBLE
                tvEmpty.text = "Eroare: ${e.message}"
            }
        }
    }

    private fun confirmRebook(flight: AlternativeFlightDTO) {
        val costText = when {
            flight.isFree -> "fără cost suplimentar."
            flight.priceDifference > 0 -> "Vei plăti o diferență de %.2f €.".format(flight.priceDifference)
            flight.priceDifference < 0 -> "Vei primi înapoi %.2f € din diferența de preț.".format(-flight.priceDifference)
            else -> "fără diferență de preț."
        }
        AlertDialog.Builder(this)
            .setTitle("Confirmă rebooking")
            .setMessage(
                "Rezervarea ta va fi mutată pe zborul ${flight.flightNumber} " +
                        "(${flight.departureTime.take(16).replace("T", " ")}), $costText\n\n" +
                        "Vei primi un loc nou și va trebui să refaci check-in-ul."
            )
            .setPositiveButton("Confirmă") { _, _ -> performRebook(flight.flightId) }
            .setNegativeButton("Anulează", null)
            .show()
    }

    private fun performRebook(newFlightId: Int) {
        val progress = findViewById<ProgressBar>(R.id.pbRebooking)
        progress.visibility = View.VISIBLE


        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.rebookBooking(bookingId, RebookRequest(newFlightId))
                progress.visibility = View.GONE
                android.util.Log.e("DEBUG_REBOOK", "code=${response.code()} error=${response.errorBody()?.string()}")
                if (response.isSuccessful) {
                    val result = response.body()
                    Toast.makeText(
                        this@RebookingActivity,
                        result?.message ?: "Rezervare mutată cu succes",
                        Toast.LENGTH_LONG
                    ).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    Toast.makeText(
                        this@RebookingActivity,
                        "Rebooking eșuat. Încearcă alt zbor.",
                        Toast.LENGTH_LONG
                    ).show()
                    loadAlternatives() // reîncarcă — poate locurile s-au ocupat între timp
                }
            } catch (e: Exception) {
                progress.visibility = View.GONE
                Toast.makeText(this@RebookingActivity, "Eroare: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}



class AlternativesAdapter(
    private val flights: List<AlternativeFlightDTO>,
    private val onSelect: (AlternativeFlightDTO) -> Unit
) : RecyclerView.Adapter<AlternativesAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvFlightNumber: TextView = view.findViewById(R.id.tvAltFlightNumber)
        val tvRoute: TextView = view.findViewById(R.id.tvAltRoute)
        val tvDeparture: TextView = view.findViewById(R.id.tvAltDeparture)
        val tvSeats: TextView = view.findViewById(R.id.tvAltSeats)
        val tvPriceDiff: TextView = view.findViewById(R.id.tvAltPriceDiff)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alternative_flight, parent, false)
        return VH(view)
    }

    override fun getItemCount() = flights.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val f = flights[position]
        holder.tvFlightNumber.text = f.flightNumber
        holder.tvRoute.text = "${f.origin} ✈ ${f.destination}"
        holder.tvDeparture.text = f.departureTime.take(16).replace("T", "  ")
        holder.tvSeats.text = "${f.freeSeats} locuri libere"

        holder.tvPriceDiff.text = when {
            f.isFree -> "Gratuit — fără cost suplimentar"
            f.priceDifference > 0 -> "Diferență de plată: +%.2f €".format(f.priceDifference)
            f.priceDifference < 0 -> "Primești înapoi %.2f €".format(-f.priceDifference)
            else -> "Același preț"
        }

        holder.itemView.setOnClickListener { onSelect(f) }
    }
}