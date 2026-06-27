package com.example.airlinereservationsapp

data class CalendarDay(
    val dayOfMonth: Int,
    val dateString: String,
    val price: Double?,
    val isSelectable: Boolean,
    val isToday: Boolean = false
)