package com.pragyan.aigymposeestimationapp.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.pragyan.aigymposeestimationapp.presentation.navigation.NavigationScreens
import com.pragyan.aigymposeestimationapp.theme.GymPoseEstimationTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun BottomNavBar(navController: NavController) {
    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {

            BottomNavItem(
                icon = Icons.Filled.Home,
                label = "Dashboard"
            ) {
                navController.navigate(NavigationScreens.DashboardScreen.name)
            }

            BottomNavItem(
                icon = Icons.Outlined.FitnessCenter,
                label = "Exercise"
            ) {
                navController.navigate(NavigationScreens.ExerciseScreen.name)
            }

            BottomNavItem(
                icon = Icons.Outlined.Settings,
                label = "Settings"
            ) {
                navController.navigate(NavigationScreens.SettingsScreen.name)
            }
        }
    }
}


@Composable
fun BottomNavItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}


@Preview
@Composable
fun WidgetPreview() {
    GymPoseEstimationTheme {
        val navController = rememberNavController()
        BottomNavBar(navController = navController)
    }
}