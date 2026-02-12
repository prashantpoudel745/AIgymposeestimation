package com.pragyan.frontendandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.pragyan.frontendandroid.presentation.navigation.AppNavigation
import com.pragyan.frontendandroid.ui.theme.FrontendAndroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            App()
        }
    }
}

@Composable
@Preview
fun App() {
    FrontendAndroidTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            AppNavigation()
        }
    }
}
