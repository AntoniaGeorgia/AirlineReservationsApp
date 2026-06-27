package com.example.airlinereservationsapp

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class CalendarAdapter(
    private val days: List<CalendarDay>,
    private var selectedStart: String?,
    private var selectedEnd: String?,
    private val onDayClick: (String) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.DayViewHolder>() {

    fun updateSelection(start: String?, end: String?) {
        selectedStart = start
        selectedEnd = end
        notifyDataSetChanged()
    }

    inner class DayViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDay: TextView = view.findViewById(R.id.tvDay)
        val tvPrice: TextView = view.findViewById(R.id.tvPrice)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar_day, parent, false)
        return DayViewHolder(view)
    }

    override fun getItemCount() = days.size

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val day = days[position]
        val ctx = holder.itemView.context

        if (day.dayOfMonth == 0 || !day.isSelectable) {
            holder.tvDay.text = ""
            holder.tvPrice.text = ""
            holder.itemView.isClickable = false
            holder.itemView.setBackgroundColor(ContextCompat.getColor(ctx, android.R.color.transparent))
            return
        }

        holder.tvDay.text = day.dayOfMonth.toString()
        holder.tvPrice.text = if (day.price != null) "${day.price.toInt()} €" else "-"

        val isStart = day.dateString == selectedStart
        val isEnd = day.dateString == selectedEnd
        val isInRange = selectedStart != null && selectedEnd != null &&
                day.dateString > selectedStart!! && day.dateString < selectedEnd!!

        when {
            isStart || isEnd -> {
                holder.itemView.setBackgroundResource(R.drawable.bg_day_selected)
                holder.tvDay.setTextColor(ContextCompat.getColor(ctx, android.R.color.white))
                holder.tvPrice.setTextColor(ContextCompat.getColor(ctx, android.R.color.white))
                holder.tvDay.setTypeface(null, Typeface.BOLD)
            }
            isInRange -> {
                holder.itemView.setBackgroundResource(R.drawable.bg_day_range)
                holder.tvDay.setTextColor(ContextCompat.getColor(ctx, R.color.teal_700))
                holder.tvPrice.setTextColor(ContextCompat.getColor(ctx, R.color.teal_700))
                holder.tvDay.setTypeface(null, Typeface.NORMAL)
            }
            day.price == null -> {
                holder.itemView.setBackgroundColor(ContextCompat.getColor(ctx, android.R.color.transparent))
                holder.tvDay.setTextColor(ContextCompat.getColor(ctx, android.R.color.darker_gray))
                holder.tvPrice.setTextColor(ContextCompat.getColor(ctx, android.R.color.darker_gray))
                holder.tvDay.setTypeface(null, Typeface.NORMAL)
            }
            else -> {
                holder.itemView.setBackgroundColor(ContextCompat.getColor(ctx, android.R.color.transparent))
                holder.tvDay.setTextColor(ContextCompat.getColor(ctx, android.R.color.black))
                holder.tvPrice.setTextColor(ContextCompat.getColor(ctx, R.color.teal_700))
                holder.tvDay.setTypeface(null, Typeface.NORMAL)
            }
        }

        holder.itemView.isClickable = true
        holder.itemView.setOnClickListener {
            if (day.isSelectable) onDayClick(day.dateString)
        }
    }
}