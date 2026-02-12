package com.pragyan.frontendandroid.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.pragyan.frontendandroid.presentation.navigation.LocalExerciseNavState
import com.pragyan.frontendandroid.presentation.components.BottomNavBar
import com.pragyan.frontendandroid.presentation.components.TopNavBarWithIcon
import com.pragyan.frontendandroid.ui.theme.FrontendAndroidTheme
import kotlinx.coroutines.delay

@Composable
fun UploadScreen(
    modifier: Modifier = Modifier,
    navController: NavController
) {
    var uploadProgress by remember { mutableFloatStateOf(0.5f) }
    var isUploading by remember { mutableStateOf(true) }
    val exercise = LocalExerciseNavState.current.selectedExercise


    Scaffold(
        modifier = modifier,
        bottomBar = { BottomNavBar(navController = navController) },
        topBar = {
            TopNavBarWithIcon(
                title = exercise?.exerciseName ?: "Upload Exercise",
                navController = navController
            )
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier.padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    // Header Text
                    Text(
                        text = "Upload a video of your ${(exercise?.exerciseName)?.lowercase()} to receive\npersonalized feedback on your form.",
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )

                    // Dashed Border Container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
//                            .height(200.dp)
                            .border(
                                width = 2.dp,
                                color = Color(0xFF2D4D4D),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            Text(
                                text = "Select Video",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Text(
                                text = "Tap to choose a video from your library",
                                color = Color(0xFF7A9999),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )

                            Button(
                                onClick = {},
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text(
                                    text = "Choose Video",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Upload Progress Section
                    if (isUploading) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Uploading...",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )

                            // Progress Bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onBackground,
                                        RoundedCornerShape(4.dp)
                                    )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(uploadProgress)
                                        .fillMaxHeight()
                                        .background(
                                            MaterialTheme.colorScheme.secondary,
                                            RoundedCornerShape(4.dp)
                                        )
                                )
                            }

                            Text(
                                text = "${(uploadProgress * 100).toInt()}% complete",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp
                            )

                            Text(
                                text = "Your video is being analyzed. Feedback will\nbe available shortly.",
                                color = Color.White,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            )
                        }
                    }

                }
            }
        }
    }
    // Simulate upload progress
    LaunchedEffect(isUploading) {
        if (isUploading) {
            delay(100)
            if (uploadProgress < 1f) {
                uploadProgress += 0.01f
            } else {
                isUploading = false
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun UploadScreenPreview() {
    FrontendAndroidTheme {
        val navController = rememberNavController()
        UploadScreen(navController = navController)
    }
}
