package com.habit.tracker

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Click without the grey ripple (the original app has none). */
fun Modifier.tap(onClick: () -> Unit): Modifier = composed {
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
}

/** The "glass" card look of the original app. */
@Composable
fun Modifier.glass(radius: Dp = 18.dp): Modifier {
    val p = LocalPal.current
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(p.glass)
        .border(1.dp, p.edge, shape)
}

@Composable
fun Tx(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 14.sp,
    color: Color = LocalPal.current.ink,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign? = null,
    strike: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontWeight = weight,
            textAlign = align ?: TextAlign.Unspecified,
            textDecoration = if (strike) TextDecoration.LineThrough else null,
            lineHeight = lineHeight,
        ),
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
    )
}

/** Stroke icon from the original icon set (24x24). */
@Composable
fun Ic(
    name: String,
    size: Dp = 20.dp,
    tint: Color = LocalPal.current.ink,
    stroke: Float = 2f,
    modifier: Modifier = Modifier,
) {
    val parts = IconData.all[name] ?: IconExtra.all[name] ?: return
    val paths = remember(name) { parts.map { PathParser().parsePathString(it.d).toPath() to it.fill } }
    Canvas(modifier.size(size)) {
        val sc = this.size.minDimension / 24f
        scale(sc, sc, pivot = Offset.Zero) {
            for ((i, pf) in paths.withIndex()) {
                val (path, fill) = pf
                val part = parts[i]
                withTransform({
                    translate(part.tx, part.tx)
                    scale(part.s, part.s, Offset.Zero)
                }) {
                    if (fill) drawPath(path, tint)
                    else drawPath(path, tint, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}

class SegItem(val key: String, val label: String, val dot: Boolean = false)

/** Two-button switch (Task | Habit). */
@Composable
fun Seg(items: List<SegItem>, sel: String, onSel: (String) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(p.glass)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (it in items) {
            val on = it.key == sel
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (on) p.ac else Color.Transparent)
                    .tap { onSel(it.key) }
                    .padding(10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tx(it.label, size = 14.sp, color = if (on) p.tink else p.ink, weight = if (on) FontWeight.Bold else FontWeight.Normal)
                if (it.dot) {
                    Box(Modifier.padding(start = 6.dp).size(6.dp).clip(CircleShape).background(p.cross))
                }
            }
        }
    }
}

/** Category chips. They share the full row width when they fit, and scroll sideways when they do not. */
@Composable
fun ChipsRow(items: List<Pair<String, String>>, sel: String, onAdd: (() -> Unit)? = null, onSel: (String) -> Unit) {
    val p = LocalPal.current
    val gap = 6.dp
    val hasAdd = onAdd != null
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        val maxW = this.maxWidth
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            androidx.compose.ui.layout.Layout(
                modifier = Modifier.widthIn(min = maxW),
                content = {
                    for ((k, label) in items) {
                        val on = k == sel
                        val shape = RoundedCornerShape(20.dp)
                        Box(
                            Modifier
                                .height(28.dp)
                                .clip(shape)
                                .background(if (on) p.ac else p.glass)
                                .border(1.dp, if (on) Color.Transparent else p.edge, shape)
                                .tap { onSel(k) }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Tx(label, size = 12.sp, color = if (on) p.tink else p.ink, weight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                        }
                    }
                    if (onAdd != null) {
                        val shape = RoundedCornerShape(20.dp)
                        Box(
                            Modifier.height(28.dp).clip(shape).background(p.glass).border(1.dp, p.edge, shape).tap { onAdd() }.padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Ic("plus", size = 14.dp) }
                    }
                },
            ) { measurables, cons ->
                val g = gap.roundToPx()
                val hPx = 28.dp.roundToPx()
                // natural widths come from intrinsics, so every chip is measured exactly ONCE (Compose forbids measuring twice)
                val nat = measurables.map { it.maxIntrinsicWidth(hPx) }
                val total = nat.sum() + g * (nat.size - 1).coerceAtLeast(0)
                val width = maxOf(total, cons.minWidth)
                // extra space is shared by the label chips only (the "+" button keeps its own size)
                val grow = if (hasAdd) nat.size - 1 else nat.size
                val extra = if (grow > 0) (width - total).coerceAtLeast(0) / grow else 0
                val placed = measurables.mapIndexed { i, m ->
                    val w = nat[i] + if (i < grow) extra else 0
                    m.measure(androidx.compose.ui.unit.Constraints.fixedWidth(w))
                }
                val h = placed.maxOfOrNull { it.height } ?: 0
                val used = placed.sumOf { it.width } + g * (placed.size - 1).coerceAtLeast(0)
                layout(maxOf(used, width), h) {
                    var x = 0
                    placed.forEach { it.placeRelative(x, 0); x += it.width + g }
                }
            }
        }
    }
}

/** Text with the "+" replaced by the small round add-button, like the original empty message. */
@Composable
fun PlusText(text: String, size: TextUnit, color: Color, weight: FontWeight = FontWeight.Normal, align: TextAlign = TextAlign.Center) {
    val p = LocalPal.current
    val i = text.indexOf('+')
    if (i < 0) {
        Tx(text, size = size, color = color, weight = weight, align = align)
        return
    }
    val s = buildAnnotatedString {
        append(text.substring(0, i))
        appendInlineContent("fab", "+")
        append(text.substring(i + 1))
    }
    val dot = size * 1.55f
    BasicText(
        s,
        style = TextStyle(color = color, fontSize = size, fontWeight = weight, textAlign = align),
        inlineContent = mapOf(
            "fab" to InlineTextContent(Placeholder(dot * 1.15f, dot, PlaceholderVerticalAlign.TextCenter)) {
                Box(Modifier.fillMaxSize().padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.fillMaxHeight().aspectRatio(1f).clip(CircleShape).background(Color.Black).border(1.dp, p.edge, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Ic("plus", size = 12.dp, tint = Color.White, stroke = 2.4f) }
                }
            },
        ),
    )
}

@Composable
fun EmptyCard(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().glass().padding(18.dp), contentAlignment = Alignment.Center) { content() }
}

/** Centered popup panel with dimmed background (the original "sheet"). */
@Composable
fun SheetFrame(title: String, onClose: () -> Unit, centerTitle: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPal.current
    BackHandler(onBack = onClose)
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier.fillMaxSize().background(p.shade).tap { onClose() }.imePadding().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .clip(shape)
                .background(if (p.dark) Color(0xF2161618) else Color(0xF2F4F9FF))
                .border(1.dp, p.edge, shape)
                .tap { }
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (centerTitle) {
                Box(Modifier.fillMaxWidth().padding(bottom = 12.dp).heightIn(min = 36.dp), contentAlignment = Alignment.Center) {
                    Tx(title, size = 18.sp, weight = FontWeight.Bold, align = TextAlign.Center, modifier = Modifier.padding(horizontal = 40.dp))
                    Box(Modifier.align(Alignment.CenterEnd).size(36.dp).clip(CircleShape).tap { onClose() }, contentAlignment = Alignment.Center) {
                        Ic("x", size = 22.dp, stroke = 2.2f)
                    }
                }
            } else Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Tx(title, size = 18.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(Modifier.size(36.dp).clip(CircleShape).tap { onClose() }, contentAlignment = Alignment.Center) {
                    Ic("x", size = 22.dp, stroke = 2.2f)
                }
            }
            content()
        }
    }
}

@Composable
fun SectionTitle(text: String, first: Boolean = false) {
    val p = LocalPal.current
    Tx(text, size = 15.sp, color = p.muted, weight = FontWeight.SemiBold, modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = if (first) 4.dp else 18.dp, bottom = 8.dp))
}

/** Small text box (height 34, like the original inputs). */
@Composable
fun TfInput(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(9.dp)
    val bg = if (p.dark) p.glass2 else Color.White
    val fg = if (p.dark) p.ink else Color.Black
    val bd = if (p.dark) p.edge else Color(0x4010253A)
    BasicTextField(
        value = value,
        onValueChange = { onChange(normDigits(it)) },
        visualTransformation = BnDigits,
        singleLine = true,
        textStyle = TextStyle(color = fg, fontSize = 14.sp),
        cursorBrush = SolidColor(fg),
        keyboardOptions = KeyboardOptions.Default,
        modifier = modifier.height(38.dp).clip(shape).background(bg).border(1.dp, bd, shape),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Tx(hint, size = 14.sp, color = p.muted)
                inner()
            }
        },
    )
}

/** Small button used in rows (height 34). */
@Composable
fun RowBtn(label: String, modifier: Modifier = Modifier, icon: String? = null, color: Color? = null, onClick: () -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier.height(34.dp).clip(shape).background(p.glass2).border(1.dp, p.edge, shape).tap(onClick).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Ic(icon, size = 16.dp)
            Spacer(Modifier.width(6.dp))
        }
        Tx(label, size = 13.sp, maxLines = 1, color = color ?: p.ink, weight = if (color != null) FontWeight.Bold else FontWeight.Normal)
    }
}

/** Big coloured button (accent colour). */
@Composable
fun BigBtn(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.ac).tap(onClick).padding(13.dp),
        contentAlignment = Alignment.Center,
    ) { Tx(label, size = 16.sp, color = p.tink, weight = FontWeight.Bold) }
}

/** Footer social icons (not part of the main icon set). */
object IconExtra {
    val all: Map<String, List<IconData.Part>> = mapOf(
        "fb" to listOf(IconData.Part("M14 8h3V4h-3a4 4 0 0 0-4 4v2H7v4h3v8h4v-8h3l1-4h-4V9a1 1 0 0 1 1-1z", true)),
        "wa" to listOf(
            IconData.Part("M3 21L4.2 16.5A9 9 0 1 1 7.5 19.8Z", false),
            IconData.Part("M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.127.96.361 1.903.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0 1 22 16.92z", true, 0.4f, 7.2f),
        ),
        "ig" to listOf(
            IconData.Part("M8,3h8a5,5 0 0 1 5,5v8a5,5 0 0 1 -5,5h-8a5,5 0 0 1 -5,-5v-8a5,5 0 0 1 5,-5z", false),
            IconData.Part("M8,12a4,4 0 1,0 8,0a4,4 0 1,0 -8,0z", false),
            IconData.Part("M17.5 6.5h.01", false),
        ),
        "unlock" to listOf(
            IconData.Part("M7,11h10a2,2 0 0 1 2,2v6a2,2 0 0 1 -2,2h-10a2,2 0 0 1 -2,-2v-6a2,2 0 0 1 2,-2z", false),
            IconData.Part("M8 11V7a4 4 0 0 1 7.5-1.5", false),
        ),
        "hash" to listOf(IconData.Part("M5 9h14M5 15h14M10 4L8 20M16 4l-2 16", false)),
        "pattern" to listOf(
            IconData.Part("M4.3,6a1.7,1.7 0 1,0 3.4,0a1.7,1.7 0 1,0 -3.4,0z", false),
            IconData.Part("M16.3,6a1.7,1.7 0 1,0 3.4,0a1.7,1.7 0 1,0 -3.4,0z", false),
            IconData.Part("M4.3,18a1.7,1.7 0 1,0 3.4,0a1.7,1.7 0 1,0 -3.4,0z", false),
            IconData.Part("M16.3,18a1.7,1.7 0 1,0 3.4,0a1.7,1.7 0 1,0 -3.4,0z", false),
            IconData.Part("M7.5 6h9M17 7.5L7 16.5M7.5 18h9", false),
        ),
        "tg" to listOf(IconData.Part("M22 2L11 13", false), IconData.Part("M22 2l-7 20-4-9-9-4z", false)),
    )
}


/** Bangla mode: digits typed in any input box are shown as Bangla digits (the stored text keeps English digits). */
object BnDigits : androidx.compose.ui.text.input.VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString) =
        androidx.compose.ui.text.input.TransformedText(
            androidx.compose.ui.text.AnnotatedString(Str.dig(text.text)),
            androidx.compose.ui.text.input.OffsetMapping.Identity,
        )
}

/** Bangla digits -> English digits, so everything stored/parsed stays English. */
fun normDigits(s: String): String {
    if (s.none { it in '\u09E6'..'\u09EF' }) return s
    return s.map { c -> if (c in '\u09E6'..'\u09EF') '0' + (c - '\u09E6') else c }.joinToString("")
}
