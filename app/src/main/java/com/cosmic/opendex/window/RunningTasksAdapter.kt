package com.cosmic.opendex.window

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cosmic.opendex.R

class RunningTasksAdapter(
    private val onTaskClick: (ActiveTask) -> Unit,
    private val onTaskClose: (ActiveTask) -> Unit,
    private val onTaskLongClick: ((ActiveTask, View) -> Boolean)? = null
) : ListAdapter<ActiveTask, RunningTasksAdapter.TaskViewHolder>(TaskDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_running_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.runningTaskIcon)
        private val labelView: TextView = itemView.findViewById(R.id.runningTaskLabel)
        private val closeBtn: ImageView = itemView.findViewById(R.id.runningTaskCloseBtn)
        private val indicator: View? = itemView.findViewById(R.id.runningTaskIndicator)

        fun bind(task: ActiveTask) {
            iconView.setImageDrawable(task.app.icon)
            labelView.text = task.app.label

            indicator?.visibility = if (!task.isPinnedOnly) View.VISIBLE else View.INVISIBLE
            itemView.alpha = if (!task.isPinnedOnly && task.isMinimized) 0.65f else 1.0f

            itemView.setOnClickListener {
                onTaskClick(task)
            }

            itemView.setOnLongClickListener { v ->
                val clipData = android.content.ClipData.newPlainText("dock_task", task.app.componentNameString)
                val shadow = View.DragShadowBuilder(v)
                v.startDragAndDrop(clipData, shadow, task, View.DRAG_FLAG_GLOBAL)
                onTaskLongClick?.invoke(task, v) ?: false
                true
            }

            // Mouse secondary click
            itemView.setOnGenericMotionListener { v, event ->
                if (event.action == android.view.MotionEvent.ACTION_BUTTON_PRESS &&
                    event.buttonState == android.view.MotionEvent.BUTTON_SECONDARY
                ) {
                    onTaskLongClick?.invoke(task, v) ?: false
                } else {
                    false
                }
            }

            closeBtn.setOnClickListener {
                onTaskClose(task)
            }
        }
    }

    private class TaskDiffCallback : DiffUtil.ItemCallback<ActiveTask>() {
        override fun areItemsTheSame(oldItem: ActiveTask, newItem: ActiveTask): Boolean {
            return oldItem.id == newItem.id && oldItem.isPinnedOnly == newItem.isPinnedOnly
        }

        override fun areContentsTheSame(oldItem: ActiveTask, newItem: ActiveTask): Boolean {
            return oldItem.bounds == newItem.bounds &&
                    oldItem.isMinimized == newItem.isMinimized &&
                    oldItem.isMaximized == newItem.isMaximized &&
                    oldItem.isPinnedOnly == newItem.isPinnedOnly &&
                    oldItem.snapPosition == newItem.snapPosition
        }
    }
}
