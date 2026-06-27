package com.example.airlinereservationsapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        if (intent.getBooleanExtra("SESSION_EXPIRED", false)) {
            Toast.makeText(this, "Sesiunea a expirat. Te rugăm să te reconectezi.", Toast.LENGTH_LONG).show()
        }

        val etEmail = findViewById<EditText>(R.id.etEmailLogin)
        val etPassword = findViewById<EditText>(R.id.etPasswordLogin)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvGoToRegister = findViewById<TextView>(R.id.tvGoToRegister)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Câmpuri goale", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    val response = RetrofitInstance.api.loginUser(LoginRequest(email, password))
                    if (response.isSuccessful) {

                        val user = response.body()

                        getSharedPreferences("prefs", MODE_PRIVATE)
                            .edit()
                            .putInt("user_id", user?.userId ?: -1)
                            .putString("user_name", user?.fullName ?: "")
                            .putString("user_email", email)
                            .putString("user_role", user?.role ?: "PASSENGER")
                            .putString("jwt_token", user?.token ?: "")
                            .apply()
                        // pornește verificarea periodică a statusului zborurilor (min. 15 min)
                        val workRequest = androidx.work.PeriodicWorkRequestBuilder<FlightStatusWorker>(
                            15, java.util.concurrent.TimeUnit.MINUTES
                        ).build()
                        androidx.work.WorkManager.getInstance(this@LoginActivity).enqueueUniquePeriodicWork(
                            "flight_status_check",
                            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                            workRequest
                        )
                        // rulare imediată o dată — utilă la testare/demo
                        androidx.work.WorkManager.getInstance(this@LoginActivity)
                            .enqueue(androidx.work.OneTimeWorkRequestBuilder<FlightStatusWorker>().build())
                        android.util.Log.d("JWT", "Token: ${getSharedPreferences("prefs", MODE_PRIVATE).getString("jwt_token", "")}")

                        Toast.makeText(this@LoginActivity, "Bun venit, ${user?.fullName}!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@LoginActivity, FlightSearchActivity::class.java))
                        finish()
                    } else {
                        val errorBody = response.errorBody()?.string()
                        android.util.Log.e("LOGIN_ERROR", "Code: ${response.code()}, Body: $errorBody")
                        Toast.makeText(this@LoginActivity, "Email sau parolă greșită", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@LoginActivity, "Eroare server: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        tvGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}