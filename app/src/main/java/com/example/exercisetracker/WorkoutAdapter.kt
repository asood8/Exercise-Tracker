package com.example.exercisetracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

data class Workout(
    val id: String = "",
    val curls: Int = 0,
    val pushups: Int = 0,
    val squats: Int = 0,
    val situps: Int = 0,
    val overhead: Int = 0,
    val calories: Double = 0.0,
    val overallScore: Int = 0,
    val timestamp: com.google.firebase.Timestamp? = null
)

class WorkoutAdapter(
    private var workouts: List<Workout>,
    private val onDeleteClick: (Workout) -> Unit
) : RecyclerView.Adapter<WorkoutAdapter.WorkoutViewHolder>() {

    class WorkoutViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dateText: TextView = view.findViewById(R.id.workoutDate)
        val detailsText: TextView = view.findViewById(R.id.workoutDetails)
        val scoreText: TextView = view.findViewById(R.id.workoutScore)
        val deleteButton: ImageButton = view.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkoutViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_workout, parent, false)
        return WorkoutViewHolder(view)
    }

    override fun onBindViewHolder(holder: WorkoutViewHolder, position: Int) {
        val workout = workouts[position]
        
        val sdf = SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault())
        val dateStr = workout.timestamp?.toDate()?.let { sdf.format(it) } ?: "Unknown Date"
        holder.dateText.text = dateStr

        val details = mutableListOf<String>()
        if (workout.curls > 0) details.add("Curls: ${workout.curls}")
        if (workout.pushups > 0) details.add("Pushups: ${workout.pushups}")
        if (workout.squats > 0) details.add("Squats: ${workout.squats}")
        if (workout.situps > 0) details.add("Situps: ${workout.situps}")
        if (workout.overhead > 0) details.add("Overhead: ${workout.overhead}")
        details.add("Calories: %.1f".format(workout.calories))
        
        holder.detailsText.text = details.joinToString(", ")
        holder.scoreText.text = if (workout.overallScore == -1) "Score: N/A" else "Score: ${workout.overallScore}%"

        holder.deleteButton.setOnClickListener { onDeleteClick(workout) }
    }

    override fun getItemCount() = workouts.size

    fun updateWorkouts(newWorkouts: List<Workout>) {
        workouts = newWorkouts
        notifyDataSetChanged()
    }
}
