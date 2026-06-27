package com.example.airlinereservationsapp

import android.app.AlertDialog
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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class CityDiscoveryActivity : AppCompatActivity() {

    private var cityIata = ""
    private var cityName = ""
    private var userId = -1
    private var selectedCategory: String? = null
    private var currentPhotoPostId = -1
    private var selectedCategoryIndex = 0

    private val categories = listOf(
        "Toate", "📸 Locuri de poze", "🏛️ Atracții", "🍽️ Mâncare",
        "🎉 Distracții pentru tineri", "⚠️ Zone nesigure", "🎓 Student friendly",
        "💰 Budget travel", "🛍️ Shopping", "🌿 Nature", "🏨 Cazare"
    )

    companion object { const val REQUEST_PHOTO = 2001 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_city_discovery)

        cityIata = intent.getStringExtra("CITY_IATA") ?: ""
        cityName = intent.getStringExtra("CITY_NAME") ?: cityIata
        userId = getSharedPreferences("prefs", MODE_PRIVATE).getInt("user_id", -1)

        findViewById<ImageButton>(R.id.btnBackCity).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvCityName).text = "✈ $cityName"

        setupCategoryTabs()
        loadPosts()
        checkTravelerProfile()

        findViewById<Button>(R.id.btnAddPost).setOnClickListener { showAddPostDialog() }
        findViewById<Button>(R.id.btnFindTravelers).setOnClickListener {
            startActivity(Intent(this, CompatibleTravelersActivity::class.java).apply {
                putExtra("CITY_IATA", cityIata)
                putExtra("CITY_NAME", cityName)
            })
        }
    }

    private fun setupCategoryTabs() {
        val container = findViewById<LinearLayout>(R.id.layoutCategories)
        container.removeAllViews()

        categories.forEachIndexed { index, cat ->
            val tv = TextView(this).apply {
                text = cat
                textSize = 14f
                setPadding(32, 0, 32, 0)
                setTypeface(null, if (index == selectedCategoryIndex) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (index == selectedCategoryIndex) Color.parseColor("#00897B") else Color.parseColor("#555555"))

                // Linie dedesubt pentru cel selectat
                if (index == selectedCategoryIndex) {
                    setBackgroundResource(android.R.drawable.bottom_bar)
                    background = null
                    // Simulăm linia cu padding și border
                    val border = android.graphics.drawable.GradientDrawable().apply {
                        setColor(Color.WHITE)
                        setStroke(0, Color.TRANSPARENT)
                    }
                    background = border
                    // Adaugă indicator verde
                    val indicator = View(this@CityDiscoveryActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 3
                        )
                        setBackgroundColor(Color.parseColor("#00897B"))
                    }
                }

                setOnClickListener {
                    selectedCategoryIndex = index
                    selectedCategory = if (index == 0) null else cat
                    setupCategoryTabs()
                    loadPosts()
                }
            }

            val wrapper = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }
            wrapper.addView(tv, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                0, 1f
            ))

            // Indicator linie
            val indicator = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 3
                )
                setBackgroundColor(
                    if (index == selectedCategoryIndex) Color.parseColor("#00897B")
                    else Color.TRANSPARENT
                )
            }
            wrapper.addView(indicator)
            container.addView(wrapper)
        }
    }

    private fun loadPosts() {
        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.getCityPosts(cityIata, selectedCategory)
                if (response.isSuccessful) {
                    val posts = response.body() ?: emptyList()
                    val rv = findViewById<RecyclerView>(R.id.rvPosts)
                    val tvNo = findViewById<TextView>(R.id.tvNoPosts)

                    if (posts.isEmpty()) {
                        rv.visibility = View.GONE
                        tvNo.visibility = View.VISIBLE
                    } else {
                        rv.visibility = View.VISIBLE
                        tvNo.visibility = View.GONE
                        rv.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
                        rv.adapter = PinterestPostsAdapter(
                            posts = posts,
                            userId = userId,
                            onPostClick = { post -> showPostDetail(post) },
                            onAddPhoto = { postId -> openPhotoPicker(postId) }
                        )
                    }
                }
            } catch (_: Exception) {
                Toast.makeText(this@CityDiscoveryActivity, "Eroare la încărcare", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showPostDetail(post: TravelPostDTO) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_post_detail, null)

        view.findViewById<TextView>(R.id.tvDetailCategory).text = post.category
        view.findViewById<TextView>(R.id.tvDetailContent).text = post.content
        view.findViewById<TextView>(R.id.tvDetailAuthor).text =
            "${post.userFullName} • ${post.createdAt.take(10)}"

        val tvRating = view.findViewById<TextView>(R.id.tvDetailRating)
        tvRating.text = if (post.ratingCount > 0)
            "⭐ %.1f (%d voturi)".format(post.averageRating, post.ratingCount)
        else "Fără rating încă"

        // Rating bar doar dacă nu e postul tău
        val ratingBar = view.findViewById<RatingBar>(R.id.ratingBarDetail)
        if (post.userId == userId) {
            ratingBar.visibility = View.GONE
        } else {
            ratingBar.visibility = View.VISIBLE
            ratingBar.setOnRatingBarChangeListener { _, rating, fromUser ->
                if (fromUser) ratePost(post.id, rating.toInt())
            }
        }

        AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("Închide", null)
            .show()
    }

    private fun showAddPostDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_post, null)
        val postCategories = categories.drop(1) // fără "Toate"
        val spinner = view.findViewById<Spinner>(R.id.spinnerPostCategory)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, postCategories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        AlertDialog.Builder(this)
            .setTitle("Adaugă o recomandare pentru $cityName")
            .setView(view)
            .setPositiveButton("Postează") { _, _ ->
                val content = view.findViewById<EditText>(R.id.etPostContent).text.toString().trim()
                val category = postCategories[spinner.selectedItemPosition]
                if (content.isNotEmpty()) submitPost(category, content)
                else Toast.makeText(this, "Scrie ceva!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Anulează", null)
            .show()
    }

    private fun submitPost(category: String, content: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.createPost(
                    cityIata,
                    CreatePostRequest(userId, cityIata, category, content)
                )
                if (response.isSuccessful) {
                    Toast.makeText(this@CityDiscoveryActivity, "Postat!", Toast.LENGTH_SHORT).show()
                    loadPosts()
                }
            } catch (_: Exception) {
                Toast.makeText(this@CityDiscoveryActivity, "Eroare", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun ratePost(postId: Int, rating: Int) {
        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.ratePost(postId, RatePostRequest(postId, userId, rating))
                if (response.isSuccessful) {
                    Toast.makeText(this@CityDiscoveryActivity, "Rating adăugat!", Toast.LENGTH_SHORT).show()
                    loadPosts()
                } else if (response.code() == 409) {
                    Toast.makeText(this@CityDiscoveryActivity, "Ai votat deja!", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) { }
        }
    }

    private fun openPhotoPicker(postId: Int) {
        currentPhotoPostId = postId
        val intent = Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_PHOTO)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PHOTO && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            uploadPhotoToPost(currentPhotoPostId, uri)
        }
    }

    private fun uploadPhotoToPost(postId: Int, uri: android.net.Uri) {
        lifecycleScope.launch {
            try {
                val stream = contentResolver.openInputStream(uri) ?: return@launch
                val bytes = stream.readBytes()
                stream.close()
                val requestBody = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("photo", "photo.jpg", requestBody)
                val response = RetrofitInstance.api.uploadPostPhoto(postId, part)
                if (response.isSuccessful) {
                    Toast.makeText(this@CityDiscoveryActivity, "Poză adăugată!", Toast.LENGTH_SHORT).show()
                    loadPosts()
                }
            } catch (_: Exception) {
                Toast.makeText(this@CityDiscoveryActivity, "Eroare upload", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkTravelerProfile() {
        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.getTravelerProfile(userId)
                if (!response.isSuccessful || response.body() == null) {
                    showSoloTravelDialog()
                } else {
                    val profile = response.body()!!
                    if (profile.travelStyle == "SOLO" && !profile.openToGroup) {
                        val banner = findViewById<LinearLayout>(R.id.bannerSolo)
                        banner.visibility = View.VISIBLE
                        findViewById<Button>(R.id.btnFindCompany).setOnClickListener {
                            startActivity(Intent(this@CityDiscoveryActivity, TravelerProfileActivity::class.java))
                        }
                    }
                }
            } catch (_: Exception) { }
        }
    }

    private fun showSoloTravelDialog() {
        AlertDialog.Builder(this)
            .setTitle("Călătorești singur?")
            .setMessage("Vrei să găsești companie pentru $cityName?")
            .setPositiveButton("Da, caut companie!") { _, _ ->
                startActivity(Intent(this, TravelerProfileActivity::class.java))
            }
            .setNegativeButton("Nu, mulțumesc", null)
            .show()
    }
}

class PinterestPostsAdapter(
    private val posts: List<TravelPostDTO>,
    private val userId: Int,
    private val onPostClick: (TravelPostDTO) -> Unit,
    private val onAddPhoto: (Int) -> Unit
) : RecyclerView.Adapter<PinterestPostsAdapter.VH>() {

    class VH(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_post_pinterest, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val post = posts[position]

        val tvCategory = holder.view.findViewById<TextView>(R.id.tvPinCategory)
        val tvContent = holder.view.findViewById<TextView>(R.id.tvPinContent)
        val tvAuthor = holder.view.findViewById<TextView>(R.id.tvPinAuthor)
        val tvRating = holder.view.findViewById<TextView>(R.id.tvPinRating)
        val ivPhoto = holder.view.findViewById<ImageView>(R.id.ivPinPhoto)
        val btnAddPhoto = holder.view.findViewById<Button>(R.id.btnPinAddPhoto)

        tvCategory.text = post.category
        tvContent.text = post.content
        tvAuthor.text = post.userFullName
        tvRating.text = if (post.ratingCount > 0)
            "⭐ %.1f".format(post.averageRating) else ""

        // Arată prima poză dacă există
        val urls = post.photoUrls
        if (!urls.isNullOrEmpty()) {
            ivPhoto.visibility = View.VISIBLE
            Glide.with(holder.view.context)
                .load(urls.first())
                .centerCrop()
                .into(ivPhoto)
        } else {
            ivPhoto.visibility = View.GONE
        }

        // Buton adaugă poză doar la postul tău
        btnAddPhoto.visibility = if (post.userId == userId) View.VISIBLE else View.GONE
        btnAddPhoto.setOnClickListener { onAddPhoto(post.id) }

        holder.view.setOnClickListener { onPostClick(post) }
    }

    override fun getItemCount() = posts.size
}