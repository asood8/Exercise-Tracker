package com.example.exercisetracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AchievementAdapter(private val achievements: List<Achievement>) :
    RecyclerView.Adapter<AchievementAdapter.AchievementViewHolder>() {

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
        holder.icon.text = achievement.icon
        holder.title.text = achievement.title
    }

    override fun getItemCount() = achievements.size
}
