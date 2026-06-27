package com.example.airlinereservationsapp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import com.bumptech.glide.Glide

class CompatibleTravelersActivity : AppCompatActivity() {

    private var cityIata = ""
    private var cityName = ""
    private var userId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compatible_travelers)

        cityIata = intent.getStringExtra("CITY_IATA") ?: ""
        cityName = intent.getStringExtra("CITY_NAME") ?: cityIata
        userId = getSharedPreferences("prefs", MODE_PRIVATE).getInt("user_id", -1)

        findViewById<ImageButton>(R.id.btnBackTravelers).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvTravelersTitle).text = "Călători spre $cityName"

        loadTravelers()
    }

    private fun loadTravelers() {
        lifecycleScope.launch {
            try {
                val pbLoading = findViewById<ProgressBar>(R.id.pbTravelersLoading)
                pbLoading.visibility = View.VISIBLE

                val response = RetrofitInstance.api.getCompatibleTravelers(cityIata, userId)
                pbLoading.visibility = View.GONE

                if (response.isSuccessful) {
                    val travelers = response.body() ?: emptyList()
                    val rv = findViewById<RecyclerView>(R.id.rvTravelers)
                    rv.layoutManager = LinearLayoutManager(this@CompatibleTravelersActivity)

                    if (travelers.isEmpty()) {
                        rv.visibility = View.GONE
                        findViewById<TextView>(R.id.tvNoTravelers).visibility = View.VISIBLE
                    } else {
                        rv.visibility = View.VISIBLE
                        findViewById<TextView>(R.id.tvNoTravelers).visibility = View.GONE
                        rv.adapter = TravelersAdapter(travelers) { traveler ->
                            startActivity(
                                Intent(this@CompatibleTravelersActivity, PrivateChatActivity::class.java).apply {
                                    putExtra("OTHER_USER_ID", traveler.userId)
                                    putExtra("OTHER_USER_NAME", traveler.userFullName)
                                }
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                Toast.makeText(this@CompatibleTravelersActivity, "Eroare la încărcare", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

class TravelersAdapter(
    private val travelers: List<TravelerProfileDTO>,
    private val onMessage: (TravelerProfileDTO) -> Unit
) : RecyclerView.Adapter<TravelersAdapter.VH>() {

    class VH(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_traveler, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val t = travelers[position]

        holder.view.findViewById<TextView>(R.id.tvTravelerName).text = t.userFullName

        val styleEmoji = when (t.travelStyle) {
            "SOLO"   -> "✈ Solo"
            "GROUP"  -> "👥 Grup"
            "COUPLE" -> "💑 Cuplu"
            else     -> t.travelStyle
        }
        holder.view.findViewById<TextView>(R.id.tvTravelerStyle).text = styleEmoji

        val interestsText = t.interests.joinToString(" • ") { interest ->
            when (interest) {
                "FOOD"        -> "🍕 Mâncare"
                "NIGHTLIFE"   -> "🎉 Nightlife"
                "SIGHTSEEING" -> "🏛 Turism"
                "NATURE"      -> "🌿 Natură"
                "SHOPPING"    -> "🛍 Shopping"
                "CULTURE"     -> "🎭 Cultură"
                else          -> interest
            }
        }
        holder.view.findViewById<TextView>(R.id.tvTravelerInterests).text = interestsText

        val score = t.compatibilityScore
        val tvScore = holder.view.findViewById<TextView>(R.id.tvCompatibilityScore)
        tvScore.text = "★ $score interese comune"
        tvScore.setTextColor(
            when {
                score >= 3 -> android.graphics.Color.parseColor("#4CAF50")
                score >= 1 -> android.graphics.Color.parseColor("#FF9800")
                else       -> android.graphics.Color.parseColor("#888888")
            }
        )

        if (!t.bio.isNullOrEmpty()) {
            holder.view.findViewById<TextView>(R.id.tvTravelerBio).text = t.bio
            holder.view.findViewById<TextView>(R.id.tvTravelerBio).visibility = View.VISIBLE
        } else {
            holder.view.findViewById<TextView>(R.id.tvTravelerBio).visibility = View.GONE
        }

        val ivPhoto = holder.view.findViewById<ImageView>(R.id.ivTravelerPhoto)
        if (!t.profilePhotoUrl.isNullOrEmpty()) {
            Glide.with(holder.view.context)
                .load(t.profilePhotoUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .circleCrop()
                .into(ivPhoto)
        } else {
            ivPhoto.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        holder.view.findViewById<Button>(R.id.btnMessageTraveler).setOnClickListener {
            onMessage(t)
        }

        val tvAge = holder.view.findViewById<TextView>(R.id.tvTravelerAge)
        if (t.age != null) {
            tvAge.text = "🎂 ${t.age} ani"
            tvAge.visibility = View.VISIBLE
        } else tvAge.visibility = View.GONE

        val tvLanguages = holder.view.findViewById<TextView>(R.id.tvTravelerLanguages)
        if (t.languages.isNotEmpty()) {
            tvLanguages.text = "🗣 ${t.languages.joinToString(", ")}"
            tvLanguages.visibility = View.VISIBLE
        } else tvLanguages.visibility = View.GONE

        val tvPeriod = holder.view.findViewById<TextView>(R.id.tvTravelerPeriod)
        if (t.travelStartDate != null && t.travelEndDate != null) {
            tvPeriod.text = "📅 ${t.travelStartDate} → ${t.travelEndDate}"
            tvPeriod.visibility = View.VISIBLE
        } else tvPeriod.visibility = View.GONE

        val tvCities = holder.view.findViewById<TextView>(R.id.tvTravelerCities)
        if (t.plannedCities.isNotEmpty()) {
            tvCities.text = "📍 ${t.plannedCities.joinToString(", ")}"
            tvCities.visibility = View.VISIBLE
        } else tvCities.visibility = View.GONE
    }

    override fun getItemCount() = travelers.size
}