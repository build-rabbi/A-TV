package com.buildrabbi.atv

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class ChannelAdapter(
    context: Context,
    private val channels: List<Channel>,
    private var activeIndex: Int = 0
) : ArrayAdapter<Channel>(context, R.layout.channel_list_item, channels) {

    fun setActiveIndex(index: Int) {
        activeIndex = index
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.channel_list_item, parent, false)

        val ch = channels[position]
        view.findViewById<TextView>(R.id.chNumber).text = "${position + 1}"
        view.findViewById<TextView>(R.id.chName).text = ch.name
        view.findViewById<TextView>(R.id.chGroup).text = ch.category
        val indicator = view.findViewById<View>(R.id.chActive)

        if (position == activeIndex) {
            view.setBackgroundColor(android.graphics.Color.parseColor("#0D1A2A"))
            view.findViewById<TextView>(R.id.chName).setTextColor(android.graphics.Color.parseColor("#00C2FF"))
            indicator.visibility = View.VISIBLE
        } else {
            view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            view.findViewById<TextView>(R.id.chName).setTextColor(android.graphics.Color.parseColor("#E8EDF2"))
            indicator.visibility = View.INVISIBLE
        }
        return view
    }
}
