package com.screentextcopier

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.screentextcopier.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        setupButtons()
        setupSwitch()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun setupButtons() {
        // Direct link to system Accessibility Settings
        binding.btnAccessibilitySettings.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        // Test button to inspect the clipboard content
        binding.btnPasteTest.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val item = clipboard.primaryClip?.getItemAt(0)?.text
            if (!item.isNullOrEmpty()) {
                binding.tvClipboardPreview.text = item
            } else {
                binding.tvClipboardPreview.text = getString(R.string.empty_clipboard)
            }
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

    private fun updateServiceStatus() {
        val isEnabled = isAccessibilityServiceEnabled()

        if (isEnabled) {
            binding.tvStatusBadge.text = getString(R.string.status_service_active)
            binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_active))
            binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_badge)
            binding.tvStatusDesc.text = getString(R.string.desc_service_active)
            binding.btnAccessibilitySettings.text = getString(R.string.btn_open_accessibility)
            binding.switchFloatingWidget.isEnabled = true
        } else {
            binding.tvStatusBadge.text = getString(R.string.status_service_inactive)
            binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_inactive))
            binding.tvStatusBadge.setBackgroundColor(ContextCompat.getColor(this, R.color.status_inactive_bg))
            binding.tvStatusDesc.text = getString(R.string.desc_service_inactive)
            binding.btnAccessibilitySettings.text = getString(R.string.btn_enable_service)
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
