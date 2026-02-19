package com.example.codeonly.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.codeonly.R
import com.example.codeonly.api.ChatMessage
import com.example.codeonly.api.Part

data class ChatBubble(
    val role: String, // "user" or "assistant"
    val text: String,
    val reasoning: String? = null,
    val isStreaming: Boolean = false
)

class ChatAdapter : ListAdapter<ChatBubble, ChatAdapter.ChatViewHolder>(ChatDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun appendStreamingText(text: String) {
        val currentList = currentList.toMutableList()
        if (currentList.isNotEmpty() && currentList.last().isStreaming) {
            val last = currentList.last()
            currentList[currentList.size - 1] = last.copy(
                text = last.text + text,
                isStreaming = true
            )
            submitList(currentList)
        }
    }

    fun finishStreaming() {
        val currentList = currentList.toMutableList()
        if (currentList.isNotEmpty() && currentList.last().isStreaming) {
            val last = currentList.last()
            currentList[currentList.size - 1] = last.copy(isStreaming = false)
            submitList(currentList)
        }
    }

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userContainer: LinearLayout = itemView.findViewById(R.id.userBubbleContainer)
        private val userText: TextView = itemView.findViewById(R.id.userMessageText)
        private val assistantContainer: LinearLayout = itemView.findViewById(R.id.assistantBubbleContainer)
        private val assistantText: TextView = itemView.findViewById(R.id.assistantMessageText)
        private val reasoningText: TextView = itemView.findViewById(R.id.reasoningText)

        fun bind(bubble: ChatBubble) {
            if (bubble.role == "user") {
                userContainer.visibility = View.VISIBLE
                assistantContainer.visibility = View.GONE
                userText.text = bubble.text
            } else {
                userContainer.visibility = View.GONE
                assistantContainer.visibility = View.VISIBLE
                assistantText.text = bubble.text

                if (!bubble.reasoning.isNullOrBlank()) {
                    reasoningText.visibility = View.VISIBLE
                    reasoningText.text = bubble.reasoning
                } else {
                    reasoningText.visibility = View.GONE
                }
            }
        }
    }

    private class ChatDiffCallback : DiffUtil.ItemCallback<ChatBubble>() {
        override fun areItemsTheSame(oldItem: ChatBubble, newItem: ChatBubble): Boolean {
            return oldItem.role == newItem.role && 
                   oldItem.isStreaming == newItem.isStreaming
        }

        override fun areContentsTheSame(oldItem: ChatBubble, newItem: ChatBubble): Boolean {
            return oldItem == newItem
        }
    }
}
