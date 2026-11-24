package com.hakdaar.app.domain.safety

import android.text.InputType
import android.view.accessibility.AccessibilityNodeInfo
import com.hakdaar.app.core.model.BlockedTargetsConfig

class SensitiveScreenDetector(private val config: BlockedTargetsConfig) {

    /**
     * Checks if a target package is on the known banking/payment blocklist.
     */
    fun isBlockedPackage(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val lower = packageName.lowercase()
        return config.blockedPackages.any { blocked -> lower.contains(blocked.lowercase()) }
    }

    /**
     * Checks if text or URL contains a known banking/payment domain.
     */
    fun isBlockedDomain(textOrUrl: String?): Boolean {
        if (textOrUrl.isNullOrBlank()) return false
        val lower = textOrUrl.lowercase()
        return config.blockedDomains.any { domain -> lower.contains(domain.lowercase()) }
    }

    /**
     * Determines whether an AccessibilityNodeInfo represents a sensitive field.
     * Fail closed: If password flag is set, or if label/hint/content description
     * contains sensitive English or Hindi terms.
     */
    fun isSensitiveField(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        // 1. Check isPassword property
        if (node.isPassword) return true

        // 2. Check inputType password variations
        val inputType = node.inputType
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val isPasswordVariation = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD

        if (isPasswordVariation) return true

        // 3. Check text, label, hint, and content description for sensitive terms
        val combinedText = buildString {
            node.text?.let { append(it).append(" ") }
            node.contentDescription?.let { append(it).append(" ") }
            node.hintText?.let { append(it).append(" ") }
            node.viewIdResourceName?.let { append(it).append(" ") }
        }.lowercase()

        if (containsSensitiveKeyword(combinedText)) return true

        return false
    }

    /**
     * Checks if any string contains sensitive terms in English or Hindi.
     */
    fun containsSensitiveKeyword(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val lower = text.lowercase()

        // Check English sensitive terms
        for (term in config.englishSensitiveTerms) {
            val pattern = "\\b${Regex.escape(term.lowercase())}\\b".toRegex()
            if (pattern.containsMatchIn(lower) || lower.contains(term.lowercase())) {
                return true
            }
        }

        // Check Hindi sensitive terms
        for (term in config.hindiSensitiveTerms) {
            if (lower.contains(term.lowercase())) {
                return true
            }
        }

        return false
    }

    /**
     * Checks if the overall screen/window contains cues of payment, authentication, or login.
     */
    fun containsRestrictedScreenCues(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false
        return traverseCheckSensitive(rootNode)
    }

    private fun traverseCheckSensitive(node: AccessibilityNodeInfo): Boolean {
        if (isSensitiveField(node)) return true

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (traverseCheckSensitive(child)) return true
            } finally {
                child.recycle()
            }
        }
        return false
    }
}
