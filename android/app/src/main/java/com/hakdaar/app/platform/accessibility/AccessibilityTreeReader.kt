package com.hakdaar.app.platform.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import com.hakdaar.app.core.model.FieldSelector
import com.hakdaar.app.core.model.PageSignature

class AccessibilityTreeReader(
    private val labelResolver: FieldLabelResolver,
    private val selectorMatcher: FieldSelectorMatcher
) {

    /**
     * Verifies that the current screen matches the expected PageSignature:
     * Heading text and required visible labels must be present.
     */
    fun verifyPageSignature(root: AccessibilityNodeInfo, signature: PageSignature): Boolean {
        var foundHeading = false
        val missingLabels = signature.requiredVisibleLabels.toMutableSet()

        fun search(node: AccessibilityNodeInfo) {
            val text = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")
            if (text.contains(signature.headingExact, ignoreCase = true)) {
                foundHeading = true
            }

            val matched = missingLabels.filter { label -> text.contains(label, ignoreCase = true) }
            missingLabels.removeAll(matched.toSet())

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    search(child)
                } finally {
                    child.recycle()
                }
            }
        }

        search(root)
        return foundHeading && missingLabels.isEmpty()
    }

    /**
     * Finds the single unique editable node matching the catalog FieldSelector.
     * Returns null if 0 or multiple matching nodes exist (fail safe).
     */
    fun findMatchingFieldNode(root: AccessibilityNodeInfo, selector: FieldSelector): AccessibilityNodeInfo? {
        val matches = mutableListOf<AccessibilityNodeInfo>()

        fun search(node: AccessibilityNodeInfo) {
            if (node.isEditable && node.isVisibleToUser && node.isEnabled && !node.isPassword) {
                val label = labelResolver.resolveLabel(node)
                if (selectorMatcher.matches(selector, node, label)) {
                    matches.add(AccessibilityNodeInfo.obtain(node))
                }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    search(child)
                } finally {
                    child.recycle()
                }
            }
        }

        search(root)

        return if (matches.size == 1) {
            matches.first()
        } else {
            // Clean up any duplicated nodes
            matches.forEach { it.recycle() }
            null
        }
    }
}
