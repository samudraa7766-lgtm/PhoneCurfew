package com.example.phonecurfew

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

class MatrixRainView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.GREEN
        textSize = 40f
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val columns = 50   // number of falling columns (will be adjusted)
    private val fontSize = 40f
    private val speed = 15     // lower = faster

    private var drops = IntArray(columns) { Random.nextInt(20) }
    private val charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    private val handler = Handler(Looper.getMainLooper())
    private val runnable = object : Runnable {
        override fun run() {
            invalidate()
            handler.postDelayed(this, speed.toLong())
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.post(runnable)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(runnable)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val newColumns = (w / fontSize).toInt()
        drops = IntArray(newColumns) { Random.nextInt(20) }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)

        for (i in drops.indices) {
            val char = charSet[Random.nextInt(charSet.length)]
            val x = i * fontSize
            val y = drops[i] * fontSize

            canvas.drawText(char.toString(), x, y, paint)

            if (y > height && Random.nextInt(20) > 18) {
                drops[i] = 0
            }
            drops[i]++
        }
    }
}
