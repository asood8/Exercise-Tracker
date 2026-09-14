package com.example.exercisetracker

import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

// The "New routine" dialog. onSave gets the routine once everything in the dialog is valid.
object RoutineBuilder {
    private const val MAX_ROUNDS = 10
    private const val MAX_REST_SECONDS = 300

    fun show(activity: AppCompatActivity, onSave: (Routine) -> Unit) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_routine_builder, null)
        val nameLayout = view.findViewById<TextInputLayout>(R.id.routineNameLayout)
        val nameInput = view.findViewById<TextInputEditText>(R.id.routineNameInput)
        val stepsContainer = view.findViewById<LinearLayout>(R.id.routineStepsContainer)
        val addStepButton = view.findViewById<Button>(R.id.addStepButton)
        val roundsLayout = view.findViewById<TextInputLayout>(R.id.routineRoundsLayout)
        val roundsInput = view.findViewById<TextInputEditText>(R.id.routineRoundsInput)
        val restLayout = view.findViewById<TextInputLayout>(R.id.routineRestLayout)
        val restInput = view.findViewById<TextInputEditText>(R.id.routineRestInput)
        val exerciseNames = Exercise.entries.map { it.displayName }

        fun addStepRow() {
            val row = activity.layoutInflater.inflate(R.layout.item_routine_step, stepsContainer, false)
            val spinner = row.findViewById<Spinner>(R.id.stepExerciseSpinner)
            val targetLayout = row.findViewById<TextInputLayout>(R.id.stepTargetLayout)
            spinner.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, exerciseNames)
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, selected: View?, position: Int, id: Long) {
                    targetLayout.hint = if (Exercise.entries[position].isTimed) "Seconds" else "Reps"
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
            row.findViewById<ImageButton>(R.id.removeStepButton).setOnClickListener {
                stepsContainer.removeView(row)
                addStepButton.isEnabled = true
            }
            stepsContainer.addView(row)
            addStepButton.isEnabled = stepsContainer.childCount < Routines.MAX_STEPS
        }

        addStepRow()
        addStepButton.setOnClickListener { addStepRow() }

        // Returns the routine, or null after flagging whatever needs fixing
        fun readRoutine(): Routine? {
            listOf(nameLayout, roundsLayout, restLayout).forEach { it.error = null }
            var valid = true

            val name = nameInput.text.toString().trim()
            if (name.isEmpty()) {
                nameLayout.error = "Give it a name"
                valid = false
            } else if (Routines.nameTaken(activity, name)) {
                nameLayout.error = "There's already a routine called that"
                valid = false
            }

            val steps = (0 until stepsContainer.childCount).mapNotNull { i ->
                val row = stepsContainer.getChildAt(i)
                val exercise = Exercise.entries[row.findViewById<Spinner>(R.id.stepExerciseSpinner).selectedItemPosition]
                val targetLayout = row.findViewById<TextInputLayout>(R.id.stepTargetLayout)
                val target = row.findViewById<TextInputEditText>(R.id.stepTargetInput).text.toString().toIntOrNull()
                targetLayout.error = null
                if (target == null || target !in 1..999) {
                    targetLayout.error = "1 to 999"
                    valid = false
                    null
                } else {
                    PlanStep(exercise, target)
                }
            }
            if (stepsContainer.childCount == 0) {
                Toast.makeText(activity, "Add at least one exercise", Toast.LENGTH_SHORT).show()
                valid = false
            }

            val rounds = roundsInput.text.toString().toIntOrNull()
            if (rounds == null || rounds !in 1..MAX_ROUNDS) {
                roundsLayout.error = "1 to $MAX_ROUNDS"
                valid = false
            }
            val rest = restInput.text.toString().toIntOrNull()
            if (rest == null || rest !in 0..MAX_REST_SECONDS) {
                restLayout.error = "0 to $MAX_REST_SECONDS"
                valid = false
            }

            return if (valid && rounds != null && rest != null) Routine(name, steps, rounds, rest) else null
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle("New routine")
            .setView(view)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        // Set the click listener after showing so a validation error keeps the dialog open
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val routine = readRoutine() ?: return@setOnClickListener
                onSave(routine)
                dialog.dismiss()
            }
        }
        dialog.show()
    }
}
