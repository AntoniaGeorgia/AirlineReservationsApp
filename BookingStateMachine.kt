package com.example.models

object BookingStateMachine {

    private val validTransitions = mapOf(
        "PENDING"        to setOf("PAID", "CANCELLED"),
        "PAID"           to setOf("CHECKED_IN", "REFUND_PENDING", "CANCELLED"),
        "CHECKED_IN"     to setOf("BOARDED", "REFUND_PENDING"),
        "REFUND_PENDING" to setOf("PAID"),
        "CANCELLED"      to setOf("PAID"),
        "BOARDED"        to emptySet()
    )

    fun isValidTransition(from: String, to: String): Boolean =
        validTransitions[from]?.contains(to) == true

    fun canCancel(status: String): Boolean =
        status == "PENDING" || status == "PAID"

    fun isTerminal(status: String): Boolean =
        validTransitions[status]?.isEmpty() == true
}