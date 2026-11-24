package com.hakdaar.app.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.hakdaar.app.VoiceCaptureActivity
import com.hakdaar.app.core.model.BubbleState
import com.hakdaar.app.core.model.CatalogField
import com.hakdaar.app.data.catalog.TaskCatalogRepository
import com.hakdaar.app.data.preferences.SettingsDataStore
import com.hakdaar.app.domain.filling.FillCoordinator
import com.hakdaar.app.domain.safety.SensitiveScreenDetector
import com.hakdaar.app.domain.session.TaskSessionManager
import com.hakdaar.app.platform.overlay.OverlayController
import com.hakdaar.app.platform.speech.AndroidTextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HakdaarAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var catalogRepo: TaskCatalogRepository
    private lateinit var settingsStore: SettingsDataStore
    private lateinit var sensitiveDetector: SensitiveScreenDetector
    private lateinit var labelResolver: FieldLabelResolver
    private lateinit var selectorMatcher: FieldSelectorMatcher
    private lateinit var treeReader: AccessibilityTreeReader
    private lateinit var fieldTracker: FocusedFieldTracker
    private lateinit var fillCoordinator: FillCoordinator
    private lateinit var overlayController: OverlayController
    private lateinit var tts: AndroidTextToSpeech

    private var activeTargetField: CatalogField? = null

    override fun onCreate() {
        super.onCreate()
        catalogRepo = TaskCatalogRepository(this)
        settingsStore = SettingsDataStore(this)
        sensitiveDetector = SensitiveScreenDetector(catalogRepo.getBlockedTargets())
        labelResolver = FieldLabelResolver()
        selectorMatcher = FieldSelectorMatcher()
        treeReader = AccessibilityTreeReader(labelResolver, selectorMatcher)
        fieldTracker = FocusedFieldTracker(labelResolver, sensitiveDetector)
        fillCoordinator = FillCoordinator()
        overlayController = OverlayController(this)
        tts = AndroidTextToSpeech(this)

        setupOverlayCallbacks()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlayController.showBubble()
    }

    private fun setupOverlayCallbacks() {
        overlayController.onSpeakClicked = {
            // Open visible VoiceCaptureActivity
            val intent = Intent(this, VoiceCaptureActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
        }

        overlayController.onSayToFillClicked = {
            handleSayToFill()
        }

        overlayController.onCancelClicked = {
            TaskSessionManager.clearSession()
            overlayController.hideHighlight()
            overlayController.hideExpandedPanel()
        }

        overlayController.onFillConfirmed = { field, value ->
            executeFill(field, value)
        }

        overlayController.onFieldSkipped = { field ->
            TaskSessionManager.activeSession.value?.markFieldSkipped(field.id)
            overlayController.hideHighlight()
            advanceGuidedFlow()
        }

        overlayController.onHandoverResumeClicked = {
            TaskSessionManager.resumeFromHandover()
            evaluateActiveWindow()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                evaluateActiveWindow()
            }
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                handleViewFocused(event)
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                handleViewClicked(event)
            }
        }
    }

    private fun evaluateActiveWindow() {
        val root = rootInActiveWindow ?: return
        val pkgName = root.packageName?.toString()

        // 1. Check blocked package
        if (sensitiveDetector.isBlockedPackage(pkgName)) {
            TaskSessionManager.triggerHandover("Blocked application: $pkgName")
            overlayController.hideHighlight()
            root.recycle()
            return
        }

        // 2. Check restricted screen cues
        if (sensitiveDetector.containsRestrictedScreenCues(root)) {
            TaskSessionManager.triggerHandover("Sensitive input or authentication screen detected")
            overlayController.hideHighlight()
            root.recycle()
            return
        }

        val session = TaskSessionManager.activeSession.value
        val task = TaskSessionManager.currentTask.value

        if (session != null && task != null) {
            val page = task.pages.firstOrNull { it.id == session.currentPageId }
            if (page != null) {
                val matchesSignature = treeReader.verifyPageSignature(root, page.pageSignature)
                if (matchesSignature) {
                    if (TaskSessionManager.bubbleState.value == BubbleState.WAITING_FOR_PAGE) {
                        advanceGuidedFlow()
                    }
                }
            }
        }

        root.recycle()
    }

    private fun handleViewFocused(event: AccessibilityEvent) {
        val node = event.source ?: return
        try {
            // Update focused field descriptor
            val descriptor = fieldTracker.createDescriptor(node)
            TaskSessionManager.activeSession.value?.currentFieldDescriptor = descriptor

            // If in guided mode and awaiting focus on the active target field
            val targetField = activeTargetField
            val session = TaskSessionManager.activeSession.value
            val task = TaskSessionManager.currentTask.value

            if (targetField != null && session != null && task != null) {
                val page = task.pages.find { it.id == session.currentPageId }
                val selector = page?.selectors?.get(targetField.id)

                if (selector != null && selectorMatcher.matches(selector, node, descriptor?.associatedLabel)) {
                    val proposedValue = session.collectedValues[targetField.id]
                    if (!proposedValue.isNullOrBlank()) {
                        TaskSessionManager.updateBubbleState(BubbleState.AWAITING_FILL_CONFIRMATION)
                        tts.speak("Fill ${targetField.label} with $proposedValue?")
                        overlayController.showFillConfirmationDialog(targetField, proposedValue)
                    }
                }
            }
        } finally {
            node.recycle()
        }
    }

    private fun handleViewClicked(event: AccessibilityEvent) {
        // Safe check on click
        evaluateActiveWindow()
    }

    fun advanceGuidedFlow() {
        val session = TaskSessionManager.activeSession.value ?: return
        val task = TaskSessionManager.currentTask.value ?: return
        val page = task.pages.find { it.id == session.currentPageId } ?: return

        val nextField = fillCoordinator.getNextUnfilledField(task, page)
        if (nextField == null) {
            // All fields filled!
            TaskSessionManager.updateBubbleState(BubbleState.COMPLETED)
            overlayController.hideHighlight()
            tts.speak("All fields completed. Please review and submit the application yourself.")
            return
        }

        activeTargetField = nextField
        val selector = page.selectors[nextField.id]
        val root = rootInActiveWindow
        if (root != null && selector != null) {
            try {
                val targetNode = treeReader.findMatchingFieldNode(root, selector)
                if (targetNode != null) {
                    try {
                        val bounds = Rect()
                        targetNode.getBoundsInScreen(bounds)
                        overlayController.showFieldHighlight(bounds)
                        TaskSessionManager.updateBubbleState(BubbleState.WAITING_FOR_FIELD_FOCUS)
                        tts.speak("Tap ${nextField.label}")
                    } finally {
                        targetNode.recycle()
                    }
                }
            } finally {
                root.recycle()
            }
        }
    }

    private fun executeFill(field: CatalogField, value: String) {
        val root = rootInActiveWindow ?: return
        val session = TaskSessionManager.activeSession.value ?: return
        val task = TaskSessionManager.currentTask.value ?: return
        val page = task.pages.find { it.id == session.currentPageId } ?: return
        val selector = page.selectors[field.id] ?: return

        try {
            val targetNode = treeReader.findMatchingFieldNode(root, selector)
            if (targetNode != null) {
                try {
                    val success = fillCoordinator.performSetText(targetNode, value)
                    if (success) {
                        session.markFieldCompleted(field.id, value)
                        overlayController.hideHighlight()
                        advanceGuidedFlow()
                    } else {
                        tts.speak("Could not fill automatically. Please type manually.")
                    }
                } finally {
                    targetNode.recycle()
                }
            }
        } finally {
            root.recycle()
        }
    }

    private fun handleSayToFill() {
        val root = rootInActiveWindow ?: return
        try {
            val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focusedNode == null) {
                tts.speak("Please tap the field you want to fill first.")
                return
            }

            val descriptor = fieldTracker.createDescriptor(focusedNode)
            focusedNode.recycle()

            if (descriptor == null || descriptor.isSensitiveDetected || descriptor.isPassword) {
                tts.speak("This field is sensitive or cannot be filled.")
                return
            }

            // Launch visible VoiceCaptureActivity in Say-to-Fill mode
            val intent = Intent(this, VoiceCaptureActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("MODE", "SAY_TO_FILL")
            }
            startActivity(intent)
        } finally {
            root.recycle()
        }
    }

    override fun onInterrupt() {
        overlayController.hideBubble()
        tts.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        overlayController.hideBubble()
        tts.shutdown()
        TaskSessionManager.clearSession()
    }
}
