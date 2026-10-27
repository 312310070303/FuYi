package com.fuyi.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.max

/**
 * 悬浮译文卡片：标题栏（可拖动，右上角永远有 ×）+ 可滚动正文 + 固定底部按钮。
 * 高度由外部按内容计算并限制在屏幕的 42% 以内，保证关闭按钮永远可见。
 */
class ResultCardView(context: Context) : LinearLayout(context) {

    var onClose: (() -> Unit)? = null
    var onRetry: (() -> Unit)? = null
    var onDrag: ((Float, Float) -> Unit)? = null

    private val tvOriginal = TextView(context)
    private val tvTranslation = TextView(context)
    private val tvStatus = TextView(context)
    private var lastRawX = 0f
    private var lastRawY = 0f

    init {
        orientation = VERTICAL
        setPadding(context.dp(14), context.dp(6), context.dp(14), context.dp(8))
        background = GradientDrawable().apply {
            cornerRadius = context.dp(18).toFloat()
            setColor(0xF21F2937.toInt())
            setStroke(context.dp(1), 0x33FFFFFF)
        }
        elevation = context.dp(12).toFloat()

        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastRawX = event.rawX
                        lastRawY = event.rawY
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        onDrag?.invoke(event.rawX - lastRawX, event.rawY - lastRawY)
                        lastRawX = event.rawX
                        lastRawY = event.rawY
                        true
                    }

                    else -> true
                }
            }
        }
        header.addView(TextView(context).apply {
            text = "浮译"
            setTextColor(0xFF5EEAD4.toInt())
            textSize = 12f
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(TextView(context).apply {
            text = "×"
            setTextColor(0xFFE5E7EB.toInt())
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(context.dp(10), 0, context.dp(2), context.dp(2))
            setOnClickListener { onClose?.invoke() }
        })
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        tvOriginal.apply {
            setTextColor(0xFF9CA3AF.toInt())
            textSize = 12f
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, context.dp(2), 0, context.dp(6))
        }
        tvTranslation.apply {
            setTextColor(Color.WHITE)
            textSize = 17f
            setLineSpacing(context.dp(3).toFloat(), 1f)
            setPadding(0, 0, 0, context.dp(4))
        }
        tvStatus.apply {
            setTextColor(0xFFFCA5A5.toInt())
            textSize = 12f
            setPadding(0, context.dp(4), 0, 0)
            visibility = GONE
        }

        val content = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(tvOriginal)
            addView(tvTranslation)
            addView(tvStatus)
        }
        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(content)
        }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        val actions = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.END
        }
        actions.addView(actionButton("复制") { copyTranslation() })
        actions.addView(actionButton("重译") { onRetry?.invoke() })
        actions.addView(actionButton("关闭") { onClose?.invoke() })
        addView(actions, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun actionButton(label: String, action: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            setTextColor(0xFF5EEAD4.toInt())
            textSize = 14f
            setPadding(context.dp(12), context.dp(6), context.dp(4), context.dp(2))
            setOnClickListener { action() }
        }

    private fun copyTranslation() {
        val text = tvTranslation.text?.toString().orEmpty()
        if (text.isEmpty()) return
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("translation", text))
        context.toast("已复制")
    }

    /** 按内容估算卡片高度，限制在屏幕 42% 内，最低 160dp。 */
    fun desiredHeightPx(): Int {
        val metrics = resources.displayMetrics
        val text = tvTranslation.text?.toString().orEmpty()
        val lines = if (text.isEmpty()) {
            1
        } else {
            text.split('\n').sumOf { line -> max(1, (line.length + 17) / 18) }
        }
        val lineHeight = 17f * 1.45f * metrics.scaledDensity
        val originalLines = if (tvOriginal.text.isNullOrEmpty()) 0 else 2
        val content = lines * lineHeight + originalLines * 12f * 1.5f * metrics.scaledDensity
        val chrome = 122f * metrics.density
        val minHeight = 160f * metrics.density
        val maxHeight = metrics.heightPixels * 0.42f
        return (content + chrome).toInt().coerceIn(minHeight.toInt(), maxHeight.toInt())
    }

    fun setLoading(original: String) {
        tvOriginal.text = original
        tvTranslation.text = "翻译中…"
        tvTranslation.visibility = VISIBLE
        tvStatus.visibility = GONE
    }

    fun bind(original: String, translation: String?, error: String?) {
        tvOriginal.text = original
        if (error != null) {
            tvTranslation.text = ""
            tvTranslation.visibility = GONE
            tvStatus.text = "⚠ $error"
            tvStatus.visibility = VISIBLE
        } else {
            tvTranslation.text = translation.orEmpty()
            tvTranslation.visibility = VISIBLE
            tvStatus.visibility = GONE
        }
    }
}
