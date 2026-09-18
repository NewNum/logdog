package com.example.logdoy.lib

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class LogListAdapter : RecyclerView.Adapter<LogListAdapter.LogViewHolder>() {

    private val entries = mutableListOf<LogEntry>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun submit(list: List<LogEntry>) {
        entries.clear()
        entries.addAll(list)
        notifyDataSetChanged()
    }

    fun append(entry: LogEntry) {
        entries.add(entry)
        notifyItemInserted(entries.lastIndex)
    }

    override fun getItemCount(): Int = entries.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.logdoy_log_item, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(entries[position], timeFormat)
    }

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.logdoy_item_text)

        fun bind(entry: LogEntry, timeFormat: SimpleDateFormat) {
            val time = timeFormat.format(Date(entry.timestampMs))
            val text = if (entry.tag.isNullOrEmpty()) {
                "$time  ${entry.message}"
            } else {
                "$time  [${entry.tag}] ${entry.message}"
            }
            textView.text = text
        }
    }
}
