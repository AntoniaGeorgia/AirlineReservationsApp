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

class InboxActivity : AppCompatActivity() {

    private var userId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        android.util.Log.d("DEBUG_INBOX", "onCreate userId=$userId")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inbox)

        userId = getSharedPreferences("prefs", MODE_PRIVATE).getInt("user_id", -1)
        findViewById<ImageButton>(R.id.btnBackInbox).setOnClickListener { finish() }
        loadInbox()
    }

    private fun loadInbox() {
        lifecycleScope.launch {
            try {
                android.util.Log.d("DEBUG_INBOX", "userId=$userId")
                val response = RetrofitInstance.api.getInbox(userId)
                android.util.Log.d("DEBUG_INBOX", "code=${response.code()} body=${response.body()} error=${response.errorBody()?.string()}")
                if (response.isSuccessful) {
                    val items = response.body() ?: emptyList()
                    android.util.Log.d("DEBUG_INBOX", "raw=$items size=${items.size}")

                    val rv = findViewById<RecyclerView>(R.id.rvInbox)
                    rv.layoutManager = LinearLayoutManager(this@InboxActivity)
                    rv.adapter = InboxAdapter(items) { item ->
                        startActivity(
                            Intent(this@InboxActivity, PrivateChatActivity::class.java).apply {
                                putExtra("OTHER_USER_ID", item.userId)
                                putExtra("OTHER_USER_NAME", item.userName)
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DEBUG_INBOX", "Exception: ${e.message}", e)
                Toast.makeText(this@InboxActivity, "Eroare: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

class InboxAdapter(
    private val items: List<InboxItem>,
    private val onClick: (InboxItem) -> Unit
) : RecyclerView.Adapter<InboxAdapter.VH>() {

    class VH(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_inbox, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {

        val item = items[position]

        holder.view.findViewById<TextView>(R.id.tvInboxName).text = item.userName
        holder.view.findViewById<TextView>(R.id.tvInboxLastMessage).text = item.lastMessage
        holder.view.findViewById<TextView>(R.id.tvInboxTime).text = item.createdAt.take(10)

        val tvUnread = holder.view.findViewById<TextView>(R.id.tvUnreadDot)
        tvUnread.visibility = if (!item.isRead) View.VISIBLE else View.GONE

        val ivAvatar = holder.view.findViewById<ImageView>(R.id.ivInboxAvatar)
        if (!item.profilePhotoUrl.isNullOrEmpty()) {
            Glide.with(holder.view.context)
                .load(item.profilePhotoUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .circleCrop()
                .into(ivAvatar)
        } else {
            ivAvatar.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        holder.view.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}