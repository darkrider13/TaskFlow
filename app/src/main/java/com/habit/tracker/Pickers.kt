package com.habit.tracker

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate


/** One scrolling wheel (44dp rows, three visible, middle one selected) - same idea as the original time picker. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelColumn(items: List<String>, start: Int, loop: Boolean, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val p = LocalPal.current
    val n = items.size
    val reps = if (loop) 7 else 1
    val first = (if (loop) 3 * n else 0) + start
    val state = rememberLazyListState(initialFirstVisibleItemIndex = first)
    val fling = rememberSnapFlingBehavior(state)
    val itemPx = with(LocalDensity.current) { 44.dp.toPx() }
    val sel by remember { derivedStateOf { state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset >= itemPx / 2) 1 else 0 } }
    val scope = rememberCoroutineScope()
    val moving = state.isScrollInProgress
    LaunchedEffect(sel, moving) {
        if (!moving) {
            val k = sel.coerceIn(0, reps * n - 1)
            onSelect(k % n)
            if (loop && (k < 2 * n || k >= 5 * n)) state.scrollToItem(3 * n + k % n)
        }
    }
    LazyColumn(modifier.height(132.dp), state = state, flingBehavior = fling) {
        item { Spacer(Modifier.height(44.dp)) }
        itemsIndexed(List(reps * n) { items[it % n] }) { i, label ->
            val dist = Math.abs(i - sel)
            Box(
                Modifier.fillMaxWidth().height(44.dp).alpha(if (dist == 0) 1f else if (dist == 1) .35f else 0f)
                    .tap { scope.launch { state.animateScrollToItem(i) } },
                contentAlignment = Alignment.Center,
            ) { Tx(label, size = 24.sp, weight = FontWeight.Bold, color = if (dist == 0) p.ink else p.muted) }
        }
        item { Spacer(Modifier.height(44.dp)) }
    }
}

@Composable
fun PickerCard(icon: String, title: String, onCancel: () -> Unit, onSet: () -> Unit, setColor: Color = Color.White, body: @Composable ColumnScope.() -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(32.dp)
    DisposableEffect(Unit) { UiState.pickers++; onDispose { UiState.pickers-- } }
    // a real popup window on top of everything (so it is never cut off inside the form sheet)
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        val view = LocalView.current
        SideEffect {
            val w = (view.parent as? DialogWindowProvider)?.window
            w?.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            w?.setDimAmount(0.6f)
            if (w != null && android.os.Build.VERSION.SDK_INT >= 31) {
                w.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val lp = w.attributes
                lp.blurBehindRadius = 28
                w.attributes = lp
            }
        }
        Box(Modifier.fillMaxSize().tap { onCancel() }.padding(16.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 400.dp).fillMaxWidth().clip(shape)
                    .background(if (p.dark) Color(0xFF1C1C1E) else Color(0xFAFFFFFF))
                    .border(1.dp, p.edge, shape).tap { }
                    .padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 22.dp),
            ) {
                Row(Modifier.padding(start = 6.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Ic(icon, size = 22.dp)
                    Spacer(Modifier.width(12.dp))
                    Tx(title, size = 18.sp, weight = FontWeight.Bold)
                }
                body()
                Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(18.dp)).background(p.glass).border(1.dp, p.edge, RoundedCornerShape(18.dp)).tap(onCancel),
                        contentAlignment = Alignment.Center,
                    ) { Tx(Str.t("cancelT"), size = 16.sp, weight = FontWeight.Bold) }
                    Box(
                        Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(18.dp)).background(setColor).border(1.dp, p.edge, RoundedCornerShape(18.dp)).tap(onSet),
                        contentAlignment = Alignment.Center,
                    ) { Tx(Str.t("setB"), size = 16.sp, color = Color(0xFF041014), weight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
fun WheelBand() {
    val p = LocalPal.current
    Box(Modifier.fillMaxWidth().padding(top = 44.dp).height(44.dp).clip(RoundedCornerShape(16.dp)).background(p.field).border(1.dp, p.fieldEdge, RoundedCornerShape(16.dp)))
}

/** 12-hour time picker (hour, minute, AM/PM). Returns "HH:mm" (24-hour) like the original. */
@Composable
fun TimePickerDlg(initial: String, onCancel: () -> Unit, onSet: (String) -> Unit) {
    val p = LocalPal.current
    val v = if (Regex("^\\d\\d:\\d\\d$").matches(initial)) initial else "09:00"
    val H = v.substring(0, 2).toInt()
    val M = v.substring(3, 5).toInt()
    var h by remember { mutableIntStateOf((if (H % 12 == 0) 12 else H % 12) - 1) }
    var m by remember { mutableIntStateOf(M) }
    var pm by remember { mutableIntStateOf(if (H >= 12) 1 else 0) }
    val ampm = if (Str.isBn) listOf("এএম", "পিএম") else listOf("AM", "PM")
    PickerCard("clock", Str.t("pickT"), onCancel, {
        val h12 = h + 1
        val h24 = (h12 % 12) + (if (pm == 1) 12 else 0)
        onSet(Str.pad2(h24) + ":" + Str.pad2(m))
    }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
            Tx(Str.t("hrL"), Modifier.weight(1f), size = 13.sp, color = p.muted, align = TextAlign.Center)
            Spacer(Modifier.width(28.dp))
            Tx(Str.t("mnL"), Modifier.weight(1f), size = 13.sp, color = p.muted, align = TextAlign.Center)
            Spacer(Modifier.weight(1f))
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
            WheelBand()
            Row(Modifier.fillMaxWidth()) {
                WheelColumn(List(12) { Str.dig(Str.pad2(it + 1)) }, h, true, Modifier.weight(1f)) { h = it }
                Box(Modifier.width(28.dp).padding(top = 44.dp).height(44.dp), contentAlignment = Alignment.Center) { Tx(":", size = 26.sp, weight = FontWeight.Bold) }
                WheelColumn(List(60) { Str.dig(Str.pad2(it)) }, m, true, Modifier.weight(1f)) { m = it }
                WheelColumn(ampm, pm, false, Modifier.weight(1f)) { pm = it }
            }
        }
    }
}

/** Days-per-month picker: 00..31 or "every day of the month". Returns 0..31, or 32 for every day. */
@Composable
fun GoalPickerDlg(initial: Int, onCancel: () -> Unit, onSet: (Int) -> Unit) {
    var k by remember { mutableIntStateOf(initial) }
    val items = listOf(Str.dig("00")) + (1..31).map { Str.dig(Str.pad2(it)) } + listOf(Str.t("gaL"))
    PickerCard("cal", Str.t("gmL"), onCancel, { onSet(k) }) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
            WheelBand()
            WheelColumn(items, initial, true, Modifier.fillMaxWidth()) { k = it }
        }
    }
}

/** Date picker with three wheels (date, month, year) - same look as the time picker. Returns "yyyy-MM-dd". */
@Composable
fun DatePickerDlg(initial: String, onCancel: () -> Unit, onSet: (String) -> Unit) {
    val p = LocalPal.current
    val d0 = try { LocalDate.parse(initial) } catch (e: Exception) { LocalDate.now() }
    val y0 = 2000
    val y1 = 2100
    var d by remember { mutableIntStateOf(d0.dayOfMonth - 1) }
    var m by remember { mutableIntStateOf(d0.monthValue - 1) }
    var y by remember { mutableIntStateOf(d0.year.coerceIn(y0, y1) - y0) }
    val months = List(12) { Str.dig(LocalDate.of(2026, it + 1, 1).format(Str.fmt("MMM"))) }
    PickerCard("nav_cal", Str.t("pickD"), onCancel, {
        val yy = y0 + y
        val len = java.time.YearMonth.of(yy, m + 1).lengthOfMonth()
        onSet(LocalDate.of(yy, m + 1, minOf(d + 1, len)).toString())
    }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
            Tx(Str.t("dateL"), Modifier.weight(1f), size = 13.sp, color = p.muted, align = TextAlign.Center)
            Tx(Str.t("monL"), Modifier.weight(1f), size = 13.sp, color = p.muted, align = TextAlign.Center)
            Tx(Str.t("yrL"), Modifier.weight(1f), size = 13.sp, color = p.muted, align = TextAlign.Center)
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
            WheelBand()
            Row(Modifier.fillMaxWidth()) {
                WheelColumn(List(31) { Str.dig((it + 1).toString()) }, d, true, Modifier.weight(1f)) { d = it }
                WheelColumn(months, m, true, Modifier.weight(1f)) { m = it }
                WheelColumn(List(y1 - y0 + 1) { Str.dig((y0 + it).toString()) }, y, false, Modifier.weight(1f)) { y = it }
            }
        }
    }
}
