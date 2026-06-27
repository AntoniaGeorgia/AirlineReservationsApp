package com.example.airlinereservationsapp

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.content.Context

class AdminFlightsAdapter(
    private val flights: List<FlightDTO>,
    private val onRefresh: () -> Unit
) : RecyclerView.Adapter<AdminFlightsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvFlightNumber: TextView = view.findViewById(R.id.tvFlightNumber)
        val tvRoute: TextView = view.findViewById(R.id.tvFlightRoute)
        val tvTime: TextView = view.findViewById(R.id.tvFlightTime)
        val tvAircraft: TextView = view.findViewById(R.id.tvFlightAircraft)
        val btnDelete: Button = view.findViewById(R.id.btnDeleteFlight)
        val btnAssignGate: Button = view.findViewById(R.id.btnAssignGate)
        val btnStatus: Button = view.findViewById(R.id.btnFlightStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_flight, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val flight = flights[position]
        holder.tvFlightNumber.text = flight.flightNumber
        holder.tvRoute.text = "${flight.origin} → ${flight.destination}"
        holder.tvTime.text = "${flight.departureTime.take(16)} → ${flight.arrivalTime.take(16)}"
        holder.tvAircraft.text = "✈️ ${flight.aircraftModel} | ${flight.basePrice}€"

        holder.btnDelete.setOnClickListener {
            AlertDialog.Builder(holder.itemView.context)
                .setTitle("Șterge zbor")
                .setMessage("Ești sigur că vrei să ștergi zborul ${flight.flightNumber}?")
                .setPositiveButton("Șterge") { _, _ ->
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            RetrofitInstance.api.deleteFlight(flight.id)
                            Toast.makeText(holder.itemView.context, "Zbor șters", Toast.LENGTH_SHORT).show()
                            onRefresh()
                        } catch (e: Exception) {
                            Toast.makeText(holder.itemView.context, "Eroare: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Anulează", null)
                .show()
        }

        holder.btnAssignGate.setOnClickListener {
            showAssignGateDialog(holder.itemView.context, flight)
        }

        holder.btnStatus.setOnClickListener {
            showStatusDialog(holder.itemView.context, flight)
        }
    }

    private fun showStatusDialog(context: Context, flight: FlightDTO) {
        val options = arrayOf("DELAYED", "CANCELLED", "SCHEDULED")
        var selectedStatus = "DELAYED"
        var delayMinutes = 60

        val dialogView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 30)
        }

        val spinner = Spinner(context)
        val spinnerAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, options)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = spinnerAdapter
        dialogView.addView(spinner)

        val etDelay = EditText(context).apply {
            hint = "Minute întârziere (doar pentru DELAYED)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("60")
        }
        dialogView.addView(etDelay)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                etDelay.visibility = if (options[pos] == "DELAYED") View.VISIBLE else View.GONE
                selectedStatus = options[pos]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        AlertDialog.Builder(context)
            .setTitle("Schimbă status — ${flight.flightNumber}")
            .setView(dialogView)
            .setPositiveButton("Confirmă") { _, _ ->
                delayMinutes = etDelay.text.toString().toIntOrNull() ?: 60
                android.util.Log.d("STATUS", "Trimit status=$selectedStatus delay=$delayMinutes pentru flightId=${flight.id}")
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val response = RetrofitInstance.api.updateFlightStatus(
                            flight.id, selectedStatus, delayMinutes
                        )
                        android.util.Log.d("STATUS", "Response: ${response.code()} ${response.errorBody()?.string()}")
                        if (response.isSuccessful) {
                            val msg = when (selectedStatus) {
                                "DELAYED" -> "Zbor întârziat cu $delayMinutes minute. Poartă reasignată dacă era conflict."
                                "CANCELLED" -> "Zbor anulat. Rezervările PAID → REFUND_PENDING."
                                else -> "Status actualizat."
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            onRefresh()
                        } else {
                            Toast.makeText(context, "Eroare: ${response.code()}", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("STATUS", "Exception: ${e.message}", e)
                        Toast.makeText(context, "Eroare: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Anulează", null)
            .show()
    }

    private fun showAssignGateDialog(context: Context, flight: FlightDTO) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val gatesResponse = RetrofitInstance.api.getGates(flight.id)
                val gates = gatesResponse.body() ?: emptyList()
                val gateLabels = gates.map { "Terminal ${it.terminal} - Poarta ${it.gateNumber}" }
                val spinner = Spinner(context)
                val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, gateLabels)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinner.adapter = adapter

                AlertDialog.Builder(context)
                    .setTitle("Asignează poartă — ${flight.flightNumber}")
                    .setView(spinner)
                    .setPositiveButton("Asignează") { _, _ ->
                        val selectedGate = gates[spinner.selectedItemPosition]
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                val response = RetrofitInstance.api.assignGate(flight.id, selectedGate.id)
                                if (response.isSuccessful) {
                                    Toast.makeText(context, "Poartă asignată!", Toast.LENGTH_SHORT).show()
                                    onRefresh()
                                } else {
                                    val error = response.errorBody()?.string()
                                    Toast.makeText(context, "Conflict: $error", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Eroare: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton("Anulează", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(context, "Eroare la încărcare porți", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun getItemCount() = flights.size
}