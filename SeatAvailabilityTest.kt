package com.example

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.BeforeTest

class SeatAvailabilityTest {

    private lateinit var seats: MutableList<SeatTest>

    @BeforeTest
    fun setup() {
        seats = mutableListOf(
            SeatTest(1,  "1A",  false, "BUSINESS"),
            SeatTest(2,  "1B",  false, "BUSINESS"),
            SeatTest(3,  "1C",  true,  "BUSINESS"),
            SeatTest(4,  "9A",  false, "ECONOMY"),
            SeatTest(5,  "9B",  true,  "ECONOMY"),
            SeatTest(6,  "9C",  false, "ECONOMY"),
            SeatTest(7,  "9D",  false, "ECONOMY"),
            SeatTest(8,  "9E",  true,  "ECONOMY"),
            SeatTest(9,  "9F",  false, "ECONOMY"),
            SeatTest(10, "10A", false, "ECONOMY")
        )
    }

    @Test
    fun numaraCorectLocurileLbere() {
        assertEquals(7, seats.count { !it.isOccupied })
    }

    @Test
    fun numaraCorectLocurileOcupate() {
        assertEquals(3, seats.count { it.isOccupied })
    }

    @Test
    fun filtreazaCorectBusiness() {
        assertEquals(3, seats.filter { it.seatClass == "BUSINESS" }.size)
    }

    @Test
    fun filtreazaCorectEconomyLibere() {
        val freeEconomy = seats.filter { it.seatClass == "ECONOMY" && !it.isOccupied }
        assertEquals(5, freeEconomy.size)
    }

    @Test
    fun rezervareaUnuiLocLiberFunctioneaza() {
        assertTrue(reserveSeat(seats, 4))
        assertTrue(seats.find { it.id == 4 }!!.isOccupied)
    }

    @Test
    fun rezervareaUnuiLocOcupatEsueaza() {
        assertFalse(reserveSeat(seats, 3))
    }

    @Test
    fun rezervareaUnuiLocInexistentEsueaza() {
        assertFalse(reserveSeat(seats, 999))
    }

    @Test
    fun doubleBookingPrevenit() {
        assertTrue(reserveSeat(seats, 6))
        assertFalse(reserveSeat(seats, 6))
    }

    @Test
    fun calculProcentOcupareCorect() {
        val percent = calculateOccupancy(
            totalSeats = seats.size,
            occupiedSeats = seats.count { it.isOccupied }
        )
        assertEquals(30.0, percent, 0.1)
    }

    @Test
    fun avionulPlinReturneaza100Procente() {
        assertEquals(100.0, calculateOccupancy(180, 180), 0.1)
    }
}

data class SeatTest(
    val id: Int,
    val seatNumber: String,
    var isOccupied: Boolean,
    val seatClass: String
)

fun reserveSeat(seats: MutableList<SeatTest>, seatId: Int): Boolean {
    val seat = seats.find { it.id == seatId } ?: return false
    if (seat.isOccupied) return false
    seat.isOccupied = true
    return true
}

fun calculateOccupancy(totalSeats: Int, occupiedSeats: Int): Double {
    if (totalSeats == 0) return 0.0
    return (occupiedSeats.toDouble() / totalSeats.toDouble()) * 100.0
}