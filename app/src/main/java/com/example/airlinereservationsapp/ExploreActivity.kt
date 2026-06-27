package com.example.airlinereservationsapp

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch

class ExploreActivity : AppCompatActivity() {

    private var selectedIata: String? = null
    private var selectedIndex = 0

    private val cities = listOf(
        "🌍 Toate" to null,
        "OTP" to "București",
        // Europa
        "LHR" to "Londra Heathrow", "LGW" to "Londra Gatwick",
        "CDG" to "Paris", "VIE" to "Viena", "FRA" to "Frankfurt",
        "MAD" to "Madrid", "BCN" to "Barcelona", "FCO" to "Roma",
        "AMS" to "Amsterdam", "MUC" to "München", "ZRH" to "Zürich",
        "BRU" to "Bruxelles", "CPH" to "Copenhaga", "OSL" to "Oslo",
        "ARN" to "Stockholm", "HEL" to "Helsinki", "WAW" to "Varșovia",
        "PRG" to "Praga", "BUD" to "Budapesta", "ATH" to "Atena",
        "IST" to "Istanbul", "LIS" to "Lisabona", "DUB" to "Dublin",
        "MAN" to "Manchester",
        // Asia
        "DXB" to "Dubai", "DOH" to "Doha", "AUH" to "Abu Dhabi",
        "BKK" to "Bangkok", "SIN" to "Singapore", "KUL" to "Kuala Lumpur",
        "NRT" to "Tokyo", "ICN" to "Seoul", "PEK" to "Beijing",
        "BOM" to "Mumbai", "DEL" to "Delhi", "CMB" to "Colombo",
        // America
        "JFK" to "New York JFK", "EWR" to "New York Newark",
        "YYZ" to "Toronto", "ORD" to "Chicago", "MIA" to "Miami",
        "LAX" to "Los Angeles", "GRU" to "São Paulo", "EZE" to "Buenos Aires",
        "BOG" to "Bogotá", "LIM" to "Lima",
        // Africa
        "CAI" to "Cairo", "CMN" to "Casablanca", "JNB" to "Johannesburg",
        "NBO" to "Nairobi", "LOS" to "Lagos", "ACC" to "Accra",
        "TUN" to "Tunis", "ALG" to "Alger",
        // Australia
        "SYD" to "Sydney", "MEL" to "Melbourne", "BNE" to "Brisbane",
        "AKL" to "Auckland"

    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_explore)

        findViewById<ImageButton>(R.id.btnBackExplore).setOnClickListener { finish() }

        setupCityTabs()
        loadPosts()
    }

    private fun setupCityTabs() {
        val container = findViewById<LinearLayout>(R.id.layoutCityTabs)
        container.removeAllViews()

        cities.forEachIndexed { index, (name, iata) ->
            val wrapper = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }

            val tv = TextView(this).apply {
                text = name
                textSize = 14f
                setPadding(32, 0, 32, 0)
                setTypeface(null, if (index == selectedIndex) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (index == selectedIndex) Color.parseColor("#00897B") else Color.parseColor("#555555"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, 0, 1f
                )
                gravity = android.view.Gravity.CENTER
                setOnClickListener {
                    selectedIndex = index
                    selectedIata = iata
                    setupCityTabs()
                    loadPosts()
                }
            }

            val indicator = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 3
                )
                setBackgroundColor(
                    if (index == selectedIndex) Color.parseColor("#00897B")
                    else Color.TRANSPARENT
                )
            }

            wrapper.addView(tv)
            wrapper.addView(indicator)
            container.addView(wrapper)
        }
    }

    private fun loadPosts() {
        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.explorePosts(iata = selectedIata)
                if (response.isSuccessful) {
                    val posts = response.body() ?: emptyList()
                    val rv = findViewById<RecyclerView>(R.id.rvExplorePosts)
                    val tvNo = findViewById<TextView>(R.id.tvNoExplorePosts)

                    if (posts.isEmpty()) {
                        rv.visibility = View.GONE
                        tvNo.visibility = View.VISIBLE
                    } else {
                        rv.visibility = View.VISIBLE
                        tvNo.visibility = View.GONE
                        rv.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
                        rv.adapter = ExploreAdapter(posts) { post ->
                            startActivity(Intent(this@ExploreActivity, CityDiscoveryActivity::class.java).apply {
                                putExtra("CITY_IATA", post.cityIata)
                                putExtra("CITY_NAME", post.cityIata)
                            })
                        }
                    }
                }
            } catch (_: Exception) {
                Toast.makeText(this@ExploreActivity, "Eroare la încărcare", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

class ExploreAdapter(
    private val posts: List<TravelPostDTO>,
    private val onPostClick: (TravelPostDTO) -> Unit
) : RecyclerView.Adapter<ExploreAdapter.VH>() {

    class VH(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_explore_post, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val post = posts[position]

        holder.view.findViewById<TextView>(R.id.tvExploreCity).text = post.cityIata
        holder.view.findViewById<TextView>(R.id.tvExploreCategory).text = post.category
        holder.view.findViewById<TextView>(R.id.tvExploreContent).text = post.content
        holder.view.findViewById<TextView>(R.id.tvExploreRating).text =
            if (post.ratingCount > 0) "⭐ %.1f".format(post.averageRating) else ""

        val ivPhoto = holder.view.findViewById<ImageView>(R.id.ivExplorePhoto)
        if (!post.photoUrls.isNullOrEmpty()) {
            ivPhoto.visibility = View.VISIBLE
            Glide.with(holder.view.context).load(post.photoUrls.first()).centerCrop().into(ivPhoto)
        } else {
            ivPhoto.visibility = View.GONE
        }

        holder.view.setOnClickListener { onPostClick(post) }
    }

    override fun getItemCount() = posts.size
}