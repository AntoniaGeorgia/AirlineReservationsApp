package com.example.airlinereservationsapp

import kotlinx.serialization.Serializable

data class AdminUserResponse(
    val id: Int,
    val fullName: String,
    val email: String,
    val role: String,
    val createdAt: String
)

data class ModificationRequestResponse(
    val id: Int,
    val userId: Int,
    val userFullName: String,
    val userEmail: String,
    val bookingId: Int,
    val origin: String,
    val destination: String,
    val requestType: String,
    val description: String,
    val status: String,
    val adminNote: String?,
    val createdAt: String
)

data class ModificationRequestBody(
    val userId: Int,
    val bookingId: Int,
    val requestType: String,  // "DESTINATION", "TIME", "CANCEL", "OTHER"
    val description: String
)

data class ResolveRequestBody(
    val approved: Boolean,
    val adminNote: String? = null
)

data class AircraftDTO(
    val id: Int,
    val model: String,
    val totalSeats: Int,
    val layoutRows: Int,
    val layoutCols: Int
)

data class CreateAircraftRequest(
    val model: String,
    val layoutRows: Int,
    val layoutCols: Int
)

data class FlightDTO(
    val id: Int,
    val flightNumber: String,
    val origin: String,
    val destination: String,
    val departureTime: String,
    val arrivalTime: String,
    val basePrice: Double,
    val aircraftId: Int,
    val aircraftModel: String
)

data class CreateFlightRequest(
    val flightNumber: String,
    val origin: String,
    val destination: String,
    val departureTime: String,
    val arrivalTime: String,
    val basePrice: Double,
    val aircraftId: Int
)

data class GateDTO(
    val id: Int,
    val gateNumber: String,
    val terminal: String
)

data class BlockUserRequest(
    val userId: Int,
    val blockedUserId: Int
)
data class AlternativeFlightDTO(
    val flightId: Int,
    val flightNumber: String,
    val origin: String,
    val destination: String,
    val departureTime: String,
    val currentPrice: Double,
    val priceDifference: Double,
    val freeSeats: Int,
    val isFree: Boolean
)

data class RebookRequest(val newFlightId: Int)

data class RebookResponse(
    val bookingId: Int,
    val newFlightId: Int,
    val newFlightNumber: String,
    val newSeatLabel: String,
    val newDepartureTime: String,
    val status: String,
    val message: String
)