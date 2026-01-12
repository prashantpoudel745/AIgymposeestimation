package com.pragyan.aigymposeestimationapp.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.pragyan.aigymposeestimationapp.presentation.components.BottomNavBar
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    navController: NavController
) {
    Scaffold (
        modifier = modifier,
        bottomBar = { BottomNavBar(navController = navController) },
    ){ innerPadding ->
        Surface (
            modifier = Modifier.padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ){
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Dashboard Screen")
            }
        }
    }

}

@Preview(showBackground = true)
@Composable
fun DashboardScreenPreview() {
    val navController = rememberNavController()
    DashboardScreen(navController = navController)
}