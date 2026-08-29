package com.example.exercisetracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class UserStats(
    val userId: String = "",
    val email: String = "Anonymous",
    val totalReps: Int = 0,
    val totalCalories: Double = 0.0,
    val avgQuality: Int = 0,
    val level: Int = 1
)

class LeaderboardAdapter(private var users: List<UserStats>) :
    RecyclerView.Adapter<LeaderboardAdapter.LeaderboardViewHolder>() {

    class LeaderboardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val rankText: TextView = view.findViewById(R.id.rankText)
        val nameText: TextView = view.findViewById(R.id.userNameText)
        val statsText: TextView = view.findViewById(R.id.userStatsText)
        val levelText: TextView = view.findViewById(R.id.userLevelText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeaderboardViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_leaderboard, parent, false)
        return LeaderboardViewHolder(view)
    }

    override fun onBindViewHolder(holder: LeaderboardViewHolder, position: Int) {
        val user = users[position]
        holder.rankText.text = "#${position + 1}"
        holder.nameText.text = if (user.email.contains("@")) user.email.split("@")[0] else user.email
        holder.statsText.text = "${user.totalReps} reps | ${user.totalCalories.toInt()} kcal"
        holder.levelText.text = "Lvl ${user.level}"
    }

    override fun getItemCount() = users.size

    fun updateUsers(newUsers: List<UserStats>) {
        users = newUsers
        notifyDataSetChanged()
    }
}
