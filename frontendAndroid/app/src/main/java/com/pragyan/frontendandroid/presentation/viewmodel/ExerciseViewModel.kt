package com.pragyan.frontendandroid.presentation.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pragyan.frontendandroid.data.network.ExerciseApiService
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
                
                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                
                // Map numeric ID back to string type for backend compatibility
                val exerciseType = when(exerciseId) {
                    1 -> "biceps_curl"
                    2 -> "triceps_pushdown"
                    3 -> "leg_press"
                    4 -> "bench_press" // Note: Backend list: biceps_curl, deadlift, triceps_pushdown, leg_press
                    5 -> "deadlift"
                    else -> "biceps_curl"
                }
                
                val exerciseBody = exerciseType.toRequestBody("text/plain".toMediaTypeOrNull())
                val side = "left"
                val sideBody = side.toRequestBody("text/plain".toMediaTypeOrNull())

                val analyzeResponse = apiService.analyzeVideo(authHeader, exerciseBody, sideBody, body)
                
                if (analyzeResponse.isSuccessful) {
                    Log.d("ExerciseViewModel", "Analysis request successful. Record created by backend.")
                    _exerciseState.value = ExerciseState.Success("Exercise video uploaded and is being processed.")
                } else {
                    _exerciseState.value = ExerciseState.Error("Upload failed: ${analyzeResponse.code()}")
                }
            } catch (e: Exception) {
                Log.e("ExerciseViewModel", "Error uploading video", e)
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
