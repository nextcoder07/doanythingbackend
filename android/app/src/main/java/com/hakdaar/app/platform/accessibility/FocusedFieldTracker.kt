package com.hakdaar.app.platform.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.hakdaar.app.core.model.FocusedFieldDescriptor
import com.hakdaar.app.domain.safety.SensitiveScreenDetector

class FocusedFieldTracker(
    private val labelResolver: FieldLabelResolver,
    private val sensitiveDetector: SensitiveScreenDetector
) {

    fun createDescriptor(node: AccessibilityNodeInfo?): FocusedFieldDescriptor? {
        if (node == null) return null

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        val label = labelResolver.resolveLabel(node)
        val isSensitive = sensitiveDetector.isSensitiveField(node) ||
                sensitiveDetector.containsSensitiveKeyword(label)

        return FocusedFieldDescriptor(
            packageName = node.packageName?.toString() ?: "",
            windowId = node.windowId,
            viewIdResourceName = node.viewIdResourceName,
            className = node.className?.toString() ?: "",
            inputType = node.inputType,
            boundsInScreen = bounds,
            associatedLabel = label,
            isEditable = node.isEditable,
            isEnabled = node.isEnabled,
            isFocusable = node.isFocusable,
            isVisibleToUser = node.isVisibleToUser,
            isPassword = node.isPassword,
            isSensitiveDetected = isSensitive
        )
    }

    /**
     * Compares the fingerprint of the current focused node with the stored descriptor.
     * Prevents filling into an altered or replaced field.
     */
    fun matchesFingerprint(descriptor: FocusedFieldDescriptor, currentNode: AccessibilityNodeInfo?): Boolean {
        if (currentNode == null) return false

        if (currentNode.packageName?.toString() != descriptor.packageName) return false
        if (currentNode.windowId != descriptor.windowId) return false
        if (currentNode.className?.toString() != descriptor.className) return false
        if (currentNode.inputType != descriptor.inputType) return false
        if (currentNode.isPassword != descriptor.isPassword) return false

        descriptor.viewIdResourceName?.let { expectedId ->
            if (currentNode.viewIdResourceName != expectedId) return false
        }

        return true
    }
}
