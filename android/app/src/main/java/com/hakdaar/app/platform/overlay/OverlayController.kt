package com.hakdaar.app.platform.overlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.hakdaar.app.core.model.BubbleState
import com.hakdaar.app.core.model.CatalogField
import com.hakdaar.app.domain.session.TaskSessionManager

class OverlayController(private val service: AccessibilityService) {

    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var bubbleView: View? = null
    private var expandedPanelView: View? = null
    private var highlightView: View? = null

    private var bubbleLayoutParams: WindowManager.LayoutParams? = null

    // Callbacks to service
    var onSpeakClicked: (() -> Unit)? = null
    var onSayToFillClicked: (() -> Unit)? = null
    var onCancelClicked: (() -> Unit)? = null
    var onFillConfirmed: ((CatalogField, String) -> Unit)? = null
    var onFieldSkipped: ((CatalogField) -> Unit)? = null
    var onHandoverResumeClicked: (() -> Unit)? = null

    @SuppressLint("ClickableViewAccessibility")
    fun showBubble() {
        if (bubbleView != null) return

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 200
        }
        bubbleLayoutParams = layoutParams

        val container = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 12, 16, 12)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E3A8A"))
                cornerRadius = 32f
                setStroke(3, Color.parseColor("#60A5FA"))
            }
            elevation = 16f
        }

        // Status indicator dot
        val dot = View(service).apply {
            val dotParams = LinearLayout.LayoutParams(16, 16).apply {
                gravity = Gravity.CENTER_VERTICAL
                setMargins(0, 0, 12, 0)
            }
            this.layoutParams = dotParams
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#34D399")) // Green for ready
                cornerRadius = 8f
            }
        }

        val label = TextView(service).apply {
            text = "Hakdaar"
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        container.addView(dot)
        container.addView(label)

        // Dragging & Click logic
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = false

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isClick = false
                    }
                    layoutParams.x = initialX + dx
                    layoutParams.y = initialY + dy
                    windowManager.updateViewLayout(container, layoutParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        toggleExpandedPanel()
                    }
                    true
                }
                else -> false
            }
        }

        bubbleView = container
        windowManager.addView(container, layoutParams)
    }

    fun hideBubble() {
        hideExpandedPanel()
        hideHighlight()
        bubbleView?.let {
            windowManager.removeView(it)
            bubbleView = null
        }
    }

    fun toggleExpandedPanel() {
        if (expandedPanelView != null) {
            hideExpandedPanel()
        } else {
            showExpandedPanel()
        }
    }

    private fun showExpandedPanel() {
        if (expandedPanelView != null) return

        val params = WindowManager.LayoutParams(
            (service.resources.displayMetrics.widthPixels * 0.9).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            y = (bubbleLayoutParams?.y ?: 200) + 100
        }

        val panel = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A")) // Dark background for high contrast
                cornerRadius = 20f
                setStroke(2, Color.parseColor("#38BDF8"))
            }
            elevation = 20f
        }

        val state = TaskSessionManager.bubbleState.value
        val task = TaskSessionManager.currentTask.value
        val session = TaskSessionManager.activeSession.value

        // Title
        val titleText = TextView(service).apply {
            text = task?.title ?: "Hakdaar Co-Pilot"
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        panel.addView(titleText)

        // Instruction / Status text
        val infoText = TextView(service).apply {
            text = when (state) {
                BubbleState.HANDOVER -> "Looking away for sensitive step: ${TaskSessionManager.handoverReason.value ?: ""}"
                BubbleState.COLLECTING_FIELDS -> "Please speak your details."
                BubbleState.WAITING_FOR_PAGE -> "Open the demo form in your browser."
                BubbleState.WAITING_FOR_FIELD_FOCUS -> "Tap the highlighted field."
                BubbleState.AWAITING_FILL_CONFIRMATION -> "Review value and confirm to fill."
                else -> "Hakdaar is ready."
            }
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 14f
            setPadding(0, 8, 0, 16)
        }
        panel.addView(infoText)

        // Buttons row
        val buttonRow = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        if (state == BubbleState.HANDOVER) {
            val resumeBtn = Button(service).apply {
                text = "I am done — Resume"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#059669"))
                setOnClickListener {
                    hideExpandedPanel()
                    onHandoverResumeClicked?.invoke()
                }
            }
            buttonRow.addView(resumeBtn)
        } else {
            val speakBtn = Button(service).apply {
                text = "Speak"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#2563EB"))
                setOnClickListener {
                    hideExpandedPanel()
                    onSpeakClicked?.invoke()
                }
            }
            val sayToFillBtn = Button(service).apply {
                text = "Fill This"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#7C3AED"))
                setOnClickListener {
                    hideExpandedPanel()
                    onSayToFillClicked?.invoke()
                }
            }
            val cancelBtn = Button(service).apply {
                text = "Cancel"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#DC2626"))
                setOnClickListener {
                    hideExpandedPanel()
                    onCancelClicked?.invoke()
                }
            }

            buttonRow.addView(speakBtn)
            buttonRow.addView(sayToFillBtn)
            buttonRow.addView(cancelBtn)
        }

        panel.addView(buttonRow)
        expandedPanelView = panel
        windowManager.addView(panel, params)
    }

    fun hideExpandedPanel() {
        expandedPanelView?.let {
            windowManager.removeView(it)
            expandedPanelView = null
        }
    }

    /**
     * Draws non-blocking rectangular highlight around field bounds.
     * Uses FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE so user can tap right through it.
     */
    fun showFieldHighlight(bounds: Rect) {
        hideHighlight()

        val params = WindowManager.LayoutParams(
            bounds.width() + 16,
            bounds.height() + 16,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bounds.left - 8
            y = bounds.top - 8
        }

        val highlight = View(service).apply {
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(6, Color.parseColor("#F59E0B")) // Bright amber highlight
                cornerRadius = 8f
            }
        }

        highlightView = highlight
        windowManager.addView(highlight, params)
    }

    fun hideHighlight() {
        highlightView?.let {
            windowManager.removeView(it)
            highlightView = null
        }
    }

    fun showFillConfirmationDialog(field: CatalogField, proposedValue: String) {
        hideExpandedPanel()

        val params = WindowManager.LayoutParams(
            (service.resources.displayMetrics.widthPixels * 0.9).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val dialog = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 24, 28, 24)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 16f
                setStroke(3, Color.parseColor("#2563EB"))
            }
            elevation = 24f
        }

        val question = TextView(service).apply {
            text = "Fill ${field.label}?"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val valueDisplay = TextView(service).apply {
            text = proposedValue
            setTextColor(Color.parseColor("#1D4ED8"))
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 12, 0, 16)
        }

        val btnRow = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val fillBtn = Button(service).apply {
            text = "Fill"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2563EB"))
            setOnClickListener {
                windowManager.removeView(dialog)
                onFillConfirmed?.invoke(field, proposedValue)
            }
        }

        val skipBtn = Button(service).apply {
            text = "Skip"
            setTextColor(Color.parseColor("#475569"))
            setBackgroundColor(Color.parseColor("#E2E8F0"))
            setOnClickListener {
                windowManager.removeView(dialog)
                onFieldSkipped?.invoke(field)
            }
        }

        val cancelBtn = Button(service).apply {
            text = "Cancel"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#DC2626"))
            setOnClickListener {
                windowManager.removeView(dialog)
                onCancelClicked?.invoke()
            }
        }

        btnRow.addView(skipBtn)
        btnRow.addView(cancelBtn)
        btnRow.addView(fillBtn)

        dialog.addView(question)
        dialog.addView(valueDisplay)
        dialog.addView(btnRow)

        windowManager.addView(dialog, params)
    }
}
