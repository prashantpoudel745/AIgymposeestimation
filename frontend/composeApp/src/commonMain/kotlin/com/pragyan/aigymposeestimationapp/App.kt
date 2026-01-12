package com.pragyan.aigymposeestimationapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.pragyan.aigymposeestimationapp.presentation.components.navigation.AppNavigation
import com.pragyan.aigymposeestimationapp.theme.GymPoseEstimationTheme
import org.jetbrains.compose.ui.tooling.preview.Preview


@Composable
@Preview
fun App() {
    GymPoseEstimationTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            AppNavigation()
        }
    }
}
