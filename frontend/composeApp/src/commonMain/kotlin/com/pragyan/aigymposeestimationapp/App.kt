package com.pragyan.aigymposeestimationapp

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pragyan.aigymposeestimationapp.presentation.components.navigation.AppNavigation
import org.jetbrains.compose.ui.tooling.preview.Preview


@Composable
@Preview
fun App() {
    MaterialTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { _ ->
            AppNavigation()
        }
    }
}
