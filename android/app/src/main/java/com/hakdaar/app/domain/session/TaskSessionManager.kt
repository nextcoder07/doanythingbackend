package com.hakdaar.app.domain.session

import com.hakdaar.app.core.model.BubbleState
import com.hakdaar.app.core.model.CatalogField
import com.hakdaar.app.core.model.CatalogTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-level singleton managing the in-memory ActiveTaskSession and BubbleState.
 * Guarantees zero persistent storage of personal data.
 */
object TaskSessionManager {

    private val _bubbleState = MutableStateFlow(BubbleState.IDLE)
    val bubbleState: StateFlow<BubbleState> = _bubbleState.asStateFlow()

    private val _activeSession = MutableStateFlow<ActiveTaskSession?>(null)
    val activeSession: StateFlow<ActiveTaskSession?> = _activeSession.asStateFlow()

    private val _currentTask = MutableStateFlow<CatalogTask?>(null)
    val currentTask: StateFlow<CatalogTask?> = _currentTask.asStateFlow()

    // Transient audio buffer (wiped with fill(0))
    private var audioBuffer: ByteArray? = null

    // Handover reason if paused on sensitive screen
    private val _handoverReason = MutableStateFlow<String?>(null)
    val handoverReason: StateFlow<String?> = _handoverReason.asStateFlow()

    fun updateBubbleState(newState: BubbleState) {
        _bubbleState.value = newState
    }

    fun startTask(task: CatalogTask) {
        // Clear previous session if any
        clearSession()

        _currentTask.value = task
        val session = ActiveTaskSession(
            taskId = task.id,
            currentPageId = task.pages.firstOrNull()?.id
        )
        // Initialize missing fields list with required non-sensitive fields
        session.missingFieldIds.addAll(
            task.fields.filter { it.required && !it.sensitive && !it.handoverOnly }.map { it.id }
        )
        _activeSession.value = session
        _bubbleState.value = BubbleState.COLLECTING_FIELDS
    }

    fun storeAudioBuffer(bytes: ByteArray) {
        clearAudioBuffer()
        audioBuffer = bytes
    }

    fun getAudioBuffer(): ByteArray? = audioBuffer

    fun clearAudioBuffer() {
        audioBuffer?.let { buf ->
            buf.fill(0) // Zero out physical byte array in RAM before dropping reference
        }
        audioBuffer = null
    }

    fun triggerHandover(reason: String) {
        clearAudioBuffer()
        _handoverReason.value = reason
        _bubbleState.value = BubbleState.HANDOVER
    }

    fun resumeFromHandover() {
        _handoverReason.value = null
        if (_activeSession.value != null) {
            _bubbleState.value = BubbleState.WAITING_FOR_PAGE
        } else {
            _bubbleState.value = BubbleState.IDLE
        }
    }

    fun clearSession() {
        clearAudioBuffer()
        _activeSession.value?.wipe()
        _activeSession.value = null
        _currentTask.value = null
        _handoverReason.value = null
        _bubbleState.value = BubbleState.IDLE
    }
}
