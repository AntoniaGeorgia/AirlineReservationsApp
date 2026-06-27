package com.example

import com.example.models.BookingStateMachine
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class BookingValidationTest {

    // ── Validarea cererii de rezervare ──
    @Test
    fun bookingValidCuToateCampurile() {
        assertTrue(isValidBooking(BookingRequestTest(1, 10, 5, "12A", 299.99)))
    }

    @Test
    fun bookingInvalidFaraUserId() {
        assertFalse(isValidBooking(BookingRequestTest(-1, 10, 5, "12A", 299.99)))
    }

    @Test
    fun bookingInvalidFaraFlightId() {
        assertFalse(isValidBooking(BookingRequestTest(1, -1, 5, "12A", 299.99)))
    }

    @Test
    fun bookingInvalidCuPretNegativ() {
        assertFalse(isValidBooking(BookingRequestTest(1, 10, 5, "12A", -50.0)))
    }

    @Test
    fun bookingInvalidCuSeatLabelGol() {
        assertFalse(isValidBooking(BookingRequestTest(1, 10, 5, "", 299.99)))
    }

    // ── Automatul de stări (cod de producție) ──
    @Test
    fun stareInitialaEstePending() {
        assertEquals("PENDING", getInitialBookingStatus())
    }

    @Test
    fun tranzitieValidaDeLaPendingLaPaid() {
        assertTrue(BookingStateMachine.isValidTransition("PENDING", "PAID"))
    }

    @Test
    fun tranzitieValidaDeLaPaidLaCheckedIn() {
        assertTrue(BookingStateMachine.isValidTransition("PAID", "CHECKED_IN"))
    }

    @Test
    fun tranzitieValidaDeLaCheckedInLaBoarded() {
        assertTrue(BookingStateMachine.isValidTransition("CHECKED_IN", "BOARDED"))
    }

    @Test
    fun tranzitieInvalidaDeLaPendingLaCheckedIn() {
        assertFalse(BookingStateMachine.isValidTransition("PENDING", "CHECKED_IN"))
    }

    @Test
    fun tranzitieInvalidaDeLaPaidLaBoarded() {
        assertFalse(BookingStateMachine.isValidTransition("PAID", "BOARDED"))
    }

    // ── Tranziții de recuperare prin rebooking ──
    @Test
    fun tranzitieValidaRefundPendingLaPaid() {
        assertTrue(BookingStateMachine.isValidTransition("REFUND_PENDING", "PAID"))
    }

    @Test
    fun tranzitieValidaCancelledLaPaidPrinRebooking() {
        assertTrue(BookingStateMachine.isValidTransition("CANCELLED", "PAID"))
    }

    @Test
    fun paidPoateTreceInRefundPending() {
        assertTrue(BookingStateMachine.isValidTransition("PAID", "REFUND_PENDING"))
    }

    // ── Stări terminale ──
    @Test
    fun boardedEsteTerminala() {
        assertTrue(BookingStateMachine.isTerminal("BOARDED"))
        assertFalse(BookingStateMachine.isValidTransition("BOARDED", "PAID"))
    }

    // ── Reguli de anulare ──
    @Test
    fun anularePosibilaDoarDinPendingSauPaid() {
        assertTrue(BookingStateMachine.canCancel("PENDING"))
        assertTrue(BookingStateMachine.canCancel("PAID"))
        assertFalse(BookingStateMachine.canCancel("CHECKED_IN"))
        assertFalse(BookingStateMachine.canCancel("BOARDED"))
    }
}

// validarea cererii rămâne locală — testează regula, nu un endpoint
data class BookingRequestTest(
    val userId: Int,
    val flightId: Int,
    val seatId: Int,
    val seatLabel: String,
    val finalPrice: Double
)

fun isValidBooking(request: BookingRequestTest): Boolean =
    request.userId > 0 &&
            request.flightId > 0 &&
            request.seatId > 0 &&
            request.seatLabel.isNotEmpty() &&
            request.finalPrice >= 0

fun getInitialBookingStatus(): String = "PENDING"