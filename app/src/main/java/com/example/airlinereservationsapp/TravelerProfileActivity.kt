package com.example.airlinereservationsapp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

class TravelerProfileActivity : AppCompatActivity() {

    private var userId = -1
    private var selectedPhotoUri: Uri? = null
    private lateinit var ivProfilePhoto: ImageView
    private val PICK_IMAGE_REQUEST = 1001
    private val PERMISSION_REQUEST = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_traveler_profile)

        userId = getSharedPreferences("prefs", MODE_PRIVATE).getInt("user_id", -1)
        ivProfilePhoto = findViewById(R.id.ivProfilePhoto)

        findViewById<ImageButton>(R.id.btnBackProfile).setOnClickListener { finish() }

        findViewById<Button>(R.id.btnPickPhoto).setOnClickListener {
            if (checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                openGallery()
            } else {
                requestPermissions(
                    arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES),
                    PERMISSION_REQUEST
                )
            }
        }

        loadProfile()

        findViewById<Button>(R.id.btnSaveProfile).setOnClickListener {
            saveProfile()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            openGallery()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            selectedPhotoUri = data?.data
            ivProfilePhoto.setImageURI(selectedPhotoUri)
            android.util.Log.d("DEBUG_PHOTO", "Photo selected: $selectedPhotoUri")
        }
    }

    private fun loadProfile() {
        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.getTravelerProfile(userId)
                android.util.Log.d("DEBUG_PROFILE", "Load: ${response.code()} body=${response.body()}")
                if (response.isSuccessful) {
                    val profile = response.body() ?: return@launch
                    bindProfile(profile)
                    val photoUrl = profile.profilePhotoUrl
                    if (!photoUrl.isNullOrEmpty()) {
                        Glide.with(this@TravelerProfileActivity)
                            .load(photoUrl)
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .into(ivProfilePhoto)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DEBUG_PROFILE", "Load error: ${e.message}", e)
        }}
    }
    private fun bindProfile(profile: TravelerProfileDTO) {
        findViewById<EditText>(R.id.etPlannedCities).setText(
            profile.plannedCities?.joinToString(", ") ?: ""
        )
        profile.age?.let { findViewById<EditText>(R.id.etAge).setText(it.toString()) }
        findViewById<EditText>(R.id.etLanguages).setText(
            profile.languages?.joinToString(", ") ?: ""
        )
        when (profile.travelStyle) {
            "SOLO"   -> findViewById<RadioButton>(R.id.rbSolo).isChecked = true
            "GROUP"  -> findViewById<RadioButton>(R.id.rbGroup).isChecked = true
            "COUPLE" -> findViewById<RadioButton>(R.id.rbCouple).isChecked = true
        }
        profile.interests.forEach { interest ->
            when (interest) {
                "FOOD"        -> findViewById<CheckBox>(R.id.cbFood).isChecked = true
                "NIGHTLIFE"   -> findViewById<CheckBox>(R.id.cbNightlife).isChecked = true
                "SIGHTSEEING" -> findViewById<CheckBox>(R.id.cbSightseeing).isChecked = true
                "NATURE"      -> findViewById<CheckBox>(R.id.cbNature).isChecked = true
                "SHOPPING"    -> findViewById<CheckBox>(R.id.cbShopping).isChecked = true
                "CULTURE"     -> findViewById<CheckBox>(R.id.cbCulture).isChecked = true
            }
        }
        findViewById<EditText>(R.id.etBio).setText(profile.bio ?: "")
        findViewById<CheckBox>(R.id.cbOpenToGroup).isChecked = profile.openToGroup
    }

    private fun saveProfile() {
        val travelStyle = when (findViewById<RadioGroup>(R.id.rgTravelStyle).checkedRadioButtonId) {
            R.id.rbSolo   -> "SOLO"
            R.id.rbGroup  -> "GROUP"
            R.id.rbCouple -> "COUPLE"
            else -> "SOLO"
        }
        val openToGroup = findViewById<CheckBox>(R.id.cbOpenToGroup).isChecked
        val interests = mutableListOf<String>()
        if (findViewById<CheckBox>(R.id.cbFood).isChecked)        interests.add("FOOD")
        if (findViewById<CheckBox>(R.id.cbNightlife).isChecked)   interests.add("NIGHTLIFE")
        if (findViewById<CheckBox>(R.id.cbSightseeing).isChecked) interests.add("SIGHTSEEING")
        if (findViewById<CheckBox>(R.id.cbNature).isChecked)      interests.add("NATURE")
        if (findViewById<CheckBox>(R.id.cbShopping).isChecked)    interests.add("SHOPPING")
        if (findViewById<CheckBox>(R.id.cbCulture).isChecked)     interests.add("CULTURE")
        val bio = findViewById<EditText>(R.id.etBio).text.toString().trim()
        val age = findViewById<EditText>(R.id.etAge).text.toString().toIntOrNull()
        val languages = findViewById<EditText>(R.id.etLanguages).text.toString()
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val startDate: String? = null
        val endDate: String? = null
        val plannedCities = findViewById<EditText>(R.id.etPlannedCities).text.toString()
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }

        if (interests.isEmpty()) {
            Toast.makeText(this, "Selectează cel puțin un interes!", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                selectedPhotoUri?.let { uri ->
                    val file = uriToFile(uri)
                    android.util.Log.d("DEBUG_PHOTO", "Uploading: ${file.absolutePath} size=${file.length()}")
                    val requestBody = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    val part = MultipartBody.Part.createFormData("photo", file.name, requestBody)
                    val uploadResponse = RetrofitInstance.api.uploadProfilePhoto(userId, part)
                    android.util.Log.d("DEBUG_PHOTO", "Upload: ${uploadResponse.code()} body=${uploadResponse.body()}")
                }

                val response = RetrofitInstance.api.updateTravelerProfile(
                    UpdateProfileRequest(
                        userId = userId,
                        travelStyle = travelStyle,
                        interests = interests,
                        bio = bio.ifEmpty { null },
                        openToGroup = openToGroup,
                        age = age,
                        languages = languages,
                        travelStartDate = startDate,
                        travelEndDate = endDate,
                        plannedCities = plannedCities,
                    )
                )
                if (response.isSuccessful) {
                    Toast.makeText(this@TravelerProfileActivity, "Profil salvat!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    val error = response.errorBody()?.string()
                    android.util.Log.e("DEBUG_PHOTO", "Save failed: ${response.code()} $error")
                    Toast.makeText(this@TravelerProfileActivity, "Eroare salvare: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("DEBUG_PHOTO", "Error: ${e.message}", e)
                Toast.makeText(this@TravelerProfileActivity, "Eroare: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun uriToFile(uri: Uri): File {
        val file = File(cacheDir, "profile_photo_${userId}.jpg")
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        return file
    }
}