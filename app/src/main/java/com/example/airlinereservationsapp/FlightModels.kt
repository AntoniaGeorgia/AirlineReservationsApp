package com.example.airlinereservationsapp

import com.google.gson.annotations.SerializedName

data class FlightItinerary(
    @SerializedName("itineraryId") val itineraryId: String,
    @SerializedName("flights") val flights: List<Flight>,
    @SerializedName("totalPrice") val totalPrice: Double,
    @SerializedName("isDirect") val isDirect: Boolean,
    @SerializedName("layoverAirport") val layoverAirport: String? = null,
    @SerializedName("duration") val duration: String? = null
)

data class SeatResponse(
    val id: Int,
    val seatNumber: String,
    val isOccupied: Boolean,
    val seatClass: String? = "ECONOMY",
    val currentPrice: Double? = 0.0
)

data class BookingRequest(
    @SerializedName("userId") val userId: Int,
    @SerializedName("flightId") val flightId: Int,
    @SerializedName("secondFlightId") val secondFlightId: Int? = null,
    @SerializedName("seatId") val seatId: Int,
    @SerializedName("seatLabel") val seatLabel: String? = null,
    @SerializedName("finalPrice") val finalPrice: Double,
    @SerializedName("firstSeatId") val firstSeatId: Int? = null,
    @SerializedName("firstSeatLabel") val firstSeatLabel: String? = null,
    @SerializedName("seatClass") val seatClass: String? = null,
    @SerializedName("baggageOption") val baggageOption: String? = null,
    @SerializedName("secondBaggageOption") val secondBaggageOption: String? = null
)

enum class BookingStatus {
    PENDING, PAID, CHECKED_IN, BOARDED, CANCELLED, REFUND_PENDING
}

data class BookingResponse(
    val bookingId: Int,
    val status: BookingStatus,
    val seatLabel: String? = null,
    val boardingToken: String? = null,
    val flightId: Int? = null,
    val message: String,
    val origin: String? = null,
    val destination: String? = null,
    val flightNumber: String? = null,
    val departureTime: String? = null,
    val finalPrice: Double = 0.0,
    val seatClass: String? = null,
    val baggageOption: String? = null,
    val firstNamePassenger: String? = null,
    val lastNamePassenger: String? = null,
    val qrCode: String? = null,
    val flightStatus: String? = null,
    val delayMinutes: Int? = null
)

data class PassengerDetailsRequest(
    @SerializedName("bookingId") val bookingId: Int,
    @SerializedName("firstName") val firstName: String,
    @SerializedName("lastName") val lastName: String,
    @SerializedName("documentType") val documentType: String,
    @SerializedName("documentNumber") val documentNumber: String,
    @SerializedName("gender") val gender: String,
    @SerializedName("nationality") val nationality: String
)

data class ReserveSeatRequest(
    @SerializedName("userId") val userId: Int,
    @SerializedName("seatNumber") val seatNumber: String,
    @SerializedName("flightId") val flightId: Int
)

data class SimpleResponse(
    val message: String
)

data class Seat(
    val id: Int,
    val seatNumber: String,
    val isOccupied: Boolean,
    val seatClass: String,
    var isSelected: Boolean = false
)

data class UserRequest(
    val fullName: String,
    val email: String,
    val password: String,
    val role: String = "PASSENGER"
)

data class AirportResponse(
    @SerializedName("code") val code: String,
    @SerializedName("name") val name: String,
    @SerializedName("latitude") val latitude: Float,
    @SerializedName("longitude") val longitude: Float,
    @SerializedName("country") val country: String
)

data class RatePostRequest(
    val postId: Int,
    val userId: Int,
    val rating: Int
)

data class LoginResponse(
    val token: String,
    val userId: Int?,
    val fullName: String?,
    val role: String?
)
data class CheapDestinationDTO(
    val destination: String,
    val originCity: String,
    val price: Double,
    val flightId: Int,
    val departureDate: String
)
