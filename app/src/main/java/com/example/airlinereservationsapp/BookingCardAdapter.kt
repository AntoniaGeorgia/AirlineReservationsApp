package com.example.airlinereservationsapp

import android.graphics.Color
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

class BookingCardAdapter(
    private val bookings: List<BookingResponse>,
    private val onClick: (BookingResponse) -> Unit
) : RecyclerView.Adapter<BookingCardAdapter.VH>() {

    class VH(val view: View) : RecyclerView.ViewHolder(view)

    private fun getCityImageUrl(iataCode: String): String {
        val seed = when (iataCode.uppercase()) {
            "LHR" -> "london"
            "CDG" -> "paris"
            "VIE" -> "vienna"
            "FRA" -> "frankfurt"
            "BCN" -> "barcelona"
            "FCO" -> "rome"
            "AMS" -> "amsterdam"
            "MAD" -> "madrid"
            "MUC" -> "munich"
            "ZRH" -> "zurich"
            "BRU" -> "brussels"
            "LIS" -> "lisbon"
            "ATH" -> "athens"
            "DUB" -> "dublin"
            "CPH" -> "copenhagen"
            "IST" -> "istanbul"
            "DXB" -> "dubai"
            "JFK" -> "newyork"
            "SIN" -> "singapore"
            "SYD" -> "sydney"
            "OTP" -> "bucharest"
            "PRG" -> "prague"
            "BUD" -> "budapest"
            "OSL" -> "oslo"
            "ARN" -> "stockholm"
            "HEL" -> "helsinki"
            "WAW" -> "warsaw"
            "TFS" -> "tenerife"
            "PMI" -> "mallorca"
            "JMK" -> "mykonos"
            "KEF" -> "iceland"
            "RAK" -> "marrakech"
            "CAI" -> "cairo"
            "DPS" -> "bali"
            "BKK" -> "bangkok"
            "NRT" -> "tokyo"
            "CUN" -> "cancun"
            else  -> iataCode.lowercase()
        }
        return "https://picsum.photos/seed/$seed/800/400"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_booking_card, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val b = bookings[position]

        holder.view.findViewById<TextView>(R.id.tvCardRoute).text =
            "${b.origin ?: "?"} ✈ ${b.destination ?: "?"}"

        val dateDisplay = b.departureTime?.take(16)?.replace("T", "  ") ?: ""
        holder.view.findViewById<TextView>(R.id.tvCardDate).text = dateDisplay

        holder.view.findViewById<TextView>(R.id.tvCardSeat).text =
            "✦ Loc ${b.seatLabel ?: "-"}"

        holder.view.findViewById<TextView>(R.id.tvCardPrice).text =
            "%.2f €".format(b.finalPrice)

        val tvStatus = holder.view.findViewById<TextView>(R.id.tvCardStatus)
        val statusName = b.status?.name ?: "PENDING"
        val statusText = when (statusName) {
            "PAID"                -> "✓ Platit"
            "CHECKED_IN"          -> "✓ Check-in"
            "IMBARCAT", "BOARDED" -> "✈ Imbarcat"
            "ANULAT", "CANCELLED" -> "✗ Anulat"
            "PENDING"             -> "Pending"
            "REFUND_PENDING"      -> "⚠ Zbor anulat — apasă pt. alternative"

            else                  -> statusName
        }
        val statusColor = when (statusName) {
            "PAID"                -> "#4CAF50"
            "CHECKED_IN"          -> "#0E9AAB"
            "IMBARCAT", "BOARDED" -> "#9C27B0"
            "ANULAT", "CANCELLED" -> "#F44336"
            "REFUND_PENDING"      -> "#D32F2F"
            else                  -> "#FF9800"
        }
        tvStatus.text = statusText
        tvStatus.setBackgroundColor(Color.parseColor(statusColor))

        // Picsum — simplu, fara API key, fara JSON parsing, garantat functional
        Glide.with(holder.view.context)
            .load(getCityImageUrl(b.destination ?: ""))
            .transition(DrawableTransitionOptions.withCrossFade(400))
            .placeholder(android.R.color.darker_gray)
            .error(android.R.color.holo_blue_dark)
            .into(holder.view.findViewById(R.id.ivCityImage))

        holder.view.setOnClickListener { onClick(b) }
    }

    override fun getItemCount() = bookings.size
}