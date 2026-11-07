package com.mzazi.phone

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RecentAdapter(private val onClick: (RecentCall) -> Unit) :
    RecyclerView.Adapter<RecentAdapter.Holder>() {

    private val items = ArrayList<RecentCall>()

    fun submit(list: List<RecentCall>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position], onClick)
    }

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatar: TextView = itemView.findViewById(R.id.tvAvatar)
        private val name: TextView = itemView.findViewById(R.id.tvName)
        private val meta: TextView = itemView.findViewById(R.id.tvMeta)

        fun bind(call: RecentCall, onClick: (RecentCall) -> Unit) {
            val title = call.name ?: T9.formatNumber(call.number)
            avatar.text = T9.initials(title)
            name.text = title

            val relative = DateUtils.getRelativeTimeSpanString(
                call.timestamp,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )
            meta.text = "${T9.formatNumber(call.number)}  ·  $relative"

            itemView.setOnClickListener { onClick(call) }
        }
    }
}
