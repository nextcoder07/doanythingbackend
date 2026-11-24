package com.hakdaar.app.core.model

import android.graphics.Rect

/**
 * Short-lived in-memory descriptor for the currently focused text field.
 * Contains accessibility metadata ONLY.
 * NEVER reads or retains existing text content of editable fields.
 */
data class FocusedFieldDescriptor(
    val packageName: String,
    val windowId: Int,
    val viewIdResourceName: String?,
    val className: String,
    val inputType: Int,
    val boundsInScreen: Rect,
    val associatedLabel: String?,
    val isEditable: Boolean,
    val isEnabled: Boolean,
    val isFocusable: Boolean,
    val isVisibleToUser: Boolean,
    val isPassword: Boolean,
    val isSensitiveDetected: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
