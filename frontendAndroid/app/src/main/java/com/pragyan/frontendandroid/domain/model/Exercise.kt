package com.pragyan.frontendandroid.domain.model


import com.pragyan.frontendandroid.R


data class Exercise(
    val exerciseId: Int,
    val exerciseName: String,
    val targetMuscle: String,
    val exerciseDescription: String,
    val exerciseImage: Int
)

fun getExerciseSampleData(): List<Exercise> {
    return listOf(
        Exercise(1, "Bicep Curls", "Biceps", "Strengthens the biceps.", R.drawable.bicep),
        Exercise(2, "Triceps Pushdown", "Triceps", "Targets the triceps.", R.drawable.tricep),
        Exercise(3, "Leg Press", "Quadriceps", "Builds leg strength.", R.drawable.legpress),
        Exercise(4, "Bench Press", "Chest", "Develops chest muscles.", R.drawable.benchpress),
        Exercise(5, "Deadlifts", "Back", "Strengthens the back.", R.drawable.deadlift),
        Exercise(6, "Plank", "Core", "Improves core stability.", R.drawable.plank)
    )
}

enum class ExerciseType {
    BICEPS_CURL,
    DEADLIFT,
    TRICEPS_PUSHDOWN,
    LEG_PRESS
}