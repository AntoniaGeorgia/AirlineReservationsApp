package com.example.airlinereservationsapp

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object CheckInScheduler {
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<CheckInWorker>(1, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "checkin_check",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}