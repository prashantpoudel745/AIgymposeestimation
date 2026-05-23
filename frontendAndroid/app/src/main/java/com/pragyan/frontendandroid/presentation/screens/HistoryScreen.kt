package com.pragyan.frontendandroid.presentation.screens

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.pragyan.frontendandroid.config.NetworkConfig
import com.pragyan.frontendandroid.data.network.ExerciseApiService
import com.pragyan.frontendandroid.domain.model.ExerciseRecord
import com.pragyan.frontendandroid.presentation.components.BottomNavBar
import com.pragyan.frontendandroid.presentation.components.TopNavBar
import com.pragyan.frontendandroid.presentation.viewmodel.AuthViewModel
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class HistoryViewModel : ViewModel() {
    private val apiService = Retrofit.Builder()
        .baseUrl(NetworkConfig.API_BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ExerciseApiService::class.java)

    private val _historyState = mutableStateOf<HistoryState>(HistoryState.Loading)
    val historyState: State<HistoryState> = _historyState

    fun fetchHistory() {
        viewModelScope.launch {
            _historyState.value = HistoryState.Loading
            try {
                val token = AuthViewModel.accessToken
                if (token == null) {
                    _historyState.value = HistoryState.Error("User not authenticated")
                    return@launch
                }
                val response = apiService.fetchHistory("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    _historyState.value = HistoryState.Success(response.body()!!.records)
                } else {
                    _historyState.value = HistoryState.Error("Failed to fetch history")
                }
            } catch (e: Exception) {
                _historyState.value = HistoryState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

sealed class HistoryState {
    object Loading : HistoryState()
    data class Success(val records: List<ExerciseRecord>) : HistoryState()
    data class Error(val message: String) : HistoryState()
}

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel(), navController: NavController) {
    val state by viewModel.historyState
    var selectedVideoUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.fetchHistory()
    }

    Scaffold(
        bottomBar = { BottomNavBar(navController = navController) },
        topBar = { TopNavBar(title = "Exercise History") }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                is HistoryState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is HistoryState.Error -> Text(
                    text = (state as HistoryState.Error).message,
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.error
                )
                is HistoryState.Success -> {
                    val records = (state as HistoryState.Success).records
                    if (records.isEmpty()) {
                        Text("No history found", modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            items(records) { record ->
                                HistoryItem(record) {
                                    selectedVideoUrl = record.processedVideoUrl
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedVideoUrl != null) {
        VideoPlayerDialog(videoUrl = selectedVideoUrl!!) {
            selectedVideoUrl = null
        }
    }
}

@Composable
fun HistoryItem(record: ExerciseRecord, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = record.exerciseType.replace("_", " ").uppercase(), style = MaterialTheme.typography.titleMedium)
                Text(text = "Reps: ${record.reps} | Side: ${record.side}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Detection: ${record.detectionRate}%", style = MaterialTheme.typography.bodySmall)
                Text(text = record.timestamp.split("T")[0], style = MaterialTheme.typography.labelSmall)
            }
            if (record.processedVideoUrl != null) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play Video")
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerDialog(videoUrl: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = {
                        PlayerView(it).apply {
                            player = exoPlayer
                            useController = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) {
                    Text("Close", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
