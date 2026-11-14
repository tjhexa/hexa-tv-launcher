package com.hexa.hometv

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.widget.TextView

/**
 * Gold wordmark with a light sweep every few seconds. Only invalidates during
 * the short sweep, so idle cost is zero.
 */
class ShimmerTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextView(context, attrs) {

    private val matrix = Matrix()
    private val handler = Handler(Looper.getMainLooper())
    private var anim: ValueAnimator? = null
    private var shader: LinearGradient? = null

    private var colorStart = 0xFFE9C887.toInt()
    private var colorMid = 0xFFFFF4D6.toInt()
    private var colorEnd = 0xFFE9C887.toInt()

    fun setShimmerColors(start: Int, mid: Int, end: Int) {
        colorStart = start
        colorMid = mid
        colorEnd = end
        if (width > 0) rebuildShader(width)
    }

    private val sweeper = object : Runnable {
        override fun run() {
            sweep()
            handler.postDelayed(this, 7000L)
        }
    }

    init {
        paint.isAntiAlias = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildShader(w)
    }

    private fun rebuildShader(w: Int) {
        val sh = LinearGradient(
            0f, 0f, w.toFloat().coerceAtLeast(1f), 0f,
            intArrayOf(colorStart, colorMid, colorEnd),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        shader = sh
        matrix.setTranslate(-w.toFloat(), 0f)
        sh.setLocalMatrix(matrix)
        paint.shader = sh
    }

    private fun sweep() {
        val w = width.toFloat().coerceAtLeast(1f)
        anim?.cancel()
        anim = ValueAnimator.ofFloat(-1f, 1f).apply {
            duration = 1400L
            addUpdateListener {
                val p = it.animatedValue as Float
                matrix.setTranslate(p * w, 0f)
                shader?.setLocalMatrix(matrix)
                invalidate()
            }
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.removeCallbacks(sweeper)
        handler.postDelayed(sweeper, 2500L)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(sweeper)
        anim?.cancel()
        super.onDetachedFromWindow()
    }
}
