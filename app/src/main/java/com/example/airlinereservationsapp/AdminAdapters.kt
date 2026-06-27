package com.example.airlinereservationsapp

import android.graphics.Color
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

// ══════════════════════════════════════════════════════
// Adapter pentru lista utilizatori
// ══════════════════════════════════════════════════════
class AdminUsersAdapter(
    private val users: List<AdminUserResponse>
) : RecyclerView.Adapter<AdminUsersAdapter.VH>() {

    class VH(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_user, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val u = users[position]
        holder.view.findViewById<TextView>(R.id.tvAdminUserName).text = u.fullName
        holder.view.findViewById<TextView>(R.id.tvAdminUserEmail).text = u.email

        val tvRole = holder.view.findViewById<TextView>(R.id.tvAdminUserRole)
        tvRole.text = u.role
        tvRole.setTextColor(if (u.role == "ADMIN") Color.parseColor("#00897B")
        else Color.parseColor("#666666"))

        holder.view.findViewById<TextView>(R.id.tvAdminUserDate).text =
            u.createdAt.take(10)
    }

    override fun getItemCount() = users.size
}

// ══════════════════════════════════════════════════════
// Adapter pentru requesturi de modificare
// ══════════════════════════════════════════════════════
class AdminRequestsAdapter(
    private val requests: List<ModificationRequestResponse>,
    private val onRefresh: () -> Unit
) : RecyclerView.Adapter<AdminRequestsAdapter.VH>() {

    class VH(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_request, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = requests[position]

        holder.view.findViewById<TextView>(R.id.tvReqUser).text =
            "${r.userFullName} (${r.userEmail})"
        holder.view.findViewById<TextView>(R.id.tvReqRoute).text =
            "${r.origin} ✈ ${r.destination} — Booking #${r.bookingId}"
        holder.view.findViewById<TextView>(R.id.tvReqType).text = when (r.requestType) {
            "DESTINATION" -> "✏️ Schimbare destinatie"
            "TIME"        -> "🕐 Schimbare ora"
            "CANCEL"      -> "❌ Anulare + Refund"
            else          -> "📝 Altele"
        }
        holder.view.findViewById<TextView>(R.id.tvReqDescription).text = r.description
        holder.view.findViewById<TextView>(R.id.tvReqDate).text = r.createdAt.take(16)

        val tvStatus = holder.view.findViewById<TextView>(R.id.tvReqStatus)
        tvStatus.text = r.status
        tvStatus.setTextColor(when (r.status) {
            "APPROVED" -> Color.parseColor("#4CAF50")
            "REJECTED" -> Color.parseColor("#F44336")
            else       -> Color.parseColor("#FF9800")
        })

        val btnApprove = holder.view.findViewById<Button>(R.id.btnApproveRequest)
        val btnReject  = holder.view.findViewById<Button>(R.id.btnRejectRequest)
        val noteLayout = holder.view.findViewById<View>(R.id.layoutAdminNote)

        if (r.status == "PENDING") {
            btnApprove.visibility = View.VISIBLE
            btnReject.visibility  = View.VISIBLE
            noteLayout.visibility = View.GONE

            btnApprove.setOnClickListener {
                showResolveDialog(holder.view.context, r.id, true)
            }
            btnReject.setOnClickListener {
                showResolveDialog(holder.view.context, r.id, false)
            }
        } else {
            btnApprove.visibility = View.GONE
            btnReject.visibility  = View.GONE
            noteLayout.visibility = if (!r.adminNote.isNullOrEmpty()) View.VISIBLE else View.GONE
            holder.view.findViewById<TextView>(R.id.tvAdminNote).text = r.adminNote ?: ""
        }
    }

    private fun showResolveDialog(context: android.content.Context, requestId: Int, approve: Boolean) {
        val editNote = EditText(context).apply {
            hint = "Nota admin (optional)"
            setPadding(32, 16, 32, 16)
        }

        AlertDialog.Builder(context)
            .setTitle(if (approve) "Aprobare request" else "Respingere request")
            .setMessage(if (approve)
                "Esti sigur ca vrei sa aprobi acest request?"
            else
                "Esti sigur ca vrei sa respingi acest request?")
            .setView(editNote)
            .setPositiveButton(if (approve) "Aproba" else "Respinge") { _, _ ->
                val note = editNote.text.toString().trim()
                resolveRequest(context, requestId, approve, note.ifEmpty { null })
            }
            .setNegativeButton("Anuleaza", null)
            .show()
    }

    private fun resolveRequest(
        context: android.content.Context,
        requestId: Int,
        approved: Boolean,
        note: String?
    ) {
        (context as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope?.launch {
            try {
                val response = RetrofitInstance.api.resolveRequest(
                    requestId,
                    ResolveRequestBody(approved = approved, adminNote = note)
                )
                if (response.isSuccessful) {
                    Toast.makeText(context,
                        if (approved) "Request aprobat!" else "Request respins",
                        Toast.LENGTH_SHORT).show()
                    onRefresh()
                }
            } catch (_: Exception) {
                Toast.makeText(context, "Eroare", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun getItemCount() = requests.size
}