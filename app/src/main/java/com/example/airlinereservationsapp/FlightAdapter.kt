package com.example.airlinereservationsapp

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.widget.Button

class FlightAdapter(
    private val itineraries: List<FlightItinerary>,
    private val onFlightClick: (FlightItinerary) -> Unit
) : RecyclerView.Adapter<FlightAdapter.FlightViewHolder>() {

    class FlightViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvFlightPrice: TextView = view.findViewById(R.id.tvFlightPrice)
        val tvFlightType: TextView = view.findViewById(R.id.tvFlightType)
        val tvFlightNumber: TextView = view.findViewById(R.id.tvFlightNumber)
        val tvDepartureTime: TextView = view.findViewById(R.id.tvDepartureTime)
        val tvArrivalTime: TextView = view.findViewById(R.id.tvArrivalTime)
        val tvDuration: TextView = view.findViewById(R.id.tvDuration)
        val tvOrigin: TextView = view.findViewById(R.id.tvOrigin)
        val tvDestination: TextView = view.findViewById(R.id.tvDestination)
        val btnBook: Button = view.findViewById(R.id.btnBook)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FlightViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_flight, parent, false)
        return FlightViewHolder(view)
    }

    override fun onBindViewHolder(holder: FlightViewHolder, position: Int) {
        val itinerary = itineraries[position]
        val firstFlight = itinerary.flights.first()
        val lastFlight = itinerary.flights.last()

        holder.tvOrigin.text = firstFlight.origin
        holder.tvDestination.text = lastFlight.destination
        holder.tvDepartureTime.text = firstFlight.departureTime
        holder.tvArrivalTime.text = lastFlight.arrivalTime
        holder.tvDuration.text = itinerary.duration ?: ""
        holder.tvFlightNumber.text = firstFlight.flightNumber
        holder.tvFlightPrice.text = "${itinerary.totalPrice} EUR"

        if (itinerary.isDirect) {
            holder.tvFlightType.text = "Direct"
            holder.tvFlightType.setTextColor(Color.GREEN)
        } else {
            holder.tvFlightType.text = "1 Escală (${itinerary.layoverAirport})"
            holder.tvFlightType.setTextColor(Color.parseColor("#FFA500"))
        }

        holder.btnBook.setOnClickListener { onFlightClick(itinerary)  }
    }

    override fun getItemCount() = itineraries.size
}