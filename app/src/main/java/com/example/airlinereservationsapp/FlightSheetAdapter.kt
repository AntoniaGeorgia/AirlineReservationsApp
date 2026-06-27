package com.example.airlinereservationsapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FlightSheetAdapter(
    private val flights: List<Flight>,
    private val onFlightClick: (Flight) -> Unit
) : RecyclerView.Adapter<FlightSheetAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvAirline: TextView = view.findViewById(R.id.tvSheetAirline)
        val tvPrice: TextView = view.findViewById(R.id.tvSheetPrice)
        val tvDep: TextView = view.findViewById(R.id.tvSheetDep)
        val tvArr: TextView = view.findViewById(R.id.tvSheetArr)
        val tvDuration: TextView = view.findViewById(R.id.tvSheetDuration)
        val tvRoute: TextView = view.findViewById(R.id.tvSheetClass)
        val tvFlight: TextView = view.findViewById(R.id.tvSheetSeats)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context)
            .inflate(R.layout.item_flight_sheet, parent, false)
    )

    override fun getItemCount() = flights.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val f = flights[position]
        holder.tvAirline.text = f.flightNumber
        holder.tvPrice.text = String.format("%.0f €", f.currentPrice)
        holder.tvDep.text = f.departureTime.take(5)
        holder.tvArr.text = f.arrivalTime.take(5)
        holder.tvDuration.text = calcDuration(f.departureTime, f.arrivalTime)
        holder.tvRoute.text = f.origin
        holder.tvFlight.text = f.destination
        holder.itemView.setOnClickListener { onFlightClick(f) }
    }

    private fun calcDuration(dep: String, arr: String): String {
        return try {
            val depParts = dep.take(5).split(":").map { it.toInt() }
            val arrParts = arr.take(5).split(":").map { it.toInt() }
            val depMin = depParts[0] * 60 + depParts[1]
            var arrMin = arrParts[0] * 60 + arrParts[1]
            if (arrMin < depMin) arrMin += 24 * 60
            val diff = arrMin - depMin
            "${diff / 60}h ${diff % 60}m"
        } catch (e: Exception) {
            ""
        }
    }
}