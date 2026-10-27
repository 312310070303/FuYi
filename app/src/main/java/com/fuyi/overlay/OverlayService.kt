package com.fuyi.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 常驻前台服务：悬浮球 + 冻结帧选区 + 译文卡片。
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())
    private val ocr = OcrEngine()

    private var bubble: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var selectView: SelectView? = null
    private var cardView: ResultCardView? = null
    private var cardParams: WindowManager.LayoutParams? = null

    private var busy = false
    private var lastOriginal = ""
    private var lastAnchor: RectF? = null

    private val screenWidth: Int
        get() = windowManager.currentWindowMetrics.bounds.width()

    private val screenHeight: Int
        get() = windowManager.currentWindowMetrics.bounds.height()

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = Prefs(this)
        startForegroundCompat()

        if (!Settings.canDrawOverlays(this)) {
            toast("请先授予悬浮窗权限")
            stopSelf()
            return
        }
        addBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            toast("请先授予悬浮窗权限")
            stopSelf()
            return START_NOT_STICKY
        }
        if (bubble == null) addBubble()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val params = bubbleParams ?: return
        val size = dp(56)
        params.x = params.x.coerceIn(0, max(0, screenWidth - size))
        params.y = params.y.coerceIn(0, max(0, screenHeight - size))
        bubble?.let {
            try {
                windowManager.updateViewLayout(it, params)
            } catch (_: Throwable) {
            }
        }
    }

    override fun onDestroy() {
        closeSelector()
        closeCard(false)
        bubble?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Throwable) {
            }
        }
        bubble = null
        bubbleParams = null
        ocr.close()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    // ---------------------------------------------------------------- 悬浮球

    private fun addBubble() {
        val size = dp(56)
        val view = TextView(this).apply {
            text = "译"
            setTextColor(Color.WHITE)
            textSize = 21f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF0F766E.toInt())
                setStroke(dp(2), 0x55FFFFFF)
            }
            elevation = dp(8).toFloat()
        }

        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = max(0, screenWidth - size - dp(8))
            y = max(dp(80), screenHeight / 2)
        }

        attachBubbleTouch(view, params)

        try {
            windowManager.addView(view, params)
            bubble = view
            bubbleParams = params
        } catch (t: Throwable) {
            toast("悬浮窗添加失败：${t.message}")
            stopSelf()
        }
    }

    private fun attachBubbleTouch(view: View, params: WindowManager.LayoutParams) {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    view.alpha = 0.7f
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    val size = dp(56)
                    params.x = (startX + dx).toInt().coerceIn(0, max(0, screenWidth - size))
                    params.y = (startY + dy).toInt().coerceIn(0, max(0, screenHeight - size))
                    try {
                        windowManager.updateViewLayout(view, params)
                    } catch (_: Throwable) {
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    view.alpha = 1f
                    if (!moved) beginCapture()
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    view.alpha = 1f
                    true
                }

                else -> false
            }
        }
    }

    // ---------------------------------------------------------------- 截屏

    private fun beginCapture() {
        if (busy) return
        if (!CaptureService.isReady) {
            toast("无障碍服务未连接，请回设置重新开启")
            openAccessibilitySettings()
            return
        }
        busy = true
        bubble?.visibility = View.GONE

        handler.postDelayed({
            val started = CaptureService.captureScreen { bitmap, error ->
                handler.post {
                    busy = false
                    if (bitmap == null) {
                        toast(error ?: "截屏失败")
                        bubble?.visibility = View.VISIBLE
                    } else {
                        showSelector(bitmap)
                    }
                }
            }
            if (!started) {
                busy = false
                toast("无障碍服务未连接")
                bubble?.visibility = View.VISIBLE
            }
        }, 220)
    }

    private fun showSelector(frame: Bitmap) {
        val view = SelectView(this)
        view.setBitmap(frame)
        view.onCancel = {
            closeSelector()
            bubble?.visibility = View.VISIBLE
        }
        view.onRegion = { rect ->
            closeSelector()
            translateRegion(frame, rect)
        }
        view.onBlock = { block ->
            closeSelector()
            translateBlock(block)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        try {
            windowManager.addView(view, params)
        } catch (t: Throwable) {
            toast("无法显示选区：${t.message}")
            bubble?.visibility = View.VISIBLE
            return
        }

        selectView = view
        view.requestFocus()

        if (prefs.smartSelect) {
            ocr.recognize(frame, { blocks ->
                handler.post {
                    if (selectView === view) view.setBlocks(blocks)
                }
            })
        }
    }

    private fun closeSelector() {
        val view = selectView ?: return
        selectView = null
        try {
            windowManager.removeView(view)
        } catch (_: Throwable) {
        }
    }

    private fun translateRegion(frame: Bitmap, rawRect: Rect) {
        val rect = Rect(rawRect)
        rect.left = rect.left.coerceIn(0, frame.width - 2)
        rect.top = rect.top.coerceIn(0, frame.height - 2)
        rect.right = rect.right.coerceIn(rect.left + 1, frame.width)
        rect.bottom = rect.bottom.coerceIn(rect.top + 1, frame.height)

        val crop = try {
            Bitmap.createBitmap(frame, rect.left, rect.top, rect.width(), rect.height())
        } catch (t: Throwable) {
            toast("裁剪失败")
            bubble?.visibility = View.VISIBLE
            return
        }

        val anchor = RectF(rect)
        val statusCard = ensureCard(anchor)
        statusCard.bind("", "识别中…", null)
        fitCard(statusCard)

        ocr.recognize(crop, { blocks ->
            val text = OcrEngine.clean(blocks.joinToString("\n") { it.text })
            handler.post {
                if (text.isBlank()) {
                    closeCard()
                    toast("没有识别到文字，换个范围再试")
                } else {
                    doTranslate(text, anchor)
                }
            }
        }, { message ->
            handler.post {
                closeCard()
                toast(message)
            }
        })
    }

    private fun translateBlock(block: TextBlock) {
        val text = OcrEngine.clean(block.text)
        if (text.isBlank()) {
            toast("这个文字块没有内容")
            bubble?.visibility = View.VISIBLE
            return
        }
        doTranslate(text, block.rect)
    }

    // ---------------------------------------------------------------- 翻译与卡片

    private fun doTranslate(text: String, anchor: RectF?) {
        lastOriginal = text
        lastAnchor = anchor

        val view = ensureCard(anchor)
        view.setLoading(text)
        fitCard(view)

        Translator.translate(prefs, text) { result, error ->
            handler.post {
                if (cardView !== view) return@post
                view.bind(text, result, error)
                fitCard(view)
            }
        }
    }

    private fun ensureCard(anchor: RectF?): ResultCardView {
        closeCard(false)

        val view = ResultCardView(this)
        val width = min(screenWidth - dp(32), dp(380))

        val params = WindowManager.LayoutParams(
            width,
            dp(160),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = screenHeight / 2
        }

        view.onClose = { closeCard() }
        view.onRetry = {
            if (lastOriginal.isNotEmpty()) doTranslate(lastOriginal, lastAnchor)
        }
        view.onDrag = { dx, dy ->
            params.x = (params.x + dx).toInt().coerceIn(0, max(0, screenWidth - width))
            params.y = (params.y + dy).toInt()
                .coerceIn(0, max(0, screenHeight - params.height))
            try {
                windowManager.updateViewLayout(view, params)
            } catch (_: Throwable) {
            }
        }

        try {
            windowManager.addView(view, params)
        } catch (t: Throwable) {
            toast("无法显示译文卡片：${t.message}")
            bubble?.visibility = View.VISIBLE
            return view
        }

        cardView = view
        cardParams = params
        bubble?.visibility = View.GONE

        if (anchor != null) {
            val centerX = ((anchor.left + anchor.right) / 2f).toInt()
            params.x = (centerX - width / 2).coerceIn(dp(8), max(dp(8), screenWidth - width - dp(8)))
            var y = anchor.bottom.toInt() + dp(12)
            if (y + dp(200) > screenHeight) y = anchor.top.toInt() - dp(200)
            params.y = y.coerceIn(dp(50), max(dp(50), screenHeight - dp(200)))
            try {
                windowManager.updateViewLayout(view, params)
            } catch (_: Throwable) {
            }
        }

        view.post {
            if (cardView !== view) return@post
            fitCard(view)
        }

        return view
    }

    private fun fitCard(view: ResultCardView) {
        val params = cardParams ?: return
        if (cardView !== view) return

        val height = view.desiredHeightPx()
        var changed = false
        if (params.height != height) {
            params.height = height
            changed = true
        }

        val maxY = (screenHeight - height - dp(8)).coerceAtLeast(dp(8))
        if (params.y > maxY) {
            params.y = maxY
            changed = true
        }
        if (params.y < dp(8)) {
            params.y = dp(8)
            changed = true
        }

        if (changed) {
            try {
                windowManager.updateViewLayout(view, params)
            } catch (_: Throwable) {
            }
        }
    }

    private fun closeCard(restoreBubble: Boolean = true) {
        val view = cardView ?: return
        cardView = null
        cardParams = null
        try {
            windowManager.removeView(view)
        } catch (_: Throwable) {
        }
        if (restoreBubble) bubble?.visibility = View.VISIBLE
    }

    // ---------------------------------------------------------------- 杂项

    private fun startForegroundCompat() {
        val channelId = "fuyi_overlay"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(channelId, "悬浮翻译", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("浮译运行中")
            .setContentText("回到游戏，点悬浮球即可翻译")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()

        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Throwable) {
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
