package com.pragyan.frontendandroid.presentation.screens

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.pragyan.frontendandroid.poselandmarker.OverlayView
import com.pragyan.frontendandroid.poselandmarker.PoseLandmarkerHelper
import com.pragyan.frontendandroid.presentation.components.CameraPreview

@Composable
fun CameraScreen() {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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
                    overlayView.setResults(
                        resultBundle.results.first(),
                        resultBundle.inputImageHeight,
                        resultBundle.inputImageWidth,
                        RunningMode.LIVE_STREAM
                    )
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
    }


    Box(modifier = Modifier.fillMaxSize()) {

        CameraPreview(
            poseLandmarkerHelper = poseLandmarkerHelper,
            lifecycleOwner = lifecycleOwner
        )

        AndroidView(
            factory = { overlayView },
            modifier = Modifier.fillMaxSize()
        )
    }
}

