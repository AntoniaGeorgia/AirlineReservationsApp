package com.example.airlinereservationsapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PrivateChatActivity : AppCompatActivity() {

    private var userId = -1
    private var otherUserId = -1
    private var otherUserName = ""
    private lateinit var adapter: MessagesAdapter
    private val messages = mutableListOf<PrivateMessageDTO>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_private_chat)

        userId = getSharedPreferences("prefs", MODE_PRIVATE).getInt("user_id", -1)
        otherUserId = intent.getIntExtra("OTHER_USER_ID", -1)
        otherUserName = intent.getStringExtra("OTHER_USER_NAME") ?: "Utilizator"

        findViewById<ImageButton>(R.id.btnBackChat).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvChatTitle).text = otherUserName

        val rv = findViewById<RecyclerView>(R.id.rvMessages)
        adapter = MessagesAdapter(messages, userId)
        rv.layoutManager = LinearLayoutManager(this).also { it.stackFromEnd = true }
        rv.adapter = adapter

        findViewById<ImageButton>(R.id.btnSendMessage).setOnClickListener {
            val content = findViewById<EditText>(R.id.etMessage).text.toString().trim()
            if (content.isNotEmpty()) {
                sendMessage(content)
            }
        }

        loadMessages()
        startPolling()

        val btnBlock = findViewById<ImageButton>(R.id.btnBlockUser)

        lifecycleScope.launch {
            try {
                val blockedResponse = RetrofitInstance.api.getBlockedUsers(userId)
                if (blockedResponse.isSuccessful) {
                    val blockedIds = blockedResponse.body() ?: emptyList()
                    if (blockedIds.contains(otherUserId)) {
                        btnBlock.setImageResource(android.R.drawable.ic_menu_add)
                        btnBlock.tag = "blocked"
                    }
                }
            } catch (_: Exception) { }
        }

        btnBlock.setOnClickListener {
            val isBlocked = btnBlock.tag == "blocked"

            if (!isBlocked) {
                android.app.AlertDialog.Builder(this@PrivateChatActivity)
                    .setTitle("Blochează utilizator")
                    .setMessage("Ești sigur că vrei să blochezi pe $otherUserName? Nu vei mai putea schimba mesaje.")
                    .setPositiveButton("Blochează") { _, _ ->
                        lifecycleScope.launch {
                            try {
                                RetrofitInstance.api.blockUser(BlockUserRequest(userId, otherUserId))
                                btnBlock.setImageResource(android.R.drawable.ic_menu_add)
                                btnBlock.tag = "blocked"
                                Toast.makeText(this@PrivateChatActivity, "$otherUserName a fost blocat", Toast.LENGTH_SHORT).show()
                                finish()
                            } catch (e: Exception) {
                                Toast.makeText(this@PrivateChatActivity, "Eroare: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton("Anulează", null)
                    .show()
            } else {
                lifecycleScope.launch {
                    try {
                        RetrofitInstance.api.unblockUser(BlockUserRequest(userId, otherUserId))
                        btnBlock.setImageResource(android.R.drawable.ic_lock_lock)
                        btnBlock.tag = "unblocked"
                        Toast.makeText(this@PrivateChatActivity, "$otherUserName a fost deblocat", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@PrivateChatActivity, "Eroare: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

    }

    private fun loadMessages() {
        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.getMessages(userId, otherUserId)
                if (response.isSuccessful) {
                    val newMessages = response.body() ?: emptyList()
                    messages.clear()
                    messages.addAll(newMessages)
                    adapter.notifyDataSetChanged()
                    if (messages.isNotEmpty()) {
                        findViewById<RecyclerView>(R.id.rvMessages)
                            .scrollToPosition(messages.size - 1)
                    }
                }
            } catch (_: Exception) { }
        }
    }

    private fun sendMessage(content: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.sendMessage(
                    SendMessageRequest(userId, otherUserId, content)
                )
                if (response.isSuccessful) {
                    findViewById<EditText>(R.id.etMessage).text.clear()
                    loadMessages()
                }
            } catch (_: Exception) {
                Toast.makeText(this@PrivateChatActivity, "Eroare trimitere", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startPolling() {
        lifecycleScope.launch {
            while (true) {
                delay(5000)
                loadMessages()
            }
        }
    }
}

class MessagesAdapter(
    private val messages: List<PrivateMessageDTO>,
    private val myUserId: Int
) : RecyclerView.Adapter<MessagesAdapter.VH>() {

    class VH(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = if (viewType == 1) R.layout.item_message_sent
        else R.layout.item_message_received
        val v = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return VH(v)
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].fromUserId == myUserId) 1 else 0
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val msg = messages[position]
        holder.view.findViewById<TextView>(R.id.tvMessageContent).text = msg.content
        holder.view.findViewById<TextView>(R.id.tvMessageTime).text = msg.createdAt.take(16).replace("T", " ")
    }

    override fun getItemCount() = messages.size
}