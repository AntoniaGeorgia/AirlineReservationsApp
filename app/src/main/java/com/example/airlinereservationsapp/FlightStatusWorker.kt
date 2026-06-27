package com.example.airlinereservationsapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Worker periodic care verifică statusul rezervărilor userului.
 * La schimbări (zbor anulat → REFUND_PENDING, sau zbor întârziat),
 * ridică o notificare cu deep link spre detaliile rezervării.
 *
 * Snapshot-ul stărilor anterioare e ținut în SharedPreferences ("prefs"),
 * cheia "status_snapshot", ca set de stringuri "bookingId|bookingStatus|flightStatus|delay".
 */
class FlightStatusWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        android.util.Log.d("FSW", "Worker pornit")
        val prefs = applicationContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val userId = prefs.getInt("user_id", -1)
        val snapPrefs = applicationContext.getSharedPreferences("flight_status_prefs", Context.MODE_PRIVATE)
        android.util.Log.d("FSW", "userId=$userId")
        if (userId == -1) return Result.success() // nelogat — n-avem ce verifica

        return try {
            val response = RetrofitInstance.api.getUserBookings(userId)
            android.util.Log.d("FSW", "response code=${response.code()}")
            if (!response.isSuccessful) return Result.retry()
            val bookings = response.body() ?: emptyList()

            val oldSnapshot = snapPrefs.getStringSet("snapshot_$userId", emptySet()) ?: emptySet()
            android.util.Log.d("FSW", "bookings=${bookings.size}, snapshot vechi=${oldSnapshot.size}")
            val oldMap = oldSnapshot.mapNotNull { entry ->
                val parts = entry.split("|")
                if (parts.size == 4) parts[0] to Triple(parts[1], parts[2], parts[3]) else null
            }.toMap()

            val newSnapshot = mutableSetOf<String>()

            for (b in bookings) {
                val id = b.bookingId
                val bookingStatus = b.status?.name ?: "UNKNOWN"
                val flightStatus = b.flightStatus ?: "SCHEDULED"
                val delay = (b.delayMinutes ?: 0).toString()

                newSnapshot.add("$id|$bookingStatus|$flightStatus|$delay")

                val old = oldMap[id.toString()] ?: continue // booking nou — fara notificare
                val (oldBookingStatus, oldFlightStatus, oldDelay) = old


                if (bookingStatus == "REFUND_PENDING" && oldBookingStatus != "REFUND_PENDING") {
                    notify(
                        notificationId = id,
                        title = "✈ Zbor anulat",
                        text = "Zborul ${b.flightNumber ?: ""} ${b.origin ?: ""} → ${b.destination ?: ""} a fost anulat. " +
                                "Apasă pentru a alege un zbor alternativ gratuit.",
                        bookingId = id
                    )
                }


                if (flightStatus == "DELAYED" && (oldFlightStatus != "DELAYED" || oldDelay != delay)) {
                    notify(
                        notificationId = id + 100_000,
                        title = "⏱ Zbor întârziat",
                        text = "Zborul ${b.flightNumber ?: ""} ${b.origin ?: ""} → ${b.destination ?: ""} " +
                                "are o întârziere de $delay minute. Plecare nouă: ${b.departureTime?.take(16)?.replace("T", " ") ?: "-"}.",
                        bookingId = id
                    )
                }
            }

            snapPrefs.edit().putStringSet("snapshot_$userId", newSnapshot).apply()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun notify(notificationId: Int, title: String, text: String, bookingId: Int) {
        val manager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Status zboruri",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Notificări pentru zboruri anulate sau întârziate" }
            )
        }

        val intent = Intent(applicationContext, BookingDetailActivity::class.java).apply {
            putExtra("BOOKING_ID", bookingId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            manager.notify(notificationId, notification)
        } catch (_: SecurityException) {
            // userul nu a acordat permisiunea POST_NOTIFICATIONS — ignoram
        }
    }

    companion object {
        private const val CHANNEL_ID = "flight_status"
    }
}