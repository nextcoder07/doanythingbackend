package com.hakdaar.app.domain.session

import com.hakdaar.app.core.model.FocusedFieldDescriptor
import java.util.UUID

/**
 * Single in-memory active task session.
 * Exists ONLY in RAM during an active interaction.
 * Wiped completely on task finish, cancellation, timeout, error, or process termination.
 * NEVER serialized to disk, Room, SharedPreferences, or cloud database.
 */
class ActiveTaskSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val taskId: String,
    var currentPageId: String? = null,
    val collectedValues: MutableMap<String, String> = mutableMapOf(),
    val sourceQuotes: MutableMap<String, String> = mutableMapOf(),
    val missingFieldIds: MutableList<String> = mutableListOf(),
    val ambiguousFieldIds: MutableList<String> = mutableListOf(),
    val completedFieldIds: MutableSet<String> = mutableSetOf(),
    val skippedFieldIds: MutableSet<String> = mutableSetOf(),
    var currentFieldDescriptor: FocusedFieldDescriptor? = null,
    var activeTranscript: String? = null,
    val startedAt: Long = System.currentTimeMillis()
) {
    fun wipe() {
        collectedValues.clear()
        sourceQuotes.clear()
        missingFieldIds.clear()
        ambiguousFieldIds.clear()
        completedFieldIds.clear()
        skippedFieldIds.clear()
        currentFieldDescriptor = null
        activeTranscript = null
    }

    fun isFieldCompleted(fieldId: String): Boolean = completedFieldIds.contains(fieldId)
    fun isFieldSkipped(fieldId: String): Boolean = skippedFieldIds.contains(fieldId)

    fun markFieldCompleted(fieldId: String, value: String) {
        collectedValues[fieldId] = value
        completedFieldIds.add(fieldId)
        missingFieldIds.remove(fieldId)
        ambiguousFieldIds.remove(fieldId)
    }

    fun markFieldSkipped(fieldId: String) {
        skippedFieldIds.add(fieldId)
        missingFieldIds.remove(fieldId)
        ambiguousFieldIds.remove(fieldId)
    }
}
