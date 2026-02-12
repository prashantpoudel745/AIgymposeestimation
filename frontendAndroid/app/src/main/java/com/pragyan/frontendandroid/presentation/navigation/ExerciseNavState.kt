package com.pragyan.frontendandroid.presentation.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.pragyan.frontendandroid.domain.model.Exercise

class ExerciseNavState {
    var selectedExercise by mutableStateOf<Exercise?>(null)
        private set

    fun selectExercise(exercise: Exercise) {
        selectedExercise = exercise
    }

    fun clear() {
        selectedExercise = null
    }
}

val LocalExerciseNavState =
    staticCompositionLocalOf<ExerciseNavState> {
        error("ExerciseNavState not provided")
    }