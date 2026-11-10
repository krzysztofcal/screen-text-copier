package com.screentextcopier

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.screentextcopier.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    // State for secure clipboard inspection
    private var cachedClipboardRawText: String? = null
    private var isClipboardRevealed: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        setupButtons()
        setupSwitch()
        setupClipboardControls()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    override fun onPause() {
        super.onPause()
        // Automatically hide and mask sensitive clipboard contents when the user leaves the application
        maskSensitivePreview()
    }

    private fun maskSensitivePreview() {
        val raw = cachedClipboardRawText
        if (raw != null && isClipboardRevealed) {
            isClipboardRevealed = false
            val (lines, chars) = TextExtractor.computeTextMetadata(raw)
            binding.tvClipboardPreview.text = getString(R.string.clipboard_masked_summary, lines, chars)
            binding.btnTogglePreview.text = getString(R.string.btn_toggle_preview_show)
        }
    }

    private fun setupButtons() {
        // Direct link to system Accessibility Settings
        binding.btnAccessibilitySettings.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        // Direct shortcut to Application Details Settings for Android 13/14 Restricted Settings
        binding.btnOpenAppInfo.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        }
    }

    private fun setupSwitch() {
        val isFloatingEnabled = prefs.getBoolean(
            TextCopierAccessibilityService.PREF_FLOATING_BUTTON_ENABLED,
            true
        )
        binding.switchFloatingWidget.isChecked = isFloatingEnabled

        binding.switchFloatingWidget.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit()
                .putBoolean(
                    TextCopierAccessibilityService.PREF_FLOATING_BUTTON_ENABLED,
                    isChecked
                )
                .apply()
        }
    }

    private fun setupClipboardControls() {
        // Inspect clipboard securely: mask by default, do not expose raw text unless user chooses to
        binding.btnPasteTest.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val item = clipboard.primaryClip?.getItemAt(0)?.text?.toString()

            if (!item.isNullOrEmpty()) {
                cachedClipboardRawText = item
                isClipboardRevealed = false

                val (lines, chars) = TextExtractor.computeTextMetadata(item)
                binding.tvClipboardPreview.text = getString(R.string.clipboard_masked_summary, lines, chars)
                binding.btnTogglePreview.text = getString(R.string.btn_toggle_preview_show)
                binding.layoutPreviewActions.visibility = View.VISIBLE
            } else {
                cachedClipboardRawText = null
                isClipboardRevealed = false
                binding.tvClipboardPreview.text = getString(R.string.empty_clipboard)
                binding.layoutPreviewActions.visibility = View.GONE
            }
        }

        // Toggle plaintext vs masked summary
        binding.btnTogglePreview.setOnClickListener {
            val raw = cachedClipboardRawText ?: return@setOnClickListener
            isClipboardRevealed = !isClipboardRevealed

            if (isClipboardRevealed) {
                binding.tvClipboardPreview.text = raw
                binding.btnTogglePreview.text = getString(R.string.btn_toggle_preview_hide)
            } else {
                val (lines, chars) = TextExtractor.computeTextMetadata(raw)
                binding.tvClipboardPreview.text = getString(R.string.clipboard_masked_summary, lines, chars)
                binding.btnTogglePreview.text = getString(R.string.btn_toggle_preview_show)
            }
        }

        // Clear preview immediately to avoid lingering sensitive data on screen
        binding.btnClearPreview.setOnClickListener {
            cachedClipboardRawText = null
            isClipboardRevealed = false
            binding.tvClipboardPreview.text = getString(R.string.test_hint)
            binding.layoutPreviewActions.visibility = View.GONE
        }
    }

    private fun updateServiceStatus() {
        val isEnabled = isAccessibilityServiceEnabled()

        if (isEnabled) {
            binding.tvStatusBadge.text = getString(R.string.status_service_active)
            binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_active))
            binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_badge)
            binding.tvStatusDesc.text = getString(R.string.desc_service_active)
            binding.btnAccessibilitySettings.text = getString(R.string.btn_open_accessibility)
            binding.switchFloatingWidget.isEnabled = true

            // Hide restricted settings helper if already active
            binding.cardRestrictedSettings.visibility = View.GONE
        } else {
            binding.tvStatusBadge.text = getString(R.string.status_service_inactive)
            binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_inactive))
            binding.tvStatusBadge.setBackgroundColor(ContextCompat.getColor(this, R.color.status_inactive_bg))
            binding.tvStatusDesc.text = getString(R.string.desc_service_inactive)
            binding.btnAccessibilitySettings.text = getString(R.string.btn_enable_service)

            // Show helper on Android 13+ (API 33+) when service is inactive
            binding.cardRestrictedSettings.visibility =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) View.VISIBLE else View.GONE
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        if (TextCopierAccessibilityService.isServiceRunning) {
            return true
        }

        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)

        val expectedId = "${packageName}/${TextCopierAccessibilityService::class.java.canonicalName}"
        return enabledServices.any { service ->
            service.id.equals(expectedId, ignoreCase = true) ||
                    service.resolveInfo?.serviceInfo?.name == TextCopierAccessibilityService::class.java.name
        }
    }
}
