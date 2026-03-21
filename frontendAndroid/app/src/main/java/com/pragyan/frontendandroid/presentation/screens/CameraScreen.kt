package com.pragyan.frontendandroid.presentation.screens

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.pragyan.frontendandroid.poselandmarker.OverlayView
import com.pragyan.frontendandroid.poselandmarker.PoseLandmarkerHelper
import com.pragyan.frontendandroid.presentation.components.CameraPreview
import com.pragyan.frontendandroid.presentation.navigation.LocalExerciseNavState
import com.pragyan.frontendandroid.presentation.navigation.NavigationScreens
import com.pragyan.frontendandroid.presentation.viewmodel.ExerciseViewModel

@Composable
fun CameraScreen(
    navController: NavController,
    viewModel: ExerciseViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val exercise = LocalExerciseNavState.current.selectedExercise
    
    var isRecording by remember { mutableStateOf(true) }

    val overlayView = remember {
        OverlayView(context, null)
    }

    val poseLandmarkerHelper = remember {
        PoseLandmarkerHelper(
            context = context,
            runningMode = RunningMode.LIVE_STREAM,
            poseLandmarkerHelperListener = object :
                PoseLandmarkerHelper.LandmarkerListener {

                override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
                    if (isRecording) {
                        overlayView.setResults(
                            resultBundle.results.first(),
                            resultBundle.inputImageHeight,
                            resultBundle.inputImageWidth,
                            RunningMode.LIVE_STREAM
                        )
                    }
                }

                override fun onError(error: String, errorCode: Int) {
                    Log.e("PoseError", error)
                }
            }
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(
            poseLandmarkerHelper = poseLandmarkerHelper,
            lifecycleOwner = lifecycleOwner,
            isRecording = isRecording,
            onRecordingFinished = { uri ->
                Log.d("CameraScreen", "Recording finished, processing video in background...")
                exercise?.exerciseId?.let { id ->
                    viewModel.processVideo(context, uri, id)
                }
                // Navigate to dashboard immediately after recording is finished
                navController.navigate(NavigationScreens.DashboardScreen.name) {
                    popUpTo(NavigationScreens.DashboardScreen.name) { inclusive = true }
                }
            }
        )

        if (isRecording) {
            AndroidView(
                factory = { overlayView },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Stop Button
        if (isRecording) {
            FloatingActionButton(
                onClick = { 
                    isRecording = false 
                    Log.d("CameraScreen", "Stop requested")
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                containerColor = Color.Red,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Stop, contentDescription = "Stop Streaming")
            }
        }
    }
}
