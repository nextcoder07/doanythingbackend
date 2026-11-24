package com.hakdaar.app.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo

class ActiveWindowInspector(private val service: AccessibilityService) {

    fun getActiveRootNode(): AccessibilityNodeInfo? {
        return service.rootInActiveWindow
    }

    fun getCurrentPackageName(): String? {
        val root = getActiveRootNode() ?: return null
        val pkg = root.packageName?.toString()
        root.recycle()
        return pkg
    }
}
