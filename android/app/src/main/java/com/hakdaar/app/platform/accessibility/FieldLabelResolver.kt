package com.hakdaar.app.platform.accessibility

import android.view.accessibility.AccessibilityNodeInfo

class FieldLabelResolver {

    /**
     * Resolves the associated label for an input node using:
     * 1. labeledBy node reference if available
     * 2. hintText
     * 3. contentDescription
     * 4. View ID resource name
     * 5. Preceding sibling label text
     */
    fun resolveLabel(node: AccessibilityNodeInfo): String? {
        // 1. Check labeledBy
        node.labeledBy?.let { labelNode ->
            val text = labelNode.text?.toString()
            labelNode.recycle()
            if (!text.isNullOrBlank()) return text.trim()
        }

        // 2. Check hint text
        node.hintText?.toString()?.takeIf { it.isNotBlank() }?.let { return it.trim() }

        // 3. Check content description
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { return it.trim() }

        // 4. Check preceding sibling node in parent
        val parent = node.parent
        if (parent != null) {
            try {
                for (i in 0 until parent.childCount) {
                    val child = parent.getChild(i) ?: continue
                    try {
                        if (child == node && i > 0) {
                            val prev = parent.getChild(i - 1)
                            if (prev != null) {
                                try {
                                    val prevText = prev.text?.toString()
                                    if (!prevText.isNullOrBlank()) return prevText.trim()
                                } finally {
                                    prev.recycle()
                                }
                            }
                        }
                    } finally {
                        child.recycle()
                    }
                }
            } finally {
                parent.recycle()
            }
        }

        // 5. Clean resource ID if readable (e.g. "child_name" -> "Child's full name")
        node.viewIdResourceName?.let { idName ->
            val simpleName = idName.substringAfter(":id/").substringAfter("/")
            if (simpleName.isNotBlank()) return simpleName.replace("_", " ")
        }

        return null
    }
}
