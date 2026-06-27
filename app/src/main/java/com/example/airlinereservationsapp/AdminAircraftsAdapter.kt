package com.example.airlinereservationsapp

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AdminAircraftsAdapter(
    private val aircrafts: List<AircraftDTO>,
    private val onRefresh: () -> Unit
) : RecyclerView.Adapter<AdminAircraftsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvModel: TextView = view.findViewById(R.id.tvAircraftModel)
        val tvSeats: TextView = view.findViewById(R.id.tvAircraftSeats)
        val btnDelete: Button = view.findViewById(R.id.btnDeleteAircraft)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_aircraft, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val aircraft = aircrafts[position]
        holder.tvModel.text = aircraft.model
        holder.tvSeats.text = "${aircraft.layoutRows} rânduri × ${aircraft.layoutCols} coloane = ${aircraft.totalSeats} locuri"

        holder.btnDelete.setOnClickListener {
            AlertDialog.Builder(holder.itemView.context)
                .setTitle("Șterge aeronavă")
                .setMessage("Ești sigur că vrei să ștergi ${aircraft.model}?")
                .setPositiveButton("Șterge") { _, _ ->
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            val response = RetrofitInstance.api.deleteAircraft(aircraft.id)
                            if (response.isSuccessful) {
                                val message = response.body()?.message ?: "Aeronavă ștearsă"
                                Toast.makeText(holder.itemView.context, message, Toast.LENGTH_LONG).show()
                                onRefresh()
                            } else {
                                val errorBody = response.errorBody()?.string()
                                val errorMsg = try {
                                    org.json.JSONObject(errorBody ?: "").optString("error", "Eroare la ștergere")
                                } catch (_: Exception) {
                                    "Eroare la ștergere"
                                }
                                Toast.makeText(holder.itemView.context, errorMsg, Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(holder.itemView.context, "Eroare: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Anulează", null)
                .show()
        }
    }

    override fun getItemCount() = aircrafts.size
}