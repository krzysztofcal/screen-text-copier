package com.screentextcopier

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import kotlin.math.abs

/**
 * Manages rendering, touch handling, dragging, edge snapping,
 * and click / long-press callbacks for the floating overlay button.
 */
class FloatingWidgetManager(
    private val context: Context,
    private val windowManager: WindowManager,
    private val prefs: SharedPreferences,
    private val onClick: () -> Unit,
    private val onLongPress: () -> Unit
) {

    private var floatingView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isLongPressed = false

    companion object {
        private const val KEY_POS_X = "floating_pos_x"
        private const val KEY_POS_Y = "floating_pos_y"
        private const val DEFAULT_POS_Y = 400
    }

    val isVisible: Boolean
        get() = floatingView != null

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (floatingView != null) return

        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.layout_floating_widget, null)
        val iconView = view.findViewById<ImageView>(R.id.floating_icon)

        val screenWidth = getScreenWidth()
        val defaultX = screenWidth - 160 // Near right edge by default
        val savedX = prefs.getInt(KEY_POS_X, defaultX)
        val savedY = prefs.getInt(KEY_POS_Y, DEFAULT_POS_Y)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
        }

        setupTouchListener(view, iconView, params)

        try {
            windowManager.addView(view, params)
            floatingView = view
            layoutParams = params
        } catch (_: Exception) {
            floatingView = null
            layoutParams = null
        }
    }

    fun hide() {
        floatingView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
                // Ignore if already removed
            }
            floatingView = null
            layoutParams = null
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListener(
        rootView: View,
        iconView: ImageView,
        params: WindowManager.LayoutParams
    ) {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        val longPressRunnable = Runnable {
            isLongPressed = true
            // Visual subtle scale indicator
            iconView.animate().scaleX(1.15f).scaleY(1.15f).setDuration(120).withEndAction {
                iconView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
            }.start()
            onLongPress()
        }

        rootView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    isLongPressed = false

                    // Slight feedback shrink on touch down
                    iconView.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).start()

                    handler.postDelayed(longPressRunnable, longPressTimeout)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY

                    if (!isDragging && (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop)) {
                        isDragging = true
                        handler.removeCallbacks(longPressRunnable)
                    }

                    if (isDragging) {
                        params.x = initialX + deltaX.toInt()
                        params.y = initialY + deltaY.toInt()

                        try {
                            windowManager.updateViewLayout(rootView, params)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable)
                    iconView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()

                    if (isLongPressed) {
                        // Handled by long press runnable
                    } else if (!isDragging) {
                        // 1-Click Tap -> Extract Text!
                        onClick()
                    } else {
                        // Drag completed -> Snap to nearest edge
                        snapToNearestEdge(rootView, params)
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    iconView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                    true
                }

                else -> false
            }
        }
    }

    private fun snapToNearestEdge(view: View, params: WindowManager.LayoutParams) {
        val screenWidth = getScreenWidth()
        val viewWidth = view.width.takeIf { it > 0 } ?: 160
        val targetX = if (params.x + viewWidth / 2 < screenWidth / 2) {
            16 // Snap to left edge with small margin
        } else {
            screenWidth - viewWidth - 16 // Snap to right edge with small margin
        }

        val animator = ValueAnimator.ofInt(params.x, targetX)
        animator.duration = 180
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { va ->
            params.x = va.animatedValue as Int
            try {
                windowManager.updateViewLayout(view, params)
            } catch (_: Exception) {
            }
        }
        animator.start()

        // Persist coordinates
        prefs.edit()
            .putInt(KEY_POS_X, targetX)
            .putInt(KEY_POS_Y, params.y)
            .apply()
    }

    @Suppress("DEPRECATION")
    private fun getScreenWidth(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            metrics.bounds.width()
        } else {
            val size = Point()
            windowManager.defaultDisplay.getSize(size)
            size.x
        }
    }
}
