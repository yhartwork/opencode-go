package com.example.codeonly.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.codeonly.R
import com.example.codeonly.api.Session
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionAdapter(
    private val onSessionClick: (Session) -> Unit,
    private val onSessionLongClick: (Session) -> Unit
) : ListAdapter<Session, SessionAdapter.SessionViewHolder>(SessionDiffCallback()) {

    private var selectedSessionId: String? = null

    fun setSelectedSession(sessionId: String?) {
        val oldId = selectedSessionId
        selectedSessionId = sessionId
        currentList.forEachIndexed { index, session ->
            if (session.id == oldId || session.id == sessionId) {
                notifyItemChanged(index)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_session, parent, false)
        return SessionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        val session = getItem(position)
        holder.bind(session, session.id == selectedSessionId)
    }

    inner class SessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.sessionTitle)
        private val summaryText: TextView = itemView.findViewById(R.id.sessionSummary)
        private val timeText: TextView = itemView.findViewById(R.id.sessionTime)

        fun bind(session: Session, isSelected: Boolean) {
            val title = session.title?.takeIf { it.isNotBlank() } 
                ?: session.slug 
                ?: "Session ${session.id.takeLast(6)}"
            titleText.text = title

            val summary = session.summary
            if (summary != null) {
                summaryText.text = "+${summary.additions} -${summary.deletions} (${summary.files} files)"
            } else {
                summaryText.text = ""
            }

            val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
            timeText.text = dateFormat.format(Date(session.time.updated))

            itemView.setBackgroundColor(
                if (isSelected) {
                    itemView.context.getColor(android.R.color.holo_blue_light)
                } else {
                    itemView.context.getColor(android.R.color.transparent)
                }
            )

            itemView.setOnClickListener { onSessionClick(session) }
            itemView.setOnLongClickListener {
                onSessionLongClick(session)
                true
            }
        }
    }

    private class SessionDiffCallback : DiffUtil.ItemCallback<Session>() {
        override fun areItemsTheSame(oldItem: Session, newItem: Session): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Session, newItem: Session): Boolean {
            return oldItem.id == newItem.id &&
                    oldItem.title == newItem.title &&
                    oldItem.time.updated == newItem.time.updated &&
                    oldItem.summary?.additions == newItem.summary?.additions &&
                    oldItem.summary?.deletions == newItem.summary?.deletions
        }
    }
}
