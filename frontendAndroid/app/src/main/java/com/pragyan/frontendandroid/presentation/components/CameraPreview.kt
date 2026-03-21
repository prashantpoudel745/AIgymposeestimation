package com.pragyan.frontendandroid.presentation.components

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.pragyan.frontendandroid.poselandmarker.PoseLandmarkerHelper
import java.io.File
import java.util.concurrent.Executors

@Composable
fun CameraPreview(
    poseLandmarkerHelper: PoseLandmarkerHelper,
    lifecycleOwner: LifecycleOwner,
    isRecording: Boolean,
    onRecordingFinished: (Uri) -> Unit
) {
    val context = LocalContext.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val cameraProviderState = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val videoCaptureState = remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    val recordingState = remember { mutableStateOf<Recording?>(null) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_START
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                cameraProviderState.value = cameraProvider

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                imageAnalyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                    poseLandmarkerHelper.detectLiveStream(imageProxy)
                    imageProxy.close()
                }

                val recorder = Recorder.Builder()
                    .setExecutor(ContextCompat.getMainExecutor(ctx))
                    .build()
                val videoCapture = VideoCapture.withOutput(recorder)
                videoCaptureState.value = videoCapture

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalyzer,
                        videoCapture
                    )
                } catch (exc: Exception) {
                    Log.e("CameraPreview", "Use case binding failed", exc)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )

    // Handle Start/Stop Recording
    LaunchedEffect(isRecording, videoCaptureState.value) {
        val videoCapture = videoCaptureState.value
        if (isRecording) {
            if (videoCapture != null && recordingState.value == null) {
                val file = File(context.cacheDir, "temp_video_${System.currentTimeMillis()}.mp4")
                val outputOptions = FileOutputOptions.Builder(file).build()

                recordingState.value = videoCapture.output
                    .prepareRecording(context, outputOptions)
                    .start(ContextCompat.getMainExecutor(context)) { event ->
                        if (event is VideoRecordEvent.Finalize) {
                            if (!event.hasError()) {
                                Log.d("CameraPreview", "Video saved to: ${file.absolutePath}")
                                onRecordingFinished(Uri.fromFile(file))
                            } else {
                                Log.e("CameraPreview", "Video recording error: ${event.error}")
                            }
                        }
                    }
                Log.d("CameraPreview", "Recording started")
            }
        } else {
            recordingState.value?.let {
                Log.d("CameraPreview", "Stopping recording...")
                it.stop()
                recordingState.value = null
            }
            // Unbind everything to stop the stream
            cameraProviderState.value?.unbindAll()
        }
    }
}
