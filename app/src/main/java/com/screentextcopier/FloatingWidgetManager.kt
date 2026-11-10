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
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import kotlin.math.abs

/**
 * Manages rendering, touch handling, dragging, boundary constraints, edge snapping,
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

    private data class SystemInsets(val top: Int, val bottom: Int, val left: Int, val right: Int)

    companion object {
        private const val KEY_POS_X = "floating_pos_x"
        private const val KEY_POS_Y = "floating_pos_y"
        private const val DEFAULT_POS_Y = 400
        private const val EDGE_MARGIN_PX = 16
        private const val ESTIMATED_VIEW_SIZE_PX = 160
    }

    val isVisible: Boolean
        get() = floatingView != null

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (floatingView != null) return

        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.layout_floating_widget, null)
        val iconView = view.findViewById<ImageView>(R.id.floating_icon)

        val insets = getSystemInsets()
        val (screenWidth, screenHeight) = getScreenDimensions()
        val defaultX = (screenWidth - ESTIMATED_VIEW_SIZE_PX - insets.right - EDGE_MARGIN_PX).coerceAtLeast(insets.left + EDGE_MARGIN_PX)
        val rawSavedX = prefs.getInt(KEY_POS_X, defaultX)
        val rawSavedY = prefs.getInt(KEY_POS_Y, DEFAULT_POS_Y)

        val clampedX = clampX(rawSavedX, ESTIMATED_VIEW_SIZE_PX, screenWidth, insets)
        val clampedY = clampY(rawSavedY, ESTIMATED_VIEW_SIZE_PX, screenHeight, insets)

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
            x = clampedX
            y = clampedY
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
                        val insets = getSystemInsets()
                        val (screenWidth, screenHeight) = getScreenDimensions()
                        val viewWidth = rootView.width.takeIf { it > 0 } ?: ESTIMATED_VIEW_SIZE_PX
                        val viewHeight = rootView.height.takeIf { it > 0 } ?: ESTIMATED_VIEW_SIZE_PX

                        params.x = clampX(initialX + deltaX.toInt(), viewWidth, screenWidth, insets)
                        params.y = clampY(initialY + deltaY.toInt(), viewHeight, screenHeight, insets)

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
                        // Drag completed -> Snap to nearest edge within bounds
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
        val insets = getSystemInsets()
        val (screenWidth, screenHeight) = getScreenDimensions()
        val viewWidth = view.width.takeIf { it > 0 } ?: ESTIMATED_VIEW_SIZE_PX
        val viewHeight = view.height.takeIf { it > 0 } ?: ESTIMATED_VIEW_SIZE_PX

        val minSnapX = insets.left + EDGE_MARGIN_PX
        val maxSnapX = (screenWidth - viewWidth - insets.right - EDGE_MARGIN_PX).coerceAtLeast(minSnapX)

        val targetX = if (params.x + viewWidth / 2 < screenWidth / 2) {
            minSnapX
        } else {
            maxSnapX
        }

        // Clamp Y against actual system insets
        val targetY = clampY(params.y, viewHeight, screenHeight, insets)
        params.y = targetY

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

        // Persist validated coordinates
        prefs.edit()
            .putInt(KEY_POS_X, targetX)
            .putInt(KEY_POS_Y, targetY)
            .apply()
    }

    private fun clampX(x: Int, viewWidth: Int, screenWidth: Int, insets: SystemInsets): Int {
        val minX = insets.left
        val maxX = (screenWidth - viewWidth - insets.right).coerceAtLeast(minX)
        return x.coerceIn(minX, maxX)
    }

    private fun clampY(y: Int, viewHeight: Int, screenHeight: Int, insets: SystemInsets): Int {
        val minY = insets.top
        val maxY = (screenHeight - viewHeight - insets.bottom).coerceAtLeast(minY)
        return y.coerceIn(minY, maxY)
    }

    private fun getSystemInsets(): SystemInsets {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = windowManager.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            SystemInsets(
                top = insets.top,
                bottom = insets.bottom,
                left = insets.left,
                right = insets.right
            )
        } else {
            val topId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            val top = if (topId > 0) context.resources.getDimensionPixelSize(topId) else 48
            val navId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
            val bottom = if (navId > 0) context.resources.getDimensionPixelSize(navId) else 48
            SystemInsets(top = top, bottom = bottom, left = 0, right = 0)
        }
    }

    private fun getScreenDimensions(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            val size = Point()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getSize(size)
            Pair(size.x, size.y)
        }
    }
}
