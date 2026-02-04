package com.pragyan.aigymposeestimationapp.presentation.screens

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.mohamedrejeb.calf.io.KmpFile
import com.mohamedrejeb.calf.io.getName
import com.mohamedrejeb.calf.picker.FilePickerSelectionMode
import com.mohamedrejeb.calf.picker.FilePickerFileType
import com.mohamedrejeb.calf.picker.rememberFilePickerLauncher
import com.pragyan.aigymposeestimationapp.presentation.components.BottomNavBar
import com.pragyan.aigymposeestimationapp.presentation.components.TopNavBarWithIcon
import com.pragyan.aigymposeestimationapp.presentation.navigation.LocalExerciseNavState
import com.pragyan.aigymposeestimationapp.theme.GymPoseEstimationTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun UploadScreen(
    modifier: Modifier = Modifier,
    navController: NavController
) {
    var uploadProgress by remember { mutableStateOf(0.5f) }
    var isUploading by remember { mutableStateOf(true) }
    val exercise = LocalExerciseNavState.current.selectedExercise

    val scope = rememberCoroutineScope()

    var selectedFile by remember { mutableStateOf<KmpFile?>(null) }
    val isFileSelected = selectedFile != null

    val pickerLauncher = rememberFilePickerLauncher(
        type = FilePickerFileType.Video,
        selectionMode = FilePickerSelectionMode.Single,
        onResult = { files ->
            selectedFile = files.firstOrNull()
        }
    )

    Scaffold(
        modifier = modifier,
        bottomBar = { BottomNavBar(navController = navController) },
        topBar = { TopNavBarWithIcon(title = exercise?.exerciseName ?: "Upload Exercise", navController = navController)}
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
                                text = if (isFileSelected) "Upload video to analyze the form" else "Tap to choose a video from your library",
                                color = Color(0xFF7A9999),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )

                            Button(
                                onClick = {
                                    if (!isFileSelected) {
                                        pickerLauncher.launch()
                                    } else {
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text(
                                    text = if (!isFileSelected) "Choose Video" else "Upload Video",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }

                            if (isFileSelected) {
                                Button(
                                    onClick = {
                                        selectedFile = null
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    Text(
                                        text = "Cancel",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                }
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
            kotlinx.coroutines.delay(100)
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
    GymPoseEstimationTheme {
        val navController = rememberNavController()
        UploadScreen(navController = navController)
    }
}
