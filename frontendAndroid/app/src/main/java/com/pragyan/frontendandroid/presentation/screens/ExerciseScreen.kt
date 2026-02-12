package com.pragyan.frontendandroid.presentation.screens
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.pragyan.frontendandroid.domain.model.getExerciseSampleData
import com.pragyan.frontendandroid.presentation.navigation.LocalExerciseNavState
import com.pragyan.frontendandroid.presentation.navigation.NavigationScreens
import com.pragyan.frontendandroid.presentation.components.BottomNavBar
import com.pragyan.frontendandroid.presentation.components.TopNavBar
import com.pragyan.frontendandroid.ui.theme.FrontendAndroidTheme

@Composable
fun ExerciseScreen(
    modifier: Modifier = Modifier,
    navController: NavController
) {
    val exercises = getExerciseSampleData()
    val exerciseNavState = LocalExerciseNavState.current

    Scaffold(
        modifier = modifier,
        bottomBar = { BottomNavBar(navController = navController) },
        topBar = { TopNavBar(title = "Exercises") }
    ) { innerPadding ->
        Surface(
            modifier = Modifier.padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(exercises) { exercise ->
                        OutlinedCard(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.background,
                            ),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.tertiary
                            ),
                            modifier = Modifier
                                .size(width = 175.dp, height = 187.dp)
                                .clickable {
                                    exerciseNavState.selectExercise(exercise)
                                    navController.navigate(NavigationScreens.UploadScreen.name)
                                }
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Image(
                                    painter = painterResource(exercise.exerciseImage),
                                    contentDescription = "Exercise Image",
                                    modifier = Modifier.size(
                                        width = 175.dp,
                                        height = 97.dp
                                    ),
                                    contentScale = ContentScale.Crop
                                )
                                Text(
                                    text = exercise.exerciseName,
                                    modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                                )
                                Text(
                                    text = exercise.exerciseDescription,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ExerciseScreenPreview() {
    FrontendAndroidTheme {
        val navController = rememberNavController()
        ExerciseScreen(navController = navController)
    }
}