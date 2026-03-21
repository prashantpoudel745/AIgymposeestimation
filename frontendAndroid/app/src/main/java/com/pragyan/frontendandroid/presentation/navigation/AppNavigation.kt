package com.pragyan.frontendandroid.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pragyan.frontendandroid.presentation.screens.CameraScreen
import com.pragyan.frontendandroid.presentation.screens.DashboardScreen
import com.pragyan.frontendandroid.presentation.screens.ExerciseScreen
import com.pragyan.frontendandroid.presentation.screens.LoginScreen
import com.pragyan.frontendandroid.presentation.screens.RegisterScreen
import com.pragyan.frontendandroid.presentation.screens.SettingsScreen
import com.pragyan.frontendandroid.presentation.screens.UploadScreen
import com.pragyan.frontendandroid.presentation.viewmodel.ExerciseViewModel


@Composable
fun AppNavigation(){
    val exerciseNavState = remember { ExerciseNavState() }
    val navController = rememberNavController()
    // Hoist ExerciseViewModel so it survives navigation from CameraScreen to Dashboard
    val exerciseViewModel: ExerciseViewModel = viewModel()

    CompositionLocalProvider(
        LocalExerciseNavState provides exerciseNavState
    ) {
        NavHost(
            navController = navController, startDestination = NavigationScreens.LoginScreen.name
        ) {
            composable(route = NavigationScreens.LoginScreen.name) {
                LoginScreen(navController = navController)
            }
            composable(route = NavigationScreens.RegisterScreen.name) {
                RegisterScreen(navController = navController)
            }
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
            composable (route = NavigationScreens.CameraScreen.name){
                CameraScreen(navController = navController, viewModel = exerciseViewModel)
            }

        }
    }
}