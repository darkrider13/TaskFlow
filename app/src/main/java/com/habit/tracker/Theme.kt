package com.habit.tracker

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
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
    rgba(255, 255, 255, .08f), rgba(255, 255, 255, .16f), rgba(255, 255, 255, .18f), rgba(255, 255, 255, .2f),
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
        this.drawWithCache {
            onDrawBehind { drawRect(p.bgc); drawNightScene() }
        }
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

/** Moonlit misty mountains, drawn in code (no image file), shown behind every screen in dark mode. */
private fun DrawScope.drawNightScene() {
    val w = size.width
    val h = size.height
    fun c(v: Long) = Color(v)

    // sky
    drawRect(Brush.verticalGradient(
        0f to c(0xFF040813), .40f to c(0xFF0A1838), .70f to c(0xFF14305E), 1f to c(0xFF0B1B38), startY = 0f, endY = h))

    // stars
    var seed = 12345L
    fun rnd(): Float { seed = (seed * 1103515245L + 12345L) and 0x7fffffffL; return (seed % 10000L) / 10000f }
    repeat(90) {
        val x = rnd() * w
        val y = rnd() * h * .48f
        val r = .5f + rnd() * 1.1f
        drawCircle(Color.White.copy(alpha = .25f + rnd() * .6f), r, Offset(x, y))
    }

    // moon + glow
    val mc = Offset(w * .76f, h * .27f)
    drawCircle(Brush.radialGradient(listOf(Color(0x55BFD4FF), Color(0x00BFD4FF)), mc, w * .26f), w * .26f, mc)
    drawCircle(c(0xFFE9EDF5), w * .055f, mc)
    drawCircle(Color(0x22708098), w * .014f, Offset(mc.x - w * .015f, mc.y - w * .008f))
    drawCircle(Color(0x22708098), w * .010f, Offset(mc.x + w * .018f, mc.y + w * .014f))

    fun ridge(pts: List<Pair<Float, Float>>, top: Color, bottom: Color, topY: Float) {
        val p = Path()
        p.moveTo(pts.first().first * w, h)
        for ((x, y) in pts) p.lineTo(x * w, y * h)
        p.lineTo(pts.last().first * w, h)
        p.close()
        drawPath(p, Brush.verticalGradient(listOf(top, bottom), startY = topY * h, endY = h))
    }
    fun fog(y0: Float, y1: Float, a: Float) {
        drawRect(
            Brush.verticalGradient(listOf(Color(0x00B4C8E6), Color(0xFFB4C8E6).copy(alpha = a), Color(0x00B4C8E6)), startY = y0 * h, endY = y1 * h),
            topLeft = Offset(0f, y0 * h), size = androidx.compose.ui.geometry.Size(w, (y1 - y0) * h),
        )
    }

    // main peak (moonlit on its right side)
    ridge(listOf(-.1f to .64f, .12f to .55f, .27f to .49f, .38f to .43f, .45f to .385f, .52f to .43f, .62f to .49f, .78f to .56f, 1.1f to .62f),
        c(0xFF22406E), c(0xFF0F2040), .38f)
    val face = Path().apply {
        moveTo(.45f * w, .385f * h); lineTo(.52f * w, .43f * h); lineTo(.62f * w, .49f * h); lineTo(.78f * w, .56f * h)
        lineTo(.50f * w, .56f * h); close()
    }
    drawPath(face, Color(0x1F9DB9EA))
    fog(.50f, .68f, .30f)

    // middle hills
    ridge(listOf(-.1f to .64f, .15f to .60f, .38f to .66f, .62f to .62f, .85f to .67f, 1.1f to .64f), c(0xFF10244A), c(0xFF081530), .60f)
    fog(.66f, .80f, .34f)

    // near hills
    ridge(listOf(-.1f to .76f, .25f to .72f, .55f to .79f, .85f to .74f, 1.1f to .77f), c(0xFF0A1830), c(0xFF050C1A), .72f)
    fog(.78f, .90f, .26f)

    // pine trees in the foreground
    val tree = c(0xFF040A16)
    fun pine(x: Float, base: Float, th: Float) {
        val tw = th * .42f
        for (i in 0 until 4) {
            val top = base - th + th * .22f * i
            val half = tw * (.35f + .22f * i)
            val p = Path().apply { moveTo(x * w, top * h); lineTo(x * w - half * w, (top + th * .36f) * h); lineTo(x * w + half * w, (top + th * .36f) * h); close() }
            drawPath(p, tree)
        }
        drawRect(tree, Offset(x * w - w * .004f, base * h), androidx.compose.ui.geometry.Size(w * .008f, h * .03f))
    }
    for ((x, b, t) in listOf(
        Triple(.04f, .94f, .16f), Triple(.14f, .97f, .13f), Triple(.25f, .99f, .10f),
        Triple(.74f, .99f, .09f), Triple(.84f, .96f, .14f), Triple(.94f, .93f, .18f), Triple(1.02f, .98f, .13f),
    )) pine(x, b, t)
    drawRect(tree, Offset(0f, .97f * h), androidx.compose.ui.geometry.Size(w, .03f * h))

    // darken the top a little so text stays readable
    drawRect(Brush.verticalGradient(listOf(Color(0x66000000), Color(0x00000000)), startY = 0f, endY = h * .36f), size = androidx.compose.ui.geometry.Size(w, h * .36f))
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
