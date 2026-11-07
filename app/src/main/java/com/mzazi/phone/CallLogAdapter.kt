package com.mzazi.phone

import android.provider.CallLog
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class CallLogAdapter(
    private val simManager: SimManager,
    private val countryLabel: String,
    private val onClick: (CallEntry) -> Unit
) : RecyclerView.Adapter<CallLogAdapter.Holder>() {

    private val items = ArrayList<CallEntry>()

    fun submit(list: List<CallEntry>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun itemCountSafe(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_call_log, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position], simManager, countryLabel, onClick)
    }

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val badge: TextView = itemView.findViewById(R.id.tvSimBadge)
        private val name: TextView = itemView.findViewById(R.id.tvName)
        private val detail: TextView = itemView.findViewById(R.id.tvDetail)
        private val time: TextView = itemView.findViewById(R.id.tvTime)

        fun bind(
            entry: CallEntry,
            simManager: SimManager,
            countryLabel: String,
            onClick: (CallEntry) -> Unit
        ) {
            val context = itemView.context
            val title = entry.name ?: if (entry.number.isBlank()) {
                context.getString(R.string.unknown_caller)
            } else {
                T9.formatNumber(entry.number)
            }
            name.text = title

            badge.text = simManager.slotOf(entry.accountId)
            val badgeColor = when (entry.type) {
                CallLog.Calls.MISSED_TYPE,
                CallLog.Calls.REJECTED_TYPE,
                CallLog.Calls.BLOCKED_TYPE -> R.color.danger
                CallLog.Calls.OUTGOING_TYPE -> R.color.call_start
                else -> R.color.accent
            }
            badge.setTextColor(ContextCompat.getColor(context, badgeColor))

            val typeLabel = when (entry.type) {
                CallLog.Calls.MISSED_TYPE -> context.getString(R.string.call_missed)
                CallLog.Calls.REJECTED_TYPE, CallLog.Calls.BLOCKED_TYPE ->
                    context.getString(R.string.call_blocked)
                CallLog.Calls.OUTGOING_TYPE -> context.getString(R.string.call_outgoing)
                CallLog.Calls.INCOMING_TYPE -> context.getString(R.string.call_incoming_type)
                else -> ""
            }
            detail.text = listOf(typeLabel, countryLabel)
                .filter { it.isNotBlank() }
                .joinToString("  ·  ")

            time.text = DateUtils.getRelativeTimeSpanString(
                entry.date,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )

            itemView.setOnClickListener { onClick(entry) }
        }
    }
}
