package com.example.airlinereservationsapp

import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

class ExploreDestinationAdapter(
    private val items: List<CheapDestinationDTO>,
    private val onClick: (CheapDestinationDTO) -> Unit
) : RecyclerView.Adapter<ExploreDestinationAdapter.VH>() {

    class VH(val view: View) : RecyclerView.ViewHolder(view)

    private fun cityName(code: String): String = when (code.uppercase()) {
        "LHR" -> "London"
        "CDG" -> "Paris"
        "VIE" -> "Vienna"
        "FRA" -> "Frankfurt"
        "BCN" -> "Barcelona"
        "FCO" -> "Rome"
        "AMS" -> "Amsterdam"
        "MAD" -> "Madrid"
        "MUC" -> "Munich"
        "ZRH" -> "Zurich"
        "BRU" -> "Brussels"
        "LIS" -> "Lisbon"
        "ATH" -> "Athens"
        "DUB" -> "Dublin"
        "CPH" -> "Copenhagen"
        "IST" -> "Istanbul"
        "DXB" -> "Dubai"
        "JFK" -> "New York"
        "SIN" -> "Singapore"
        "SYD" -> "Sydney"
        "PRG" -> "Prague"
        "BUD" -> "Budapest"
        "OSL" -> "Oslo"
        "ARN" -> "Stockholm"
        "TFS" -> "Tenerife"
        "PMI" -> "Mallorca"
        "JMK" -> "Mykonos"
        "KEF" -> "Reykjavik"
        "NRT" -> "Tokyo"
        "BKK" -> "Bangkok"
        "DPS" -> "Bali"
        "CAI" -> "Cairo"
        "RAK" -> "Marrakech"
        "CUN" -> "Cancun"
        else  -> code
    }

    private fun getCityImageUrl(code: String): String {
        val name = cityName(code)
        return "https://api.unsplash.com/photos/random?query=${name}+city+travel&orientation=landscape&client_id=aiL8wJqUmtGi2PR3b1oYfTc2FuzTJMaPuhY-pW2j4g8"
    }

    private fun loadUnsplashImage(context: android.content.Context, apiUrl: String, imageView: ImageView) {
        Thread {
            try {
                val url = java.net.URL(apiUrl)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connect()
                val json = conn.inputStream.bufferedReader().readText()
                val regex = """"regular"\s*:\s*"([^"]+)"""".toRegex()
                val imageUrl = regex.find(json)?.groupValues?.get(1)?.replace("\\u0026", "&")
                if (imageUrl != null) {
                    (context as? android.app.Activity)?.runOnUiThread {
                        Glide.with(context)
                            .load(imageUrl)
                            .transition(DrawableTransitionOptions.withCrossFade(400))
                            .placeholder(android.R.color.darker_gray)
                            .into(imageView)
                    }
                }
            } catch (_: Exception) { }
        }.start()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_explore_destination, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.view.findViewById<TextView>(R.id.tvExploreCity).text = cityName(item.destination)
        holder.view.findViewById<TextView>(R.id.tvExplorePrice).text = "de la ${item.price.toInt()}€"

        val iv = holder.view.findViewById<ImageView>(R.id.ivExploreImage)
        loadUnsplashImage(holder.view.context, getCityImageUrl(item.destination), iv)

        holder.view.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}