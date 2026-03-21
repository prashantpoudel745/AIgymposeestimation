package com.pragyan.frontendandroid.presentation.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pragyan.frontendandroid.data.network.ExerciseApiService
import com.pragyan.frontendandroid.domain.model.ExerciseRecordRequest
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File

class ExerciseViewModel : ViewModel() {

    private val apiService = Retrofit.Builder()
        .baseUrl("http://192.168.1.55:8000/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ExerciseApiService::class.java)

    private val _exerciseState = mutableStateOf<ExerciseState>(ExerciseState.Idle)
    val exerciseState: State<ExerciseState> = _exerciseState

    fun processVideo(context: Context, videoUri: Uri, exerciseId: Int) {
        viewModelScope.launch {
            _exerciseState.value = ExerciseState.Processing
            try {
                val token = AuthViewModel.accessToken
                if (token == null) {
                    _exerciseState.value = ExerciseState.Error("User not authenticated")
                    return@launch
                }

                val authHeader = "Bearer $token"
                val file = getFileFromUri(context, videoUri)
                val requestFile = file.asRequestBody("video/mp4".toMediaTypeOrNull())
                
                // Part name must be "file" to match backend @app.post("/analyze-video") async def analyze_video(..., file: UploadFile = File(...))
                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                
                val exerciseType = "biceps_curl" // Should ideally be dynamic based on exerciseId
                val exerciseBody = exerciseType.toRequestBody("text/plain".toMediaTypeOrNull())
                val side = "left"
                val sideBody = side.toRequestBody("text/plain".toMediaTypeOrNull())

                val analyzeResponse = apiService.analyzeVideo(authHeader, exerciseBody, sideBody, body)
                
                if (analyzeResponse.isSuccessful) {
                    // Extract data from headers as defined in backend
                    val headers = analyzeResponse.headers()
                    val reps = headers.get("X-Reps-Completed")?.toIntOrNull() ?: 0
                    val detectionRateStr = headers.get("X-Pose-Detection-Rate")?.replace("%", "") ?: "0"
                    val detectionRate = detectionRateStr.toFloatOrNull() ?: 0f
                    
                    Log.d("ExerciseViewModel", "Analysis results from headers: Reps=$reps, Rate=$detectionRate")

                    val recordRequest = ExerciseRecordRequest(
                        exerciseType = exerciseType,
                        side = side,
                        reps = reps,
                        detectionRate = detectionRate
                    )
                    
                    val saveResponse = apiService.saveExerciseRecord(authHeader, recordRequest)
                    if (saveResponse.isSuccessful) {
                        _exerciseState.value = ExerciseState.Success("Exercise analyzed and recorded successfully!")
                    } else {
                        _exerciseState.value = ExerciseState.Error("Failed to save record: ${saveResponse.message()}")
                    }
                } else {
                    _exerciseState.value = ExerciseState.Error("Analysis failed: ${analyzeResponse.code()} ${analyzeResponse.message()}")
                }
            } catch (e: Exception) {
                Log.e("ExerciseViewModel", "Error processing video", e)
                _exerciseState.value = ExerciseState.Error(e.message ?: "An unknown error occurred")
            }
        }
    }

    private fun getFileFromUri(context: Context, uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri)
        val file = File(context.cacheDir, "upload_video_${System.currentTimeMillis()}.mp4")
        file.outputStream().use { outputStream ->
            inputStream?.copyTo(outputStream)
        }
        return file
    }
    
    fun resetState() {
        _exerciseState.value = ExerciseState.Idle
    }
}

sealed class ExerciseState {
    object Idle : ExerciseState()
    object Processing : ExerciseState()
    data class Success(val message: String) : ExerciseState()
    data class Error(val message: String) : ExerciseState()
}
