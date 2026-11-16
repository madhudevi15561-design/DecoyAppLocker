package com.example.applocker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

class PatternLockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onPatternCompleted: ((List<Int>) -> Unit)? = null

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888888")
        style = Paint.Style.FILL
    }

    private val activeDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4FC3F7")
        style = Paint.Style.FILL
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4FC3F7")
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }

    private val errorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF5350")
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }

    private val selectedDots = mutableListOf<Int>()
    private var currentX = -1f
    private var currentY = -1f
    private var isDrawing = false
    private var isErrorState = false

    private fun getDotCenter(index: Int): Pair<Float, Float> {
        val row = index / 3
        val col = index % 3
        val cellW = width / 3f
        val cellH = height / 3f
        return Pair(col * cellW + cellW / 2f, row * cellH + cellH / 2f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val activeLinePaint = if (isErrorState) errorPaint else linePaint

        for (i in 0 until selectedDots.size - 1) {
            val (x1, y1) = getDotCenter(selectedDots[i])
            val (x2, y2) = getDotCenter(selectedDots[i + 1])
            canvas.drawLine(x1, y1, x2, y2, activeLinePaint)
        }

        if (isDrawing && selectedDots.isNotEmpty() && currentX >= 0f) {
            val (lastX, lastY) = getDotCenter(selectedDots.last())
            canvas.drawLine(lastX, lastY, currentX, currentY, activeLinePaint)
        }

        for (i in 0 until 9) {
            val (cx, cy) = getDotCenter(i)
            val isSelected = selectedDots.contains(i)
            val radius = if (isSelected) 22f else 14f
            val paint = when {
                isSelected && isErrorState -> errorPaint
                isSelected -> activeDotPaint
                else -> dotPaint
            }
            canvas.drawCircle(cx, cy, radius, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isErrorState = false
                selectedDots.clear()
                isDrawing = true
                checkHit(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                currentX = event.x
                currentY = event.y
                checkHit(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDrawing = false
                if (selectedDots.isNotEmpty()) {
                    onPatternCompleted?.invoke(selectedDots.toList())
                }
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun checkHit(x: Float, y: Float) {
        val hitRadius = (width / 3f) * 0.28f
        for (i in 0 until 9) {
            if (!selectedDots.contains(i)) {
                val (cx, cy) = getDotCenter(i)
                if (hypot(x - cx, y - cy) <= hitRadius) {
                    selectedDots.add(i)
                }
            }
        }
    }

    fun triggerErrorAndReset() {
        isErrorState = true
        invalidate()
        postDelayed({
            selectedDots.clear()
            isErrorState = false
            invalidate()
        }, 650)
    }
}
