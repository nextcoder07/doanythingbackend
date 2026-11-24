package com.hakdaar.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.hakdaar.app.core.model.BubbleState
import com.hakdaar.app.core.network.HakdaarApiClient
import com.hakdaar.app.data.catalog.TaskCatalogRepository
import com.hakdaar.app.data.preferences.SettingsDataStore
import com.hakdaar.app.domain.matching.MatchResult
import com.hakdaar.app.domain.matching.TaskMatcher
import com.hakdaar.app.domain.session.TaskSessionManager
import com.hakdaar.app.platform.audio.AudioRecorder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class VoiceCaptureActivity : ComponentActivity() {

    private val audioRecorder = AudioRecorder()
    private val apiClient = HakdaarApiClient()
    private lateinit var settingsStore: SettingsDataStore
    private lateinit var catalogRepo: TaskCatalogRepository

    private var mode: String = "GUIDED"

    private val requestAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(this, "Microphone permission is required for voice input", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsStore = SettingsDataStore(this)
        catalogRepo = TaskCatalogRepository(this)
        mode = intent.getStringExtra("MODE") ?: "GUIDED"

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            VoiceCaptureScreen()
        }
    }

    @Composable
    private fun VoiceCaptureScreen() {
        var isRecording by remember { mutableStateOf(false) }
        var isTranscribing by remember { mutableStateOf(false) }
        var isProcessing by remember { mutableStateOf(false) }
        var elapsedSeconds by remember { mutableStateOf(0) }
        var transcript by remember { mutableStateOf("") }
        var audioBytes by remember { mutableStateOf<ByteArray?>(null) }
        var statusMessage by remember { mutableStateOf("Tap 'Record' and speak your details clearly.") }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (mode == "SAY_TO_FILL") "Say to Fill Field" else "Voice Input — Hakdaar",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isRecording) {
                    Text(
                        text = "Recording: $elapsedSeconds / 55s",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFDC2626)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            isRecording = false
                            audioRecorder.stop()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Stop Recording")
                    }
                } else if (isTranscribing || isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(if (isTranscribing) "Transcribing speech..." else "Processing with Gemini...")
                } else if (transcript.isNotBlank()) {
                    OutlinedTextField(
                        value = transcript,
                        onValueChange = { transcript = it },
                        label = { Text("Transcript (Edit if needed)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 6
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                transcript = ""
                                audioBytes?.fill(0)
                                audioBytes = null
                                statusMessage = "Tap 'Record' to record again."
                            },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("Record Again")
                        }

                        Button(
                            onClick = {
                                isProcessing = true
                                handleSendTranscript(transcript)
                            },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("Send Details")
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            isRecording = true
                            elapsedSeconds = 0
                            statusMessage = "Listening... Speak all details clearly in one go."
                            lifecycleScope.launch {
                                TaskSessionManager.updateBubbleState(BubbleState.LISTENING)
                                val recordedWav = audioRecorder.recordAudio { sec ->
                                    elapsedSeconds = sec
                                }
                                audioBytes = recordedWav
                                isRecording = false

                                // Now transcribe
                                isTranscribing = true
                                TaskSessionManager.updateBubbleState(BubbleState.TRANSCRIBING)
                                val lang = settingsStore.languageFlow.first()
                                val result = apiClient.transcribe(recordedWav, lang)
                                isTranscribing = false

                                result.fold(
                                    onSuccess = { text ->
                                        transcript = text
                                        TaskSessionManager.updateBubbleState(BubbleState.TRANSCRIPT_REVIEW)
                                        statusMessage = "Review your spoken words below. Tap 'Send Details' to continue."
                                    },
                                    onFailure = { error ->
                                        statusMessage = "Transcription failed: ${error.localizedMessage ?: "Unknown error"}. You may type details manually."
                                        TaskSessionManager.updateBubbleState(BubbleState.ERROR)
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Record")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = {
                        audioRecorder.stop()
                        audioBytes?.fill(0)
                        finish()
                    }
                ) {
                    Text("Cancel")
                }
            }
        }
    }

    private fun handleSendTranscript(finalTranscript: String) {
        lifecycleScope.launch {
            val lang = settingsStore.languageFlow.first()

            if (mode == "SAY_TO_FILL") {
                val descriptor = TaskSessionManager.activeSession.value?.currentFieldDescriptor
                val inputType = when {
                    descriptor?.inputType ?: 0 and android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS != 0 -> "EMAIL"
                    descriptor?.inputType ?: 0 and android.text.InputType.TYPE_CLASS_PHONE != 0 -> "PHONE"
                    descriptor?.inputType ?: 0 and android.text.InputType.TYPE_CLASS_NUMBER != 0 -> "NUMBER"
                    descriptor?.inputType ?: 0 and android.text.InputType.TYPE_CLASS_DATETIME != 0 -> "DATE_TEXT"
                    else -> "TEXT"
                }

                val normResult = apiClient.normalizeValue(lang, inputType, finalTranscript)
                normResult.fold(
                    onSuccess = { norm ->
                        Toast.makeText(this@VoiceCaptureActivity, "Normalized: ${norm.normalized_value}", Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onFailure = {
                        Toast.makeText(this@VoiceCaptureActivity, "Failed to normalize value", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                )
            } else {
                val session = TaskSessionManager.activeSession.value
                val task = TaskSessionManager.currentTask.value

                if (session == null || task == null) {
                    // Initial task match need
                    val allTasks = catalogRepo.getCatalog().tasks
                    val matcher = TaskMatcher(apiClient)
                    TaskSessionManager.updateBubbleState(BubbleState.MATCHING_TASK)
                    when (val match = matcher.matchTask(finalTranscript, allTasks)) {
                        is MatchResult.Matched -> {
                            TaskSessionManager.startTask(match.task)
                            // Now extract fields
                            extractTaskFields(match.task, finalTranscript, lang)
                        }
                        is MatchResult.Ambiguous -> {
                            Toast.makeText(this@VoiceCaptureActivity, match.question ?: "Ambiguous task. Please select a task manually.", Toast.LENGTH_LONG).show()
                            finish()
                        }
                        is MatchResult.NoMatch -> {
                            Toast.makeText(this@VoiceCaptureActivity, "No matching task found. Try Say-to-Fill mode.", Toast.LENGTH_LONG).show()
                            finish()
                        }
                    }
                } else {
                    // Extract fields for current active task
                    extractTaskFields(task, finalTranscript, lang)
                }
            }
        }
    }

    private suspend fun extractTaskFields(task: com.hakdaar.app.core.model.CatalogTask, transcript: String, lang: String) {
        val session = TaskSessionManager.activeSession.value ?: return
        TaskSessionManager.updateBubbleState(BubbleState.COLLECTING_FIELDS)

        val extractResult = apiClient.extractFields(lang, transcript, task.fields)
        extractResult.fold(
            onSuccess = { dto ->
                dto.values.forEach { ev ->
                    session.collectedValues[ev.field_id] = ev.value
                    session.sourceQuotes[ev.field_id] = ev.source_quote
                    session.missingFieldIds.remove(ev.field_id)
                }
                session.missingFieldIds.clear()
                session.missingFieldIds.addAll(dto.missing_field_ids)
                session.ambiguousFieldIds.clear()
                session.ambiguousFieldIds.addAll(dto.ambiguous_field_ids)

                TaskSessionManager.updateBubbleState(BubbleState.FIELD_REVIEW)
                Toast.makeText(this@VoiceCaptureActivity, "Extracted ${dto.values.size} fields.", Toast.LENGTH_SHORT).show()
                finish()
            },
            onFailure = { err ->
                Toast.makeText(this@VoiceCaptureActivity, "Field extraction failed: ${err.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        )
    }

    override fun onPause() {
        super.onPause()
        // If app loses foreground while recording, discard audio immediately
        audioRecorder.stop()
        TaskSessionManager.clearAudioBuffer()
    }
}
