package com.cosmic.opendex.desktop

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cosmic.opendex.R
import com.cosmic.opendex.data.AppModel

class AppGridAdapter(
    private val onAppClick: (AppModel) -> Unit,
    private val onAppLongClick: ((AppModel, View) -> Boolean)? = null
) : ListAdapter<AppModel, AppGridAdapter.AppViewHolder>(AppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_icon, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = getItem(position)
        holder.bind(app)
    }

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconImage: ImageView = itemView.findViewById(R.id.appIconImage)
        private val labelText: TextView = itemView.findViewById(R.id.appLabelText)

        fun bind(app: AppModel) {
            iconImage.setImageDrawable(app.icon)
            labelText.text = app.label

            itemView.setOnClickListener {
                onAppClick(app)
            }

            itemView.setOnLongClickListener { v ->
                onAppLongClick?.invoke(app, v) ?: false
            }

            // Mouse right-click support
            itemView.setOnGenericMotionListener { v, event ->
                if (event.action == android.view.MotionEvent.ACTION_BUTTON_PRESS &&
                    event.buttonState == android.view.MotionEvent.BUTTON_SECONDARY
                ) {
                    onAppLongClick?.invoke(app, v) ?: false
                } else {
                    false
                }
            }
        }
    }

    private class AppDiffCallback : DiffUtil.ItemCallback<AppModel>() {
        override fun areItemsTheSame(oldItem: AppModel, newItem: AppModel): Boolean {
            return oldItem.componentNameString == newItem.componentNameString
        }

        override fun areContentsTheSame(oldItem: AppModel, newItem: AppModel): Boolean {
            return oldItem == newItem
        }
    }
}
