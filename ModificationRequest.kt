package com.example.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object ModificationRequests : Table("modification_requests") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id") references Users.id
    val bookingId = integer("booking_id") references BookingsTable.id
    val requestType = varchar("request_type", 30)
    val description = text("description")
    val status = varchar("status", 20).default("PENDING")
    val adminNote = text("admin_note").nullable()
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val resolvedAt = datetime("resolved_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

@Serializable
data class ModificationRequestDTO(
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
    val adminNote: String? = null,
    val createdAt: String
)

@Serializable
data class CreateModificationRequest(
    val userId: Int,
    val bookingId: Int,
    val requestType: String,
    val description: String
)

@Serializable
data class ResolveRequest(
    val approved: Boolean,
    val adminNote: String? = null
)

@Serializable
data class AdminUserDTO(
    val id: Int,
    val fullName: String,
    val email: String,
    val role: String,
    val createdAt: String
)