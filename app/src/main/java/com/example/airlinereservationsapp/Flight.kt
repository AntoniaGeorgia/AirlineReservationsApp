package com.example.airlinereservationsapp

data class Flight(
    val id: Int,
    val flightNumber: String,
    val origin: String,
    val destination: String,
    val departureTime: String,
    val arrivalTime: String,
    val currentPrice: Double
)
