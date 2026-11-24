package com.hakdaar.app.domain.filling

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.hakdaar.app.core.model.CatalogField
import com.hakdaar.app.core.model.CatalogPage
import com.hakdaar.app.core.model.CatalogTask
import com.hakdaar.app.domain.session.TaskSessionManager

sealed class FillStepState {
    object Idle : FillStepState()
    data class Highlighting(val field: CatalogField, val instruction: String) : FillStepState()
    data class AwaitingConfirmation(val field: CatalogField, val proposedValue: String) : FillStepState()
    data class Filled(val field: CatalogField, val value: String) : FillStepState()
    data class Error(val message: String) : FillStepState()
    object AllFieldsCompleted : FillStepState()
}

class FillCoordinator {

    fun getNextUnfilledField(task: CatalogTask, page: CatalogPage): CatalogField? {
        val session = TaskSessionManager.activeSession.value ?: return null
        for (fieldId in page.fieldsInOrder) {
            if (!session.isFieldCompleted(fieldId) && !session.isFieldSkipped(fieldId)) {
                return task.fields.find { it.id == fieldId }
            }
        }
        return null
    }

    /**
     * Executes ACTION_SET_TEXT strictly on the revalidated active node.
     * Never uses clipboard, simulated keystrokes, or screen coordinates.
     */
    fun performSetText(node: AccessibilityNodeInfo?, value: String): Boolean {
        if (node == null || !node.isEditable || !node.isEnabled) {
            return false
        }

        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                value
            )
        }

        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }
}
