package com.screentextcopier

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.PersistableBundle
import android.preference.PreferenceManager
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * Native AccessibilityService providing on-demand screen text extraction.
 *
 * PRIVACY & SECURITY (Zero Network):
 * - No internet permission exists in this application.
 * - Passive event subscription is strictly minimized (eventTypes = 0).
 *   [onAccessibilityEvent] is strictly EMPTY and no background monitoring occurs.
 * - Screen hierarchy is accessed ONLY when the user explicitly taps the floating button.
 */
class TextCopierAccessibilityService : AccessibilityService(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        const val PREF_FLOATING_BUTTON_ENABLED = "pref_floating_button_enabled"

        @Volatile
        var isServiceRunning = false
            private set
    }

    private var floatingWidgetManager: FloatingWidgetManager? = null
    private lateinit var prefs: SharedPreferences

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true

        // Minimize accessibility event subscription to zero:
        // We do not listen to passive events in background.
        try {
            val info = serviceInfo
            if (info != null) {
                info.eventTypes = 0
                serviceInfo = info
            }
        } catch (_: Exception) {
        }

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.registerOnSharedPreferenceChangeListener(this)

        floatingWidgetManager = FloatingWidgetManager(
            context = this,
            windowManager = windowManager,
            prefs = prefs,
            onClick = { extractAndCopyScreenText() },
            onLongPress = { handleLongPress() }
        )

        val isEnabled = prefs.getBoolean(PREF_FLOATING_BUTTON_ENABLED, true)
        if (isEnabled) {
            floatingWidgetManager?.show()
        }
    }

    /**
     * Strictly empty implementation to ensure zero background tracking or telemetry.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op: privacy by design. We do not listen to passive events.
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == PREF_FLOATING_BUTTON_ENABLED) {
            val enabled = prefs.getBoolean(PREF_FLOATING_BUTTON_ENABLED, true)
            if (enabled) {
                floatingWidgetManager?.show()
            } else {
                floatingWidgetManager?.hide()
            }
        }
    }

    /**
     * Traverses the active window hierarchy and copies all visible text into Clipboard.
     */
    private fun extractAndCopyScreenText() {
        val rawPairs = mutableListOf<Pair<CharSequence?, CharSequence?>>()

        fun extractFromNode(node: AccessibilityNodeInfo?) {
            if (node == null) return
            try {
                if (!node.isVisibleToUser) {
                    return
                }

                val text = TextExtractor.cleanText(node.text)
                val desc = TextExtractor.cleanText(node.contentDescription)

                // Only record pairs that contain actual usable text or description
                if (text != null || desc != null) {
                    rawPairs.add(text to desc)
                }

                val childCount = node.childCount
                for (i in 0 until childCount) {
                    val child = node.getChild(i) ?: continue
                    try {
                        extractFromNode(child)
                    } finally {
                        child.recycle()
                    }
                }
            } catch (_: Exception) {
                // Ignore transient accessibility node access errors
            }
        }

        // 1. Primary: inspect rootInActiveWindow
        val root = rootInActiveWindow
        if (root != null) {
            try {
                extractFromNode(root)
            } finally {
                root.recycle()
            }
        }

        // 2. Fallback / Multi-window: if no usable text was collected from root, iterate through interactive windows
        if (rawPairs.isEmpty()) {
            try {
                val currentWindows = windows
                for (window in currentWindows) {
                    val winRoot = window.root ?: continue
                    try {
                        extractFromNode(winRoot)
                    } finally {
                        winRoot.recycle()
                    }
                }
            } catch (_: Exception) {
            }
        }

        val processedItems = TextExtractor.processExtractedItems(rawPairs)

        if (processedItems.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_no_text), Toast.LENGTH_SHORT).show()
            return
        }

        val joinedText = TextExtractor.formatClipboardText(processedItems)

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("ScreenTextCopier", joinedText)

        // Protect sensitive text on Android 13+ (API 33+) from system overlay leaks
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }

        clipboard.setPrimaryClip(clip)

        val count = processedItems.size
        val message = getString(R.string.toast_copied_format, count)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Long press handler: opens MainActivity so user can easily toggle or configure the widget.
     */
    private fun handleLongPress() {
        Toast.makeText(this, getString(R.string.toast_long_press), Toast.LENGTH_SHORT).show()

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        if (::prefs.isInitialized) {
            prefs.unregisterOnSharedPreferenceChangeListener(this)
        }
        floatingWidgetManager?.hide()
        floatingWidgetManager = null
    }
}
