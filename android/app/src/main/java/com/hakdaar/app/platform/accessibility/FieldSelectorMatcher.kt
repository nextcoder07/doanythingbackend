package com.hakdaar.app.platform.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import com.hakdaar.app.core.model.FieldSelector

class FieldSelectorMatcher {

    fun matches(selector: FieldSelector, node: AccessibilityNodeInfo, resolvedLabel: String?): Boolean {
        if (!node.isEditable) return false

        // Check labelExact match against resolved label, hint or contentDescription
        val labelMatches = resolvedLabel?.equals(selector.labelExact, ignoreCase = true) == true ||
                node.hintText?.toString()?.equals(selector.labelExact, ignoreCase = true) == true ||
                node.contentDescription?.toString()?.equals(selector.labelExact, ignoreCase = true) == true

        if (!labelMatches) {
            // Also check if viewIdResourceName matches selector label key
            val viewId = node.viewIdResourceName?.substringAfter(":id/") ?: ""
            val selectorKey = selector.labelExact.lowercase().replace("'", "").replace(" ", "_")
            if (!viewId.contains(selectorKey, ignoreCase = true)) {
                return false
            }
        }

        return true
    }
}
