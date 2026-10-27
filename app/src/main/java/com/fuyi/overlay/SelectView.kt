package com.fuyi.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 冻结帧选区层：拖动框选，或轻点已识别出来的文字块。
 */
@SuppressLint("ViewConstructor")
class SelectView(context: Context) : View(context) {

    var onCancel: (() -> Unit)? = null
    var onRegion: ((Rect) -> Unit)? = null
    var onBlock: ((TextBlock) -> Unit)? = null

    private var bitmap: Bitmap? = null
    private val blocks = ArrayList<TextBlock>()
    private val selection = RectF()
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var moved = false

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val dimPaint = Paint().apply { color = 0xB8000000.toInt() }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(2).toFloat()
        color = 0xFF5EEAD4.toInt()
    }
    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(1).toFloat()
        color = 0x88FFFFFF.toInt()
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = context.sp(13)
    }
    private val hintBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99000000.toInt()
    }
    private val cancelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = context.sp(28)
        textAlign = Paint.Align.CENTER
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(Color.BLACK)
    }

    fun setBitmap(value: Bitmap) {
        bitmap = value
        invalidate()
    }

    fun setBlocks(value: List<TextBlock>) {
        blocks.clear()
        blocks.addAll(value)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = bitmap ?: return
        val w = width.toFloat()
        val h = height.toFloat()

        canvas.drawBitmap(bmp, null, RectF(0f, 0f, w, h), bitmapPaint)
        canvas.drawRect(0f, 0f, w, h, dimPaint)

        if (selection.width() > 1f && selection.height() > 1f) {
            val src = toBitmapRect(selection)
            if (src.width() > 0 && src.height() > 0) {
                canvas.drawBitmap(bmp, src, selection, bitmapPaint)
            }
            canvas.drawRect(selection, borderPaint)
        }

        if (!dragging) {
            val sx = w / bmp.width
            val sy = h / bmp.height
            for (block in blocks) {
                val r = RectF(
                    block.rect.left * sx,
                    block.rect.top * sy,
                    block.rect.right * sx,
                    block.rect.bottom * sy
                )
                canvas.drawRoundRect(r, context.dp(3).toFloat(), context.dp(3).toFloat(), blockPaint)
            }
        }

        val hint = "拖动框选 · 轻点文字块 · 返回取消"
        val pad = context.dp(12).toFloat()
        val left = context.dp(14).toFloat()
        val top = context.dp(18).toFloat()
        val textWidth = hintPaint.measureText(hint)
        canvas.drawRoundRect(
            RectF(left, top, left + textWidth + pad * 2, top + context.dp(32)),
            context.dp(16).toFloat(),
            context.dp(16).toFloat(),
            hintBgPaint
        )
        canvas.drawText(hint, left + pad, top + context.dp(21), hintPaint)
        canvas.drawText(
            "×",
            w - context.dp(28).toFloat(),
            context.dp(46).toFloat(),
            cancelPaint
        )
    }

    private fun toBitmapRect(r: RectF): Rect {
        val bmp = bitmap ?: return Rect()
        val sx = bmp.width / width.toFloat()
        val sy = bmp.height / height.toFloat()
        return Rect(
            (r.left * sx).toInt().coerceIn(0, bmp.width - 1),
            (r.top * sy).toInt().coerceIn(0, bmp.height - 1),
            (r.right * sx).toInt().coerceIn(1, bmp.width),
            (r.bottom * sy).toInt().coerceIn(1, bmp.height)
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                selection.set(event.x, event.y, event.x, event.y)
                dragging = true
                moved = false
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - downX) > context.dp(6) || abs(event.y - downY) > context.dp(6)) {
                    moved = true
                }
                selection.set(
                    min(downX, event.x),
                    min(downY, event.y),
                    max(downX, event.x),
                    max(downY, event.y)
                )
                invalidate()
            }

            MotionEvent.ACTION_UP -> {
                dragging = false
                if (!moved) {
                    if (event.x > width - context.dp(56) && event.y < context.dp(60)) {
                        onCancel?.invoke()
                        return true
                    }
                    val bmp = bitmap
                    if (bmp != null) {
                        val bx = event.x * bmp.width / width
                        val by = event.y * bmp.height / height
                        val hit = blocks.firstOrNull { it.rect.contains(bx, by) }
                        if (hit != null) {
                            onBlock?.invoke(hit)
                            return true
                        }
                    }
                    selection.setEmpty()
                    invalidate()
                } else {
                    val rect = toBitmapRect(selection)
                    if (rect.width() > context.dp(12) && rect.height() > context.dp(12)) {
                        onRegion?.invoke(rect)
                    } else {
                        selection.setEmpty()
                        invalidate()
                    }
                }
            }
        }
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            onCancel?.invoke()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
