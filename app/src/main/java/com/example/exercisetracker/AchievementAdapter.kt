package com.example.exercisetracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AchievementAdapter(
    private val achievements: List<Achievement>,
    private val onClick: (Achievement, Boolean) -> Unit
) : RecyclerView.Adapter<AchievementAdapter.AchievementViewHolder>() {

    private var unlockedIds: Set<String> = emptySet()

    class AchievementViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: TextView = view.findViewById(R.id.achievementIcon)
        val title: TextView = view.findViewById(R.id.achievementTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AchievementViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_achievement, parent, false)
        return AchievementViewHolder(view)
    }

    override fun onBindViewHolder(holder: AchievementViewHolder, position: Int) {
        val achievement = achievements[position]
        val unlocked = achievement.id in unlockedIds
        holder.icon.text = achievement.icon
        holder.title.text = achievement.title
        // Locked achievements stay visible but faded, so there's something to aim for
        holder.itemView.alpha = if (unlocked) 1f else 0.35f
        holder.itemView.contentDescription = "${achievement.title}, ${if (unlocked) "unlocked" else "locked"}"
        holder.itemView.setOnClickListener { onClick(achievement, unlocked) }
    }

    override fun getItemCount() = achievements.size

    fun update(unlockedIds: Set<String>) {
        this.unlockedIds = unlockedIds
        notifyDataSetChanged()
    }
}
