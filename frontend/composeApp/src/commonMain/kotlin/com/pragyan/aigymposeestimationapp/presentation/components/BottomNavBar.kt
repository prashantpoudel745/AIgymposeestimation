package com.pragyan.aigymposeestimationapp.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.pragyan.aigymposeestimationapp.presentation.components.navigation.NavigationScreens
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun BottomNavBar(navController: NavController) {
    BottomAppBar(
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {

                IconButton(onClick = {
                    navController.navigate(route = NavigationScreens.DashboardScreen.name)
                }) {
                    Icon(
                        Icons.Filled.Home,
                        contentDescription = "Home icon"
                    )
                }

                IconButton(
                    onClick = {
                        navController.navigate(route = NavigationScreens.ExerciseScreen.name)
                    }
                ) {
                    Icon(
                        Icons.Filled.FitnessCenter,
                        contentDescription = "Fitness icon"
                    )
                }
                IconButton(onClick = {
                    navController.navigate(route = NavigationScreens.SettingsScreen.name)
                }) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Settings icon",
                    )
                }
            }

        }
    )
}

@Preview
@Composable
fun WidgetPreview() {
    val navController = rememberNavController()
    BottomNavBar(navController = navController)
}