package com.pragyan.aigymposeestimationapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pragyan.aigymposeestimationapp.presentation.navigation.AppNavigation
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
