package com.example.airlinereservationsapp

import android.app.Application

class SkyBookApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RetrofitInstance.init(this)
    }
}