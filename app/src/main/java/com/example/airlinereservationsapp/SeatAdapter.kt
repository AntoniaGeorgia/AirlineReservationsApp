package com.example.airlinereservationsapp

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

sealed class SeatCell {
    data class SeatItem(val seat: Seat) : SeatCell()
    object Aisle : SeatCell()
    data class Header(val label: String) : SeatCell()
    data class ZoneLabel(val text: String, val color: Int) : SeatCell()
}

class SeatAdapter(
    private var cells: List<SeatCell>,
    private val onSeatSelected: (Seat) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_SEAT = 0
        const val TYPE_AISLE = 1
        const val TYPE_HEADER = 2
        const val TYPE_ZONE = 3
    }

    private var selectedSeatId: Int = -1

    fun updateCells(newCells: List<SeatCell>) {
        cells = newCells
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selectedSeatId = -1
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) = when (cells[position]) {
        is SeatCell.SeatItem -> TYPE_SEAT
        is SeatCell.Aisle -> TYPE_AISLE
        is SeatCell.Header -> TYPE_HEADER
        is SeatCell.ZoneLabel -> TYPE_ZONE
    }

    // ViewHolders
    inner class SeatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view.findViewById(R.id.seatCard)
        val tvNumber: TextView = view.findViewById(R.id.tvSeatNumber)
    }

    inner class AisleViewHolder(view: View) : RecyclerView.ViewHolder(view)

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvLabel: TextView = view.findViewById(R.id.tvRowLabel)
    }

    inner class ZoneViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvZone: TextView = view.findViewById(R.id.tvZoneLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SEAT -> SeatViewHolder(inflater.inflate(R.layout.item_seat, parent, false))
            TYPE_AISLE -> AisleViewHolder(inflater.inflate(R.layout.item_aisle, parent, false))
            TYPE_HEADER -> HeaderViewHolder(inflater.inflate(R.layout.item_row_header, parent, false))
            else -> ZoneViewHolder(inflater.inflate(R.layout.item_zone_label, parent, false))
        }
    }

    override fun getItemCount() = cells.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val cell = cells[position]) {
            is SeatCell.SeatItem -> {
                val h = holder as SeatViewHolder
                val seat = cell.seat
                h.tvNumber.text = seat.seatNumber

                val isSelected = seat.id == selectedSeatId

                when {
                    seat.isOccupied -> {
                        h.card.setCardBackgroundColor(Color.parseColor("#BDBDBD"))
                        h.tvNumber.setTextColor(Color.WHITE)
                        h.itemView.isEnabled = false
                    }
                    isSelected -> {
                        h.card.setCardBackgroundColor(Color.parseColor("#FF9800"))
                        h.tvNumber.setTextColor(Color.WHITE)
                        h.itemView.isEnabled = true
                    }
                    seat.seatClass == "BUSINESS" -> {
                        h.card.setCardBackgroundColor(Color.parseColor("#FFD700"))
                        h.tvNumber.setTextColor(Color.BLACK)
                        h.itemView.isEnabled = true
                    }
                    seat.seatClass == "PREMIUM_ECONOMY" -> {
                        h.card.setCardBackgroundColor(Color.parseColor("#90CAF9"))
                        h.tvNumber.setTextColor(Color.BLACK)
                        h.itemView.isEnabled = true
                    }
                    else -> {
                        h.card.setCardBackgroundColor(Color.WHITE)
                        h.tvNumber.setTextColor(Color.BLACK)
                        h.itemView.isEnabled = true
                    }
                }

                h.itemView.setOnClickListener {
                    if (!seat.isOccupied) {
                        selectedSeatId = seat.id
                        notifyDataSetChanged()
                        onSeatSelected(seat)
                    }
                }
            }
            is SeatCell.Header -> {
                (holder as HeaderViewHolder).tvLabel.text = cell.label
            }
            is SeatCell.ZoneLabel -> {
                val h = holder as ZoneViewHolder
                h.tvZone.text = cell.text
                h.tvZone.setBackgroundColor(cell.color)
            }
            is SeatCell.Aisle -> {  }
        }
    }
}