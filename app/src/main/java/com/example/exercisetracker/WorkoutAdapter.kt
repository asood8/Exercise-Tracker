package com.example.exercisetracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.Exclude
import java.text.SimpleDateFormat
import java.util.*

@androidx.annotation.Keep // Firestore fills this in by reflection, so R8 must keep its fields
data class Workout(
    val id: String = "",
    val curls: Int = 0,
    val pushups: Int = 0,
    val squats: Int = 0,
    val situps: Int = 0,
    val overhead: Int = 0,
    val jacks: Int = 0,
    val lunges: Int = 0,
    val plank: Int = 0, // seconds
    val calories: Double = 0.0,
    val overallScore: Int = 0,
    val durationSeconds: Int = 0, // 0 for workouts saved before durations were recorded
    val sharedToLeaderboard: Boolean = false,
    val routine: String? = null, // Name of the routine followed, if any
    val edited: Boolean = false, // Counts were corrected by hand on the Summary screen
    val timestamp: com.google.firebase.Timestamp? = null
) {
    // Rep-based exercises only; plank is time-based
    @get:Exclude
    val totalReps: Int
        get() = curls + pushups + squats + situps + overhead + jacks + lunges
}

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
        workout.routine?.let { details.add("Routine: $it") }
        if (workout.curls > 0) details.add("Curls: ${workout.curls}")
        if (workout.pushups > 0) details.add("Pushups: ${workout.pushups}")
        if (workout.squats > 0) details.add("Squats: ${workout.squats}")
        if (workout.situps > 0) details.add("Situps: ${workout.situps}")
        if (workout.overhead > 0) details.add("Overhead: ${workout.overhead}")
        if (workout.jacks > 0) details.add("Jacks: ${workout.jacks}")
        if (workout.lunges > 0) details.add("Lunges: ${workout.lunges}")
        if (workout.plank > 0) details.add("Plank: ${formatPlankTime(workout.plank)}")
        if (workout.durationSeconds > 0) details.add("Time: ${formatDuration(workout.durationSeconds)}")
        details.add("Calories: %.1f".format(workout.calories))
        if (workout.edited) details.add("Counts edited")

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
