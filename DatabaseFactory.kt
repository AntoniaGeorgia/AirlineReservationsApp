package com.example.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

object DatabaseFactory {
    fun init() {
        val config = HikariConfig().apply {
            driverClassName = "org.postgresql.Driver"
            jdbcUrl = "jdbc:postgresql://localhost:5432/airlineReservations"
            username = "antoniasava"
            password = "parola"
            maximumPoolSize = 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_SERIALIZABLE"
            validate()
        }
        Database.connect(HikariDataSource(config))
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction { block() }


}