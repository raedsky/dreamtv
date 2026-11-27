package com.mytv.player.ui

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/** خلفية الشاشة الرئيسية: خطوط ضوء عمودية بنفسجية/زرقاء متوهجة على أسود. */
class StreaksView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // x (نسبة من العرض), العرض بالـ dp, اللون, بداية ونهاية الخط (نسبة من الارتفاع)
    private class Streak(val x: Float, val w: Float, val color: Int, val top: Float, val bottom: Float)

    private fun col(i: Int) = com.mytv.player.data.Themes.current.streaks[i % 3]

    private val streaks get() = listOf(
        Streak(0.08f, 10f, col(0), 0.45f, 1.0f),
        Streak(0.14f, 26f, col(1), 0.35f, 1.0f),
        Streak(0.19f, 8f, col(2), 0.50f, 0.95f),
        Streak(0.26f, 18f, col(0), 0.20f, 0.85f),
        Streak(0.33f, 12f, col(1), 0.40f, 1.0f),
        Streak(0.41f, 6f, col(2), 0.10f, 0.70f),
        Streak(0.48f, 22f, col(0), 0.30f, 0.90f),
        Streak(0.56f, 10f, col(1), 0.15f, 0.80f),
        Streak(0.63f, 16f, col(2), 0.25f, 1.0f),
        Streak(0.71f, 8f, col(0), 0.05f, 0.75f),
        Streak(0.78f, 24f, col(1), 0.20f, 0.95f),
        Streak(0.85f, 12f, col(2), 0.40f, 1.0f),
        Streak(0.92f, 18f, col(0), 0.10f, 0.90f)
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)   // مطلوب لتأثير التوهج (Blur)
        setBackgroundColor(com.mytv.player.data.Themes.current.bg)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val t = com.mytv.player.data.Themes.current
        canvas.drawPaint(Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, height.toFloat(), t.bg, t.bgEnd, Shader.TileMode.CLAMP)
        })
        val d = resources.displayMetrics.density
        paint.maskFilter = BlurMaskFilter(14f * d, BlurMaskFilter.Blur.NORMAL)
        for (s in streaks) {
            val x = s.x * width
            val top = s.top * height
            val bottom = s.bottom * height
            paint.shader = LinearGradient(
                0f, top, 0f, bottom,
                intArrayOf(Color.TRANSPARENT, s.color, Color.TRANSPARENT),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(x, top, x + s.w * d, bottom, paint)
        }
    }
}
