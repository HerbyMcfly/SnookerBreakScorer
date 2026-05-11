package com.example.snookerbreakscorer   // ← CHANGE THIS to your actual package name!

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.*

data class SavedBreak(
    val score: Int,
    val date: String
)

class SnookerViewModel : ViewModel() {
    var currentBreakScore by mutableIntStateOf(0)
    var isBreakActive by mutableStateOf(false)
    var isListening by mutableStateOf(false)
    var statusMessage by mutableStateOf("Tap 'Create New Break' to start")

    val history = mutableStateListOf<SavedBreak>()

    private var speechRecognizer: SpeechRecognizer? = null

    fun createNewBreak(context: android.content.Context) {
        currentBreakScore = 0
        isBreakActive = true
        statusMessage = "Break started - shout colour or 'Missed'"
        startListening(context)
    }

    fun endBreak() {
        if (currentBreakScore > 0) {
            val dateFormat = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
            val dateStr = dateFormat.format(Date())
            history.add(0, SavedBreak(currentBreakScore, dateStr))
        }
        isBreakActive = false
        isListening = false
        statusMessage = "Break ended with $currentBreakScore points"
        speechRecognizer?.stopListening()
    }

    fun addPoints(points: Int) {
        if (isBreakActive) {
            currentBreakScore += points
            statusMessage = "Added $points → Current break: $currentBreakScore"
        }
    }

    private fun startListening(context: android.content.Context) {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(createRecognitionListener(context))
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            statusMessage = "Error starting voice recognition"
        }
    }

    private fun createRecognitionListener(context: android.content.Context) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
            statusMessage = "🎤 Listening... Speak now!"
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { isListening = false }

        override fun onError(error: Int) {
            isListening = false
            val errorMsg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "No match - try speaking louder/clearer"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timeout - speak sooner"
                else -> "Error $error - restarting"
            }
            statusMessage = errorMsg

            if (isBreakActive) {
                Handler(Looper.getMainLooper()).postDelayed({
                    startListening(context)
                }, 1200)  // longer pause
            }
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: emptyList()

            // Log everything we hear so we can debug
            val allHeard = matches.joinToString(" | ")
            println("DEBUG - Heard: $allHeard")   // Check Logcat for this

            val spokenText = matches.firstOrNull()?.lowercase()?.trim() ?: ""

            statusMessage = "Heard: '$spokenText'"

            val added = when {
                spokenText.contains("red") || spokenText.contains("read") -> { addPoints(1); true }
                spokenText.contains("yellow") -> { addPoints(2); true }
                spokenText.contains("green") -> { addPoints(3); true }
                spokenText.contains("brown") -> { addPoints(4); true }
                spokenText.contains("blue") -> { addPoints(5); true }
                spokenText.contains("pink") -> { addPoints(6); true }
                spokenText.contains("black") || spokenText.contains("blak") -> { addPoints(7); true }
                spokenText.contains("missed") || spokenText.contains("mist") -> { endBreak(); true }
                else -> false
            }

            if (!added && spokenText.isNotEmpty()) {
                statusMessage = "Didn't understand '$spokenText'. Try: Red, Black, Missed..."
            }

            // Restart listening
            if (isBreakActive) {
                Handler(Looper.getMainLooper()).postDelayed({
                    startListening(context)
                }, 1000)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            // Optional: show partial text for better feedback
            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!partial.isNullOrEmpty()) {
                statusMessage = "Hearing... $partial"
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override fun onCleared() {
        speechRecognizer?.destroy()
        super.onCleared()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel: SnookerViewModel = viewModel()
            SnookerApp(viewModel, this)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnookerApp(viewModel: SnookerViewModel, activity: ComponentActivity) {
    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("Snooker Break Scorer 🎱") })
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Current Break", fontSize = 24.sp)

                Text(
                    text = "${viewModel.currentBreakScore}",
                    fontSize = 88.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                if (viewModel.isBreakActive) {
                    Text(
                        text = if (viewModel.isListening) "🎤 Listening for colour..." else "Shout a colour or 'Missed'",
                        fontSize = 20.sp,
                        color = if (viewModel.isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(text = viewModel.statusMessage, fontSize = 18.sp)
                }

                Spacer(modifier = Modifier.height(32.dp))

                if (!viewModel.isBreakActive) {
                    Button(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                                == PackageManager.PERMISSION_GRANTED) {
                                viewModel.createNewBreak(activity)
                            } else {
                                ActivityCompat.requestPermissions(
                                    activity,
                                    arrayOf(Manifest.permission.RECORD_AUDIO),
                                    101
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(70.dp)
                    ) {
                        Text("Create New Break", fontSize = 22.sp)
                    }
                } else {
                    Button(
                        onClick = { viewModel.endBreak() },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("End Break Now")
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Backup tap buttons
                Text("Or tap colour:", fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple("Red", 1, Color(0xFFE53935)),
                        Triple("Yellow", 2, Color(0xFFFFEB3B)),
                        Triple("Green", 3, Color(0xFF43A047)),
                        Triple("Brown", 4, Color(0xFF8D6E63))
                    ).forEach { (name, pts, color) ->
                        Button(
                            onClick = { viewModel.addPoints(pts) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = color,
                                contentColor = if (name == "Yellow") Color.Black else Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text(name, fontSize = 12.sp)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple("Blue", 5, Color(0xFF1E88E5)),
                        Triple("Pink", 6, Color(0xFFF06292)),
                        Triple("Black", 7, Color(0xFF212121))
                    ).forEach { (name, pts, color) ->
                        Button(
                            onClick = { viewModel.addPoints(pts) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = color,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text(name, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                Text("Break History", fontSize = 20.sp)
                if (viewModel.history.isEmpty()) {
                    Text("No saved breaks yet")
                } else {
                    viewModel.history.take(5).forEach { item ->
                        Text("${item.date}  —  ${item.score} points", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}