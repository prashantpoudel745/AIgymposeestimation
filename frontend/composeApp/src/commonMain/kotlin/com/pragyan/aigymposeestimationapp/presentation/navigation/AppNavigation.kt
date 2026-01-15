package com.pragyan.aigymposeestimationapp.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pragyan.aigymposeestimationapp.presentation.screens.DashboardScreen
import com.pragyan.aigymposeestimationapp.presentation.screens.ExerciseScreen
import com.pragyan.aigymposeestimationapp.presentation.screens.SettingsScreen
import com.pragyan.aigymposeestimationapp.presentation.screens.UploadScreen


@Composable
fun AppNavigation(){
    val exerciseNavState = remember { ExerciseNavState() }
    val navController = rememberNavController()

    CompositionLocalProvider(
        LocalExerciseNavState provides exerciseNavState
    ) {
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
            composable(route = NavigationScreens.UploadScreen.name) {
                UploadScreen(navController = navController)
            }

        }
    }
}