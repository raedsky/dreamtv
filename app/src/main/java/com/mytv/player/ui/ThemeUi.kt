package com.mytv.player.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import com.mytv.player.data.Themes

/**
 * يطبّق ألوان الثيم الحالي على الشاشات.
 * كل View عليها android:tag من القيم التالية تأخذ خلفيتها من الثيم:
 *  bg | card | glass | panel | row | poster | btn | circle | tab | search | accent (لون نص) | progress
 */
object ThemeUi {

    private fun Context.dp(v: Float) = v * resources.displayMetrics.density
    private val t get() = Themes.current

    private fun shape(ctx: Context, radius: Float, fill: Int, stroke: Int = 0, strokeW: Float = 0f,
                      fillEnd: Int? = null, oval: Boolean = false) = GradientDrawable().apply {
        if (oval) shape = GradientDrawable.OVAL else cornerRadius = ctx.dp(radius)
        if (fillEnd != null) {
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            colors = intArrayOf(fill, fillEnd)
        } else setColor(fill)
        if (strokeW > 0) setStroke(ctx.dp(strokeW).toInt().coerceAtLeast(1), stroke)
    }

    private fun states(focused: Drawable, normal: Drawable, selected: Drawable? = null) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), focused)
        addState(intArrayOf(android.R.attr.state_pressed), focused)
        if (selected != null) addState(intArrayOf(android.R.attr.state_selected), selected)
        addState(intArrayOf(), normal)
    }

    private fun alpha(color: Int, a: Int) = (color and 0x00FFFFFF) or (a shl 24)

    /** بطاقة زجاجية مثل الصورة: رمادي شفاف بحد خفيف، والمحدد بحد بلون الثيم */
    fun card(ctx: Context, radius: Float = 22f) = states(
        shape(ctx, radius, 0x33FFFFFF, t.accent, 2.5f, fillEnd = alpha(t.accent, 0x22)),
        shape(ctx, radius, 0x1FFFFFFF, 0x26FFFFFF, 1f, fillEnd = 0x0AFFFFFF)
    )

    fun glass(ctx: Context) = shape(ctx, 40f, 0x1AFFFFFF, 0x2EFFFFFF, 1f, fillEnd = 0x08FFFFFF)
    fun panel(ctx: Context) = shape(ctx, 16f, 0x14FFFFFF, 0x1AFFFFFF, 1f)

    fun row(ctx: Context) = states(
        shape(ctx, 10f, t.accent),
        shape(ctx, 10f, 0x00000000),
        shape(ctx, 10f, 0x24FFFFFF, t.accent, 1f)
    )

    fun poster(ctx: Context) = states(
        shape(ctx, 12f, 0x33FFFFFF, t.accent, 3f),
        shape(ctx, 12f, 0x14FFFFFF)
    )

    fun btn(ctx: Context) = states(
        shape(ctx, 26f, t.accent, 0xFFFFFFFF.toInt(), 2f),
        shape(ctx, 26f, 0x26FFFFFF, 0x1FFFFFFF, 1f)
    )

    fun circle(ctx: Context) = states(
        shape(ctx, 0f, t.accent, 0xFFFFFFFF.toInt(), 2f, oval = true),
        shape(ctx, 0f, 0x26FFFFFF, 0x33FFFFFF, 1f, oval = true)
    )

    fun tab(ctx: Context) = states(
        shape(ctx, 20f, t.accent),
        shape(ctx, 20f, 0x1FFFFFFF),
        shape(ctx, 20f, 0x00000000, t.accent, 2f)
    )

    fun search(ctx: Context) = states(
        shape(ctx, 22f, 0x26FFFFFF, t.accent, 2f),
        shape(ctx, 22f, 0x1FFFFFFF)
    )

    fun bg() = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(t.bg, t.bgEnd))

    /** يمر على كل الشاشة ويطبّق الثيم حسب android:tag */
    fun apply(v: View) {
        val ctx = v.context
        when (v.tag as? String) {
            "bg" -> v.background = bg()
            "card" -> v.background = card(ctx)
            "card_small" -> v.background = card(ctx, 18f)
            "glass" -> v.background = glass(ctx)
            "panel" -> v.background = panel(ctx)
            "row" -> v.background = row(ctx)
            "poster" -> v.background = poster(ctx)
            "btn" -> v.background = btn(ctx)
            "circle" -> v.background = circle(ctx)
            "tab" -> v.background = tab(ctx)
            "search" -> v.background = search(ctx)
            "accent" -> (v as? TextView)?.setTextColor(t.accent)
            "progress" -> (v as? ProgressBar)?.progressTintList = ColorStateList.valueOf(t.accent)
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) apply(v.getChildAt(i))
    }
}

/** أيقونة بتدرج ألوان الثيم (أزرق ← بنفسجي في الثيم الافتراضي) مثل الصورة. */
class GradientImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) }

    override fun onDraw(canvas: Canvas) {
        val t = Themes.current
        val save = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        super.onDraw(canvas)
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
            t.iconStart, t.iconEnd, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        canvas.restoreToCount(save)
    }
}
