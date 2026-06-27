package com.example.airlinereservationsapp

data class User(
    val id: Int? = null,
    val fullName: String = "",
    val email: String,
    val password: String,
    val role: String = "PASSENGER"
)