package com.example.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import kotlinx.serialization.SerialName
import org.jetbrains.exposed.sql.javatime.datetime

import com.example.models.Users

object Aircrafts : Table("aircrafts") {
    val id = integer("id").autoIncrement()
    val model = varchar("model", 50)
    val totalSeats = integer("total_seats").default(0)
    val layoutRows = integer("layout_rows")
    val layoutCols = integer("layout_cols")
    override val primaryKey = PrimaryKey(id)
}

object Gates : Table("gates") {
    val id = integer("id").autoIncrement()
    val gateNumber = varchar("gate_number", 10)
    val terminal = varchar("terminal", 5)
    override val primaryKey = PrimaryKey(id)
}


object Flights : Table("flights") {
    val id = integer("id").autoIncrement()
    val aircraftId = integer("aircraft_id") references Aircrafts.id
    val gateId = (integer("gate_id") references Gates.id).nullable()
    val flightNumber = varchar("flight_number", 10)
    val origin = varchar("origin", 50)
    val destination = varchar("destination", 50)
    val departureTime = datetime("departure_time")
    val arrivalTime = datetime("arrival_time")
    val basePrice = decimal("base_price", 10, 2)
    val currentPrice = decimal("current_price", 10, 2)
    val status = varchar("status", 20).default("SCHEDULED")
    val delayMinutes = integer("delay_minutes").default(0)
    val overbookingPercent = integer("overbooking_percent").default(0)
    override val primaryKey = PrimaryKey(id)
}

@Serializable
data class Flight(
    val id: Int,
    val flightNumber: String,
    val origin: String,
    val destination: String,
    val departureTime: String,
    val arrivalTime: String,
    val currentPrice: Double
)
@Serializable
data class ReserveSeatRequest(
    val userId: Int,
    val seatNumber: String,
    val flightId: Int
)

object SeatsTable : Table("seats") {
    val id = integer("id").autoIncrement()
    val flightId = integer("flight_id") references Flights.id
    val seatNumber = varchar("seat_number", 10)
    val isOccupied = bool("is_occupied")
    val userId = (integer("user_id") references Users.id).nullable()
    val seatClass = varchar("seat_class", 20).default("ECONOMY")
    val priceMultiplier = decimal("price_multiplier", 3, 2).default(1.0.toBigDecimal())

    override val primaryKey = PrimaryKey(id)
}

object BookingsTable : Table("bookings") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id") references Users.id
    val flightId = integer("flight_id") references Flights.id
    val secondFlightId = integer("second_flight_id").nullable()
    val seatId = (integer("seat_id") references SeatsTable.id).nullable()
    val seatLabel = varchar("seat_label", 10).nullable()
    val status = varchar("status", 20).default("PENDING")
    val finalPrice = decimal("final_price", 10, 2).default(0.toBigDecimal())
    val firstNamePassenger = varchar("first_name_passenger", 100).nullable()
    val lastNamePassenger = varchar("last_name_passenger", 100).nullable()
    val boardingToken = varchar("boarding_token", 50).nullable()
    val createdAt = varchar("created_at", 50).nullable()
    val updatedAt = varchar("updated_at", 50).nullable()
    val voucherCode = varchar("voucher_code", 20).nullable()
    val voucherAmount = decimal("voucher_amount", 10, 2).nullable()
    val baggageOption = varchar("baggage_option", 20).default("HAND_ONLY")
    val baggagePrice = decimal("baggage_price", 10, 2).default(0.toBigDecimal())
    val seatClass = varchar("seat_class", 20).default("ECONOMY")
    val checkInStatus = varchar("check_in_status", 20).default("NOT_CHECKED_IN")
    val checkInTime = datetime("check_in_time").nullable()
    val linkedBookingId = integer("linked_booking_id").nullable()

    override val primaryKey = PrimaryKey(id)
}
@Serializable
data class BookingRequest(
    val userId: Int,
    val flightId: Int,
    val secondFlightId: Int? = null,
    val seatId: Int,
    val secondSeatId: Int? = null,
    val seatLabel: String? = null,
    val firstSeatId: Int? = null,
    val firstSeatLabel: String? = null,
    val finalPrice: Double? = null,
    val seatClass: String? = null,
    val baggageOption: String? = null,
    val secondBaggageOption: String? = null
)

@kotlinx.serialization.Serializable
data class BookingResponse(
    val bookingId: Int,
    val status: String,
    val seatLabel: String? = null,
    val boardingToken: String? = null,
    val message: String,
    val origin: String? = null,
    val destination: String? = null,
    val flightNumber: String? = null,
    val departureTime: String? = null,
    val finalPrice: Double = 0.0,
    val seatClass: String? = null,
    val baggageOption: String? = null,
    val secondBaggageOption: String? = null,
    val firstNamePassenger: String? = null,
    val lastNamePassenger: String? = null,
    val flightId: Int? = null,
    val qrCode: String? = null,
    val flightStatus: String? = null,
    val delayMinutes: Int? = null
)
@Serializable
data class PassengerDetailsRequest(
    val bookingId: Int,
    val documentType: String,
    val documentNumber: String,
    val gender: String,
    val nationality: String,
    val firstName: String,
    val lastName: String,
)
@Serializable
data class SeatResponse(
    val id: Int,
    val seatNumber: String,
    val isOccupied: Boolean,
    val seatClass: String,
    val currentPrice: Double
)


@Serializable
data class FlightItinerary(
    val itineraryId: String,
    val flights: List<Flight>,
    val totalPrice: Double,
    val isDirect: Boolean,
    val layoverAirport: String? = null
)
enum class BookingStatus {
    PENDING, PAID, CHECKED_IN, BOARDED, CANCELLED
}
@Serializable
data class LoginResponse(
    val token: String,
    val userId: Int?,
    val fullName: String?,
    val role: String?
)
@Serializable
data class AircraftDTO(
    val id: Int,
    val model: String,
    val totalSeats: Int,
    val layoutRows: Int,
    val layoutCols: Int
)

@Serializable
data class CreateAircraftRequest(
    val model: String,
    val layoutRows: Int,
    val layoutCols: Int
)

@Serializable
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

@Serializable
data class CreateFlightRequest(
    val flightNumber: String,
    val origin: String,
    val destination: String,
    val departureTime: String,
    val arrivalTime: String,
    val basePrice: Double,
    val aircraftId: Int
)

@Serializable
data class GateDTO(
    val id: Int,
    val gateNumber: String,
    val terminal: String
)

@Serializable
data class UpdateFlightStatusRequest(
    val status: String,
    val delayMinutes: Int = 0
)
@Serializable
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

@Serializable
data class RebookRequest(
    val newFlightId: Int
)

@Serializable
data class RebookResponse(
    val bookingId: Int,
    val newFlightId: Int,
    val newFlightNumber: String,
    val newSeatLabel: String,
    val newDepartureTime: String,
    val status: String,
    val message: String
)

@Serializable
data class ErrorResponse(val message: String)

@Serializable
data class CheapDestinationDTO(
    val destination: String,
    val originCity: String,
    val price: Double,
    val flightId: Int,
    val departureDate: String
)

object TransactionsTable : Table("transactions") {
    val id = integer("id").autoIncrement()
    val bookingId = integer("booking_id").nullable()
    val amount = decimal("amount", 10, 2)
    val paymentMethod = varchar("payment_method", 50).nullable()
    val transactionDate = datetime("transaction_date").nullable()
    override val primaryKey = PrimaryKey(id)
}

object AdminLogsTable : Table("admin_logs") {
    val id = integer("id").autoIncrement()
    val adminId = integer("admin_id").nullable()
    val bookingId = integer("booking_id").nullable()
    val actionType = varchar("action_type", 50).nullable()
    val oldValue = text("old_value").nullable()
    val newValue = text("new_value").nullable()
    val timestamp = datetime("timestamp").nullable()
    override val primaryKey = PrimaryKey(id)
}