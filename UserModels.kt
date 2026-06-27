package com.example.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object Users : Table("users") {
    val id = integer("id").autoIncrement()
    val fullName = varchar("full_name", 100)
    val email = varchar("email", 100).uniqueIndex()
    val passwordHash = text("password_hash")  // numele real din DB
    val role = varchar("role", 20).default("PASSENGER")
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    override val primaryKey = PrimaryKey(id)
}

@Serializable
data class User(
    val id: Int? = null,
    val fullName: String? = null,
    val email: String,
    val password: String,
    val role: String? = "PASSENGER"
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)