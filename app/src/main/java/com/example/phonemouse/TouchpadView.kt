package com.example.phonemouse

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

class TouchpadView @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : View(c, a) {

    interface Listener {
        fun onMove(dx: Float, dy: Float)
        fun onScroll(steps: Int)
        fun onTap()
        fun onTwoFingerTap()
    }

    var listener: Listener? = null

    private val slop = ViewConfiguration.get(c).scaledTouchSlop
    private val scrollStepPx = 28f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lastX = 0f; private var lastY = 0f
    private var downX = 0f; private var downY = 0f
    private var downTime = 0L
    private var maxPointers = 0
    private var scrollAccum = 0f
    private var lastAvgY = 0f
    private var moved = false

    override fun onDraw(canvas: Canvas) {
        paint.style = Paint.Style.FILL; paint.color = 0xFFE8EAF0.toInt()
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 32f, 32f, paint)
        paint.color = 0xFF6B7280.toInt(); paint.textSize = 38f; paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Touchpad · drag to move · 2 fingers to scroll", width / 2f, height / 2f, paint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.x; lastY = e.y; downX = e.x; downY = e.y
                downTime = e.eventTime; maxPointers = 1; moved = false; scrollAccum = 0f
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                maxPointers = maxOf(maxPointers, e.pointerCount)
                lastAvgY = avgY(e); scrollAccum = 0f
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(e.x - downX) > slop || abs(e.y - downY) > slop) moved = true
                if (e.pointerCount >= 2) {
                    val y = avgY(e)
                    scrollAccum += y - lastAvgY; lastAvgY = y
                    val steps = (scrollAccum / scrollStepPx).toInt()
                    if (steps != 0) { scrollAccum -= steps * scrollStepPx; listener?.onScroll(steps) }
                } else if (maxPointers == 1) {
                    listener?.onMove(e.x - lastX, e.y - lastY)
                }
                lastX = e.x; lastY = e.y
            }
            MotionEvent.ACTION_UP -> {
                val quick = e.eventTime - downTime < 250
                if (!moved && quick) {
                    if (maxPointers == 1) listener?.onTap()
                    else if (maxPointers == 2) listener?.onTwoFingerTap()
                }
                performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private fun avgY(e: MotionEvent): Float {
        var s = 0f; for (i in 0 until e.pointerCount) s += e.getY(i); return s / e.pointerCount
    }
}
