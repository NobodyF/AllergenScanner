package com.example.yolodetect

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

data class Detection(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val score: Float)

class OverlayView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    private val boxes = mutableListOf<Detection>()

    private val stroke = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = Color.YELLOW
        textSize = 32f
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    fun setDetections(dets: List<Detection>) {
        boxes.clear(); boxes.addAll(dets); invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = width / 640f
        for (d in boxes) {
            val l = d.x1 * s; val t = d.y1 * s; val r = d.x2 * s; val b = d.y2 * s
            canvas.drawRect(l, t, r, b, stroke)
            canvas.drawText(String.format("%.2f", d.score), l + 6f, t + 34f, textPaint)
        }
    }
}
