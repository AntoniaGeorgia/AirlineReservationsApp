package com.example.airlinereservationsapp

import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

class WeekendDestinationAdapter(
    private val items: List<Pair<WeekendDestination, WeatherResponse>>,
    private val onClick: (WeekendDestination) -> Unit
) : RecyclerView.Adapter<WeekendDestinationAdapter.VH>() {

    class VH(val view: View) : RecyclerView.ViewHolder(view)

    private fun getCityImageUrl(code: String): String {
        val cityName = when (code.uppercase()) {
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
        return "https://api.unsplash.com/photos/random?query=${cityName}+city+travel&orientation=landscape&client_id=aiL8wJqUmtGi2PR3b1oYfTc2FuzTJMaPuhY-pW2j4g8"
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
            .inflate(R.layout.item_weekend_destination, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (dest, weather) = items[position]
        val temp = weather.main.temp.toInt()
        val desc = weather.weather.firstOrNull()?.description
            ?.replaceFirstChar { it.uppercase() } ?: ""

        holder.view.findViewById<TextView>(R.id.tvDestEmoji).text = dest.emoji
        holder.view.findViewById<TextView>(R.id.tvDestName).text = "${dest.city}, ${dest.country}"
        holder.view.findViewById<TextView>(R.id.tvDestWeather).text = "$desc  ${temp}\u00B0C"
        holder.view.findViewById<TextView>(R.id.tvDestCode).text = dest.code

        val ivDest = holder.view.findViewById<ImageView>(R.id.ivDestImage)
        loadUnsplashImage(holder.view.context, getCityImageUrl(dest.code), ivDest)

        holder.view.setOnClickListener { onClick(dest) }
    }

    override fun getItemCount() = items.size
}