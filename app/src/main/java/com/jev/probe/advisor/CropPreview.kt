package com.jev.probe.advisor

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class CropPreview(context: Context, private val image: Bitmap) : View(context) {
    private val frame = RectF()
    private val selected = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var startX = 0f
    private var startY = 0f
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val scale = min(w / image.width.toFloat(), h / image.height.toFloat())
        val width = image.width * scale; val height = image.height * scale
        frame.set((w - width) / 2, (h - height) / 2, (w + width) / 2, (h + height) / 2)
        reset()
    }
    fun reset() {
        selected.set(frame.left, frame.top + frame.height() * .12f, frame.right, frame.top + frame.height() * .84f)
        invalidate()
    }
    fun region(): Rect {
        val bounds = CropMath.pixels(selected.left, selected.top, selected.right, selected.bottom,
            frame.left, frame.top, frame.width(), frame.height(), image.width, image.height)
        return Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)
    }
    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.rgb(225, 234, 230))
        paint.color = Color.WHITE; paint.style = Paint.Style.FILL
        canvas.drawBitmap(image, null, frame, paint)
        paint.color = 0x88000000.toInt()
        canvas.drawRect(frame.left, frame.top, frame.right, selected.top, paint)
        canvas.drawRect(frame.left, selected.bottom, frame.right, frame.bottom, paint)
        canvas.drawRect(frame.left, selected.top, selected.left, selected.bottom, paint)
        canvas.drawRect(selected.right, selected.top, frame.right, selected.bottom, paint)
        paint.color = Color.rgb(27, 185, 162); paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f
        canvas.drawRect(selected, paint)
        paint.style = Paint.Style.FILL
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x.coerceIn(frame.left, frame.right)
        val y = event.y.coerceIn(frame.top, frame.bottom)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { parent.requestDisallowInterceptTouchEvent(true); startX = x; startY = y }
            MotionEvent.ACTION_MOVE -> {
                selected.set(min(startX, x), min(startY, y), max(startX, x), max(startY, y)); invalidate()
            }
            MotionEvent.ACTION_UP -> {
                parent.requestDisallowInterceptTouchEvent(false)
                if (selected.width() < 12 || selected.height() < 12) reset()
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> { parent.requestDisallowInterceptTouchEvent(false); reset() }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
