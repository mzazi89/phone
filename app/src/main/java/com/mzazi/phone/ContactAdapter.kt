package com.mzazi.phone

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ContactAdapter(private val onClick: (Contact) -> Unit) :
    RecyclerView.Adapter<ContactAdapter.Holder>() {

    private val items = ArrayList<Contact>()

    fun submit(list: List<Contact>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position], onClick)
    }

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatar: TextView = itemView.findViewById(R.id.tvAvatar)
        private val name: TextView = itemView.findViewById(R.id.tvName)
        private val number: TextView = itemView.findViewById(R.id.tvNumber)

        fun bind(contact: Contact, onClick: (Contact) -> Unit) {
            avatar.text = T9.initials(contact.name)
            name.text = contact.name
            number.text = if (contact.numbers.size > 1) {
                "${T9.formatNumber(contact.primaryNumber)}  ·  ${contact.numbers.size} numbers"
            } else {
                T9.formatNumber(contact.primaryNumber)
            }
            itemView.setOnClickListener { onClick(contact) }
        }
    }
}
