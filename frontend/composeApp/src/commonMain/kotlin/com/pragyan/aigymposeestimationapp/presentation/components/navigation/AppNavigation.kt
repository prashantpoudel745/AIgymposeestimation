package com.pragyan.aigymposeestimationapp.presentation.components.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pragyan.aigymposeestimationapp.presentation.screens.DashboardScreen
import com.pragyan.aigymposeestimationapp.presentation.screens.ExerciseScreen
import com.pragyan.aigymposeestimationapp.presentation.screens.SettingsScreen


@Composable
fun AppNavigation(){
    val navController = rememberNavController()
    NavHost(
        navController = navController, startDestination = NavigationScreens.DashboardScreen.name
    ) {
        composable(route = NavigationScreens.DashboardScreen.name) {
            DashboardScreen(navController = navController)
        }
        composable(route = NavigationScreens.ExerciseScreen.name) {
            ExerciseScreen(navController = navController)
        }
        composable(route = NavigationScreens.SettingsScreen.name) {
            SettingsScreen(navController = navController)
        }

    }
}