package com.example.airlinereservationsapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import android.app.PendingIntent
import android.content.Intent

class CheckInWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        android.util.Log.d("CHECKIN_WORKER", "Worker pornit!")

        val userId = applicationContext
            .getSharedPreferences("prefs", Context.MODE_PRIVATE)
            .getInt("user_id", -1)
        android.util.Log.d("CHECKIN_WORKER", "UserId: $userId")

        if (userId == -1) {
            android.util.Log.d("CHECKIN_WORKER", "userId e -1, opresc")
            return@withContext Result.success()
        }

        try {
            val response = RetrofitInstance.api.getUserBookings(userId)
            android.util.Log.d("CHECKIN_WORKER", "Response code: ${response.code()}")
            val bookings = response.body()
            android.util.Log.d("CHECKIN_WORKER", "Bookings count: ${bookings?.size}")

            val now = LocalDateTime.now()
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

            bookings?.forEach { booking ->
                android.util.Log.d("CHECKIN_WORKER", "Booking: ${booking.flightNumber} status=${booking.status} dep=${booking.departureTime}")

                if (booking.status != BookingStatus.PAID) {
                    android.util.Log.d("CHECKIN_WORKER", "Skip - status nu e PAID")
                    return@forEach
                }

                val depTime = try {
                    when {
                        booking.departureTime?.contains("T") == true -> {
                            val text = booking.departureTime!!
                            // Adaugă ":00" dacă lipsesc secundele
                            val normalized = if (text.length == 16) "$text:00" else text
                            LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
                        }
                        else -> LocalDateTime.parse(booking.departureTime ?: return@forEach,
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CHECKIN_WORKER", "Eroare parsare data: ${e.message}")
                    return@forEach
                }

                val hoursUntil = ChronoUnit.HOURS.between(now, depTime)
                android.util.Log.d("CHECKIN_WORKER", "Ore pana la zbor: $hoursUntil")

                when {
                    hoursUntil in 24..48 -> {
                        showNotification(
                            "✈ Check-in disponibil!",
                            "Zborul ${booking.flightNumber} spre ${booking.destination} pleacă în ${hoursUntil}h. Fă check-in acum!",
                            booking.bookingId
                        )
                    }
                    hoursUntil in 1..23 -> {
                        showNotification(
                            "⚠ Check-in urgent!",
                            "Zborul ${booking.flightNumber} spre ${booking.destination} pleacă în ${hoursUntil}h!",
                            booking.bookingId
                        )
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CHECKIN_WORKER", "Eroare: ${e.message}")
            return@withContext Result.retry()
        }

        Result.success()
    }

    private fun showNotification(title: String, message: String, bookingId: Int = -1) {
        val channelId = "checkin_channel"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Check-in Notificări",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificări pentru check-in zbor"
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, BookingDetailActivity::class.java).apply {
            putExtra("BOOKING_ID", bookingId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            bookingId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}