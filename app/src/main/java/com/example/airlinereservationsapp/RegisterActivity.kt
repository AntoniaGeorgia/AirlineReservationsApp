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

class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val etFullName = findViewById<EditText>(R.id.etFullName)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val tvLoginLink = findViewById<TextView>(R.id.tvLoginLink)

        btnRegister.setOnClickListener {
            val name = etFullName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (name.isNotEmpty() && email.isNotEmpty() && password.isNotEmpty()) {
                val userRequest = UserRequest(name, email, password)

                lifecycleScope.launch {
                    try {
                        val response = RetrofitInstance.api.registerUser(userRequest)
                        if (response.isSuccessful) {
                            Toast.makeText(this@RegisterActivity, "Cont creat cu succes!", Toast.LENGTH_LONG).show()
                            finish() // Ne întoarcem la Login
                        } else {
                            Toast.makeText(this@RegisterActivity, "Eroare: Email deja folosit", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@RegisterActivity, "Eroare server: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                Toast.makeText(this, "Completează toate câmpurile!", Toast.LENGTH_SHORT).show()
            }
        }

        tvLoginLink.setOnClickListener {
            finish()
        }
    }
}
