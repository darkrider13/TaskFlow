package com.habit.tracker

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.hypot
import kotlin.math.max

private fun rgba(r: Int, g: Int, b: Int, a: Float) = Color(r / 255f, g / 255f, b / 255f, a)

/** The exact colour set of the original app (dark and light). */
class Pal(
    val dark: Boolean,
    val bgc: Color,
    val ink: Color,
    val muted: Color,
    val glass: Color,
    val glass2: Color,
    val edge: Color,
    val hi: Color,
    val sh: Color,
    val panel: Color,
    val daybg: Color,
    val cross: Color,
    val shade: Color,
    val ac: Color,
    val tink: Color,
    val mono: Color,
)

val DarkPal = Pal(
    true, Color(0xFF000000), Color(0xFFF4F4F5), rgba(244, 244, 245, .56f),
    rgba(255, 255, 255, .11f), rgba(255, 255, 255, .20f), rgba(255, 255, 255, .22f), rgba(255, 255, 255, .24f),
    rgba(0, 0, 0, .5f), rgba(22, 22, 26, .78f), rgba(255, 255, 255, .06f), Color(0xFFFF0000),
    rgba(0, 0, 0, .6f), Color(0xFFFFFFFF), Color(0xFF041014), Color(0xFFFFFFFF),
)

val LightPal = Pal(
    false, Color(0xFFE6EEF5), Color(0xFF10253A), rgba(16, 37, 58, .6f),
    rgba(255, 255, 255, .5f), rgba(255, 255, 255, .78f), rgba(255, 255, 255, .95f), rgba(255, 255, 255, .9f),
    rgba(40, 70, 110, .18f), rgba(244, 249, 255, .85f), rgba(255, 255, 255, .45f), Color(0xFFFF0000),
    rgba(16, 37, 58, .35f), Color(0xFF00FFFF), Color(0xFF041014), Color(0xFF000000),
)

val LocalPal = staticCompositionLocalOf { DarkPal }

/** Page background: plain black (dark) or the soft three-colour glow (light). */
fun Modifier.appBg(p: Pal): Modifier {
    return if (p.dark) {
        this.drawBehind { drawRect(p.bgc) }
    } else {
        this.drawBehind {
            drawRect(p.bgc)
            val w = size.width
            val h = size.height
            fun glow(cx: Float, cy: Float, c: Color, frac: Float) {
                val far = max(max(hypot(cx, cy), hypot(w - cx, cy)), max(hypot(cx, h - cy), hypot(w - cx, h - cy)))
                val r = far * frac
                drawCircle(
                    brush = Brush.radialGradient(listOf(c, c.copy(alpha = 0f)), center = Offset(cx, cy), radius = r),
                    radius = r,
                    center = Offset(cx, cy),
                )
            }
            glow(w * .12f, h * .06f, rgba(94, 234, 212, .55f), .42f)
            glow(w * .92f, h * .28f, rgba(167, 139, 250, .45f), .45f)
            glow(w * .18f, h * .96f, rgba(251, 146, 160, .4f), .45f)
        }
    }
}

/** Habit colours can be "#RRGGBB" or "hsl(h,s%,l%)". */
fun parseColor(s: String?): Color {
    val v = (s ?: "").trim()
    try {
        if (v.startsWith("#")) {
            val h = v.substring(1)
            if (h.length == 6) return Color(0xFF000000.toInt() or h.toLong(16).toInt())
            if (h.length == 3) {
                val r = h.substring(0, 1).repeat(2)
                val g = h.substring(1, 2).repeat(2)
                val b = h.substring(2, 3).repeat(2)
                return Color(0xFF000000.toInt() or (r + g + b).toLong(16).toInt())
            }
        }
        if (v.startsWith("hsl", ignoreCase = true)) {
            val n = Regex("[0-9.]+").findAll(v).map { it.value.toFloat() }.toList()
            if (n.size >= 3) return Color.hsl(n[0] % 360f, (n[1] / 100f).coerceIn(0f, 1f), (n[2] / 100f).coerceIn(0f, 1f))
        }
    } catch (e: Exception) {
    }
    return Color(0xFF00FFFF)
}
