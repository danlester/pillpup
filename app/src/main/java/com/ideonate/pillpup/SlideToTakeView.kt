package com.ideonate.pillpup

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat

/**
 * "Slide to take" thumb. Drag the circle to the right end to fire [onTaken].
 * Released before the threshold springs back to start.
 */
class SlideToTakeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var onTaken: (() -> Unit)? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x22000000
        style = Paint.Style.FILL
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.accent)
        alpha = 90
        style = Paint.Style.FILL
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.accent)
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99000000.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 13f * resources.displayMetrics.density
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66000000.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }

    private val thumbRadius = 16f * resources.displayMetrics.density
    private var minThumbX = 0f
    private var maxThumbX = 0f
    private var thumbX = 0f
    private var dragging = false
    private var dragOffset = 0f
    private var animator: ValueAnimator? = null
    private var locked = false

    private val label = context.getString(R.string.slide_to_take)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        minThumbX = thumbRadius + paddingLeft
        maxThumbX = w - thumbRadius - paddingRight
        if (!dragging) thumbX = minThumbX
    }

    override fun onDraw(canvas: Canvas) {
        val cy = height / 2f
        val trackH = thumbRadius * 1.5f
        val left = paddingLeft.toFloat()
        val right = (width - paddingRight).toFloat()
        val rect = RectF(left, cy - trackH / 2, right, cy + trackH / 2)
        canvas.drawRoundRect(rect, trackH / 2, trackH / 2, trackPaint)

        // filled portion behind thumb
        val fillRect = RectF(left, cy - trackH / 2, thumbX, cy + trackH / 2)
        canvas.drawRoundRect(fillRect, trackH / 2, trackH / 2, fillPaint)

        // label
        canvas.drawText(label, width / 2f, cy + labelPaint.textSize / 3f, labelPaint)

        // chevrons near right
        val sz = thumbRadius * 0.55f
        val baseX = right - thumbRadius - 6f
        for (i in 0..1) {
            val x = baseX - i * sz * 0.85f
            canvas.drawLine(x - sz / 2, cy - sz / 2, x, cy, arrowPaint)
            canvas.drawLine(x - sz / 2, cy + sz / 2, x, cy, arrowPaint)
        }

        canvas.drawCircle(thumbX, cy, thumbRadius, thumbPaint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!isEnabled || locked) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val cy = height / 2f
                val dx = e.x - thumbX
                val dy = e.y - cy
                val grab = thumbRadius * 2f
                if (dx * dx + dy * dy <= grab * grab) {
                    dragging = true
                    animator?.cancel()
                    dragOffset = e.x - thumbX
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    thumbX = (e.x - dragOffset).coerceIn(minThumbX, maxThumbX)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    val span = (maxThumbX - minThumbX).coerceAtLeast(1f)
                    val frac = (thumbX - minThumbX) / span
                    if (e.actionMasked == MotionEvent.ACTION_UP && frac >= 0.75f) {
                        thumbX = maxThumbX
                        locked = true
                        invalidate()
                        onTaken?.invoke()
                    } else {
                        springBack()
                    }
                    return true
                }
            }
        }
        return super.onTouchEvent(e)
    }

    private fun springBack() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(thumbX, minThumbX).apply {
            duration = 180
            addUpdateListener {
                thumbX = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun reset() {
        animator?.cancel()
        locked = false
        thumbX = minThumbX
        invalidate()
    }
}
