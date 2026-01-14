package com.pragyan.aigymposeestimationapp.domain.model

import aigymposeestimationapp.composeapp.generated.resources.Res
import aigymposeestimationapp.composeapp.generated.resources.benchpress
import aigymposeestimationapp.composeapp.generated.resources.bicep
import aigymposeestimationapp.composeapp.generated.resources.deadlift
import aigymposeestimationapp.composeapp.generated.resources.legpress
import aigymposeestimationapp.composeapp.generated.resources.plank
import aigymposeestimationapp.composeapp.generated.resources.tricep
import org.jetbrains.compose.resources.DrawableResource

data class Exercise(
    val exerciseId: Int,
    val exerciseName: String,
    val targetMuscle: String,
    val exerciseDescription: String,
    val exerciseImage: DrawableResource
)

fun getExerciseSampleData(): List<Exercise> {
    return listOf(
        Exercise(1, "Bicep Curls", "Biceps", "Strengthens the biceps.", Res.drawable.bicep),
        Exercise(2, "Triceps Pushdown", "Triceps", "Targets the triceps.", Res.drawable.tricep),
        Exercise(3, "Leg Press", "Quadriceps", "Builds leg strength.", Res.drawable.legpress),
        Exercise(4, "Bench Press", "Chest", "Develops chest muscles.", Res.drawable.benchpress),
        Exercise(5, "Deadlifts", "Back", "Strengthens the back.", Res.drawable.deadlift),
        Exercise(6, "Plank", "Core", "Improves core stability.", Res.drawable.plank)
    )
}
