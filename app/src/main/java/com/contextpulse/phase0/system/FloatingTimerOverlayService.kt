package com.contextpulse.phase0.system

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.contextpulse.phase0.Phase0Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FloatingTimerOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var tickerJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    private var currentType = FloatingTimerType.NONE
    private var currentTitle = ""
    private var currentPackage: String? = null

    private var titleTextView: TextView? = null
    private var timerTextView: TextView? = null
    private var iconTextView: TextView? = null
    private var rootLayout: LinearLayout? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            removeOverlay()
            stopSelf()
            return START_NOT_STICKY
        }

        val typeName = intent?.getStringExtra(EXTRA_TYPE) ?: FloatingTimerType.NONE.name
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Timer"
        val appPackage = intent?.getStringExtra(EXTRA_PACKAGE)

        val type = try {
            FloatingTimerType.valueOf(typeName)
        } catch (_: Exception) {
            FloatingTimerType.NONE
        }

        if (type == FloatingTimerType.NONE) {
            removeOverlay()
            stopSelf()
            return START_NOT_STICKY
        }

        val isSameSession = currentType == type && currentPackage == appPackage && overlayView != null
        currentType = type
        currentTitle = title
        currentPackage = appPackage

        startForegroundNotification(title, if (type == FloatingTimerType.SLEEP_TIMER) "Sleep mode active" else "Usage allowance active")

        if (overlayView == null) {
            createAndAttachOverlay()
        } else {
            updateOverlayContent()
        }

        if (!isSameSession || tickerJob == null || !tickerJob!!.isActive) {
            startTicker()
        }

        return START_STICKY
    }

    private fun startForegroundNotification(title: String, subtitle: String) {
        try {
            val notification = NotificationCompat.Builder(this, Phase0Application.CHANNEL_BLE_SERVICE)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("ContextPulse • $title")
                .setContentText(subtitle)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setShowWhen(false)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start foreground notification for floating timer", e)
        }
    }

    private fun createAndAttachOverlay() {
        val wm = windowManager ?: return

        val layoutParamsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutParamsType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 140
        }

        val density = resources.displayMetrics.density
        val dpToPx = { dp: Int -> (dp * density).toInt() }

        // Root container: pill shaped layout
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpToPx(12), dpToPx(8), dpToPx(14), dpToPx(8))
            gravity = Gravity.CENTER_VERTICAL
            elevation = dpToPx(8).toFloat()
        }
        rootLayout = root

        // Icon TextView (Emoji / Symbol)
        val iconTv = TextView(this).apply {
            textSize = 16f
            setPadding(0, 0, dpToPx(8), 0)
        }
        iconTextView = iconTv
        root.addView(iconTv)

        // Texts container (Title + Clock)
        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val titleTv = TextView(this).apply {
            textSize = 10f
            setTextColor(Color.parseColor("#A0AEC0"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isSingleLine = true
        }
        titleTextView = titleTv
        textContainer.addView(titleTv)

        val timerTv = TextView(this).apply {
            textSize = 13.5f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.MONOSPACE
            isSingleLine = true
        }
        timerTextView = timerTv
        textContainer.addView(timerTv)

        root.addView(textContainer)

        // Touch Dragging Implementation
        root.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                            isDragging = true
                        }
                        params.x = initialX + dx
                        params.y = initialY + dy
                        try {
                            wm.updateViewLayout(root, params)
                        } catch (_: Exception) {}
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            handleOverlayClick()
                        }
                        return true
                    }
                }
                return false
            }
        })

        updateOverlayContent()

        try {
            wm.addView(root, params)
            overlayView = root
            Log.d(TAG, "Floating timer overlay attached successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach floating overlay to WindowManager", e)
        }
    }

    private fun handleOverlayClick() {
        if (currentType == FloatingTimerType.SLEEP_TIMER) {
            val snooze = SnoozeManager.getInstance(this)
            Toast.makeText(
                this,
                "Sleep mode active: ${snooze.getFormattedRemainingDetailed()} remaining",
                Toast.LENGTH_SHORT
            ).show()
        } else if (currentType == FloatingTimerType.APP_USAGE_BUDGET) {
            val pkg = currentPackage
            if (pkg != null) {
                val sec = AppUsageBudgetManager.getRemainingSecondsForPackage(this, pkg)
                val m = sec / 60
                val s = sec % 60
                Toast.makeText(
                    this,
                    "$currentTitle allowance: %02d:%02d remaining".format(m, s),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun updateOverlayContent() {
        val root = rootLayout ?: return
        val iconTv = iconTextView ?: return
        val titleTv = titleTextView ?: return
        val timerTv = timerTextView ?: return

        val density = resources.displayMetrics.density
        val dpToPx = { dp: Int -> (dp * density).toInt() }

        titleTv.text = currentTitle.uppercase()

        val bg = GradientDrawable()
        bg.cornerRadius = dpToPx(18).toFloat()

        when (currentType) {
            FloatingTimerType.SLEEP_TIMER -> {
                iconTv.text = "🌙"
                bg.setColor(Color.parseColor("#EE131828"))
                bg.setStroke(dpToPx(1), Color.parseColor("#888B5CF6"))
                titleTv.setTextColor(Color.parseColor("#C4B5FD"))
            }
            FloatingTimerType.APP_USAGE_BUDGET -> {
                iconTv.text = "⏳"
                val remainingSec = currentPackage?.let {
                    AppUsageBudgetManager.getRemainingSecondsForPackage(this, it)
                } ?: 0

                if (remainingSec <= 120) {
                    bg.setColor(Color.parseColor("#EE2A1215"))
                    bg.setStroke(dpToPx(1), Color.parseColor("#88EF4444"))
                    titleTv.setTextColor(Color.parseColor("#FCA5A5"))
                } else {
                    bg.setColor(Color.parseColor("#EE1F1C16"))
                    bg.setStroke(dpToPx(1), Color.parseColor("#88F59E0B"))
                    titleTv.setTextColor(Color.parseColor("#FCD34D"))
                }
            }
            FloatingTimerType.NONE -> {}
        }

        root.background = bg
        refreshTimeText(timerTv)
    }

    private fun refreshTimeText(timerTv: TextView) {
        val totalSec: Int = when (currentType) {
            FloatingTimerType.SLEEP_TIMER -> {
                SnoozeManager.getInstance(this).getRemainingSeconds().toInt()
            }
            FloatingTimerType.APP_USAGE_BUDGET -> {
                currentPackage?.let {
                    AppUsageBudgetManager.getRemainingSecondsForPackage(this, it)
                } ?: 0
            }
            FloatingTimerType.NONE -> 0
        }

        if (totalSec <= 0) {
            timerTv.text = "00:00"
            // Re-evaluate to dismiss
            FloatingTimerOverlayManager.updateTimerState(this)
            return
        }

        val m = totalSec / 60
        val s = totalSec % 60
        timerTv.text = "%02d:%02d".format(m, s)

        // Dynamic styling as budget gets low (<= 120s)
        if (currentType == FloatingTimerType.APP_USAGE_BUDGET) {
            val root = rootLayout
            val titleTv = titleTextView
            if (root != null && titleTv != null) {
                val density = resources.displayMetrics.density
                val dpToPx = { dp: Int -> (dp * density).toInt() }
                val bg = GradientDrawable()
                bg.cornerRadius = dpToPx(18).toFloat()
                if (totalSec <= 120) {
                    bg.setColor(Color.parseColor("#EE2A1215"))
                    bg.setStroke(dpToPx(1), Color.parseColor("#88EF4444"))
                    titleTv.setTextColor(Color.parseColor("#FCA5A5"))
                } else {
                    bg.setColor(Color.parseColor("#EE1F1C16"))
                    bg.setStroke(dpToPx(1), Color.parseColor("#88F59E0B"))
                    titleTv.setTextColor(Color.parseColor("#FCD34D"))
                }
                root.background = bg
            }
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive) {
                delay(1000L)
                timerTextView?.let { refreshTimeText(it) }
            }
        }
    }

    private fun removeOverlay() {
        tickerJob?.cancel()
        tickerJob = null
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        if (overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlayView", e)
            }
            overlayView = null
            rootLayout = null
            iconTextView = null
            titleTextView = null
            timerTextView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
    }

    companion object {
        private const val TAG = "FloatingTimerOverlay"
        private const val NOTIFICATION_ID = 2002
        private const val ACTION_SHOW_OR_UPDATE = "com.contextpulse.phase0.action.SHOW_TIMER_OVERLAY"
        private const val ACTION_STOP = "com.contextpulse.phase0.action.STOP_TIMER_OVERLAY"

        private const val EXTRA_TYPE = "extra_type"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_PACKAGE = "extra_package"

        fun startOrUpdate(context: Context, state: FloatingTimerState) {
            val intent = Intent(context, FloatingTimerOverlayService::class.java).apply {
                action = ACTION_SHOW_OR_UPDATE
                putExtra(EXTRA_TYPE, state.type.name)
                putExtra(EXTRA_TITLE, state.title)
                putExtra(EXTRA_PACKAGE, state.appPackage)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start FloatingTimerOverlayService", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingTimerOverlayService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop FloatingTimerOverlayService", e)
            }
        }
    }
}
