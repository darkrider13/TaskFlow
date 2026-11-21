package com.habit.tracker

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

/** Things that live across screens: which month is shown, which habit is picked, open sheets. */
object UiState {
    var sel by mutableStateOf<Long?>(null)
    var vy by mutableIntStateOf(LocalDate.now().year)
    var vm by mutableIntStateOf(LocalDate.now().monthValue - 1)
    var pickKey by mutableStateOf<String?>(null)
    var dayKey by mutableStateOf<String?>(null)
    var edit by mutableStateOf<FormSpec?>(null)
    var backDay by mutableStateOf<String?>(null)
    var fcat by mutableStateOf("")
    var dlg by mutableStateOf<DlgSpec?>(null)
    var remSheet by mutableStateOf(false)
    var remObj by mutableStateOf<JSONObject?>(null)
    var showCats by mutableStateOf(false)
    /** key of the custom category whose edit/delete popup is open */
    var editCat by mutableStateOf<String?>(null)
    /** habit waiting for the "delete?" answer after a swipe on the Home tab */
    var delHabit by mutableStateOf<JSONObject?>(null)
    /** habit the user confirmed to delete - its card plays the slide-out animation, then deletes it */
    var removingHabit by mutableStateOf<JSONObject?>(null)
    /** number of picker popups (time / date / goal) currently open - everything behind them gets blurred */
    var pickers by mutableIntStateOf(0)
    /** the "Save to folder / Share" export chooser popup */
    var exportChoose by mutableStateOf(false)

    fun act(): JSONObject? = Logic.acts().find { it.optLong("id") == sel }
    fun resetToToday() {
        val n = LocalDate.now(); vy = n.year; vm = n.monthValue - 1; pickKey = null
    }
    /** bumped every time the Calendar page must start fresh (its scroll, list tab etc. reset too) */
    var calReset by mutableIntStateOf(0)
    /** Calendar back to its default page: this month, no date picked, no habit picked. */
    fun resetCalendar() {
        resetToToday(); sel = null; calReset++
    }
}

/** kind: "ok" (green tick), "err" (red cross) or "ask" (question with [icon]). */
class DlgSpec(val title: String, val text: String, val ok: String, val cancel: String? = null, val kind: String = "ok", val icon: String = "import", val onOk: () -> Unit = {})

class FormSpec(val kind: String, val act: JSONObject?, val task: JSONObject?, val dateKey: String? = null)

@Composable
fun CalendarScreen(rev: Int, todayKey: String, bottomPad: Dp, onOpenDay: (String) -> Unit) {
    val p = LocalPal.current
    val a = UiState.act()
    val vy = UiState.vy
    val vm = UiState.vm
    var cxTab by remember { mutableStateOf("hol") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // habit chips
        val acts = Logic.acts()
        if (acts.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (h in acts) {
                    val on = h.optLong("id") == UiState.sel
                    val shape = RoundedCornerShape(20.dp)
                    Box(
                        Modifier.height(34.dp).clip(shape)
                            .background(if (on) parseColor(h.optString("color")) else p.glass)
                            .border(1.dp, if (on) Color.Transparent else p.edge, shape)
                            .tap { UiState.sel = if (on) null else h.optLong("id") }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Tx(h.optString("name"), size = 13.sp, color = if (on) p.tink else p.ink, weight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1) }
                }
            }
        }
        if (a != null) HabitInfo(a, vy, vm, todayKey)

        // month name
        Box(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(bottom = 6.dp), contentAlignment = Alignment.Center) {
            Tx(Logic.let { LogicExt.monthName(vy, vm) }, size = 17.sp, weight = FontWeight.Bold)
        }

        // grid (swipe sideways to change month)
        val density = androidx.compose.ui.platform.LocalDensity.current
        val thr = with(density) { 45.dp.toPx() }
        Column(
            Modifier.fillMaxWidth().pointerInput(Unit) {
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onDragEnd = {
                        if (Math.abs(dx) > thr) {
                            var y = UiState.vy; var m = UiState.vm
                            if (dx < 0) { m++; if (m > 11) { m = 0; y++ } } else { m--; if (m < 0) { m = 11; y-- } }
                            UiState.vy = y; UiState.vm = m
                        }
                    },
                    onHorizontalDrag = { _, d -> dx += d },
                )
            },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (w in Str.arr("wd")) Box(Modifier.weight(1f).padding(vertical = 4.dp), contentAlignment = Alignment.Center) { Tx(w, size = 12.sp, color = p.muted) }
            }
            val ym = YearMonth.of(vy, vm + 1)
            val firstDow = ym.atDay(1).dayOfWeek.value % 7
            val last = ym.lengthOfMonth()
            val cells = firstDow + last
            val rows = (cells + 6) / 7
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in 0 until 7) {
                        val d = r * 7 + c - firstDow + 1
                        if (d < 1 || d > last) Box(Modifier.weight(1f).height(44.dp)) else DayCell(ym.atDay(d), a, todayKey, Modifier.weight(1f), onOpenDay)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // below the grid: upcoming holidays / events (only when no habit is picked)
        if (a == null) {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((k, lbl) in listOf("hol" to "cxHol", "evt" to "cxEvt")) {
                    if (k == "evt") Spacer(Modifier.weight(1f)) // pushes "upcoming events" to the far right
                    val on = cxTab == k
                    val shape = RoundedCornerShape(20.dp)
                    Box(
                        Modifier.height(34.dp).clip(shape).background(if (on) p.ac else p.glass).border(1.dp, if (on) Color.Transparent else p.edge, shape)
                            .tap { cxTab = k }.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Tx(Str.t(lbl), size = 13.sp, color = if (on) p.tink else p.ink, weight = if (on) FontWeight.Bold else FontWeight.Normal) }
                }
            }
            if (cxTab == "evt") {
                val items = Logic.todos().filter { !it.optBoolean("done") && it.optString("date") >= todayKey }
                    .sortedBy { it.optString("date") + it.optString("time").ifEmpty { "99" } }
                if (items.isEmpty()) EmptyCard { Tx(Str.t("cxNoE"), size = 14.sp, color = p.muted, align = TextAlign.Center) }
                else EventList(items.take(60).map { Triple(it.optString("date"), it.optString("text"), false) }, todayKey, onOpenDay, "evt")
            } else {
                val ymv = YearMonth.of(vy, vm + 1)
                val t0 = LocalDate.parse(todayKey)
                var d = if (ymv.atDay(1).isBefore(t0)) t0 else ymv.atDay(1)
                val end = ymv.atEndOfMonth()
                val hols = ArrayList<Triple<String, String, Boolean>>()
                while (!d.isAfter(end)) {
                    val n = Hol.info(d.toString()).gx
                    if (n.isNotEmpty()) hols.add(Triple(d.toString(), n.joinToString(" / "), true))
                    d = d.plusDays(1)
                }
                if (hols.isEmpty()) EmptyCard { Tx(Str.t("cxNoH"), size = 14.sp, color = p.muted, align = TextAlign.Center) }
                else EventList(hols, todayKey, onOpenDay, "hol$vy-$vm")
            }
        }
        Spacer(Modifier.height(bottomPad))
    }
}

private val EV_CARD_H = 60.dp
private val EV_GAP = 8.dp
private const val EV_SHOWN = 3

/** Upcoming holidays / events: exactly 3 full cards are visible; the rest scroll inside the list and always settle on a whole card. */
@Composable
fun EventList(items: List<Triple<String, String, Boolean>>, todayKey: String, onOpenDay: (String) -> Unit, listKey: String) {
    key(listKey) {
        val state = androidx.compose.foundation.lazy.rememberLazyListState()
        val fling = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(state)
        val n = minOf(items.size, EV_SHOWN)
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.fillMaxWidth().height(EV_CARD_H * n + EV_GAP * (n - 1)),
            state = state, flingBehavior = fling, verticalArrangement = Arrangement.spacedBy(EV_GAP),
        ) {
            itemsIndexed(items) { i, ev ->
                EventCard(ev.first, ev.second, todayKey, onOpenDay, hol = ev.third, first = i == 0)
            }
        }
    }
}

@Composable
fun EventCard(k: String, title: String, todayKey: String, onOpenDay: (String) -> Unit, hol: Boolean = false, first: Boolean = false) {
    val p = LocalPal.current
    val n = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(todayKey), LocalDate.parse(k)).toInt()
    val left = if (n <= 0) Str.t("cxToday") else Str.f("cxLeft", Str.bn(n))
    val ds = Str.wdDate(LocalDate.parse(k))
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().height(EV_CARD_H).glass(16.dp)
            .let { if (first && hol) it.border(1.dp, Color(0x99FF6B6B), shape) else it }
            .tap { val d = LocalDate.parse(k); UiState.vy = d.year; UiState.vm = d.monthValue - 1; UiState.pickKey = k; onOpenDay(k) }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Tx(ds, Modifier.weight(1f), size = 13.sp, color = if (hol) Color(0xFFFF6B6B) else Color(0xFF60A5FA))
        Tx(title, Modifier.widthIn(max = 130.dp), size = 13.sp, weight = FontWeight.SemiBold, align = TextAlign.Center, maxLines = 2)
        Tx(left, Modifier.weight(1f), size = 13.sp, color = p.muted, align = TextAlign.End)
    }
}

@Composable
fun DayCell(date: LocalDate, a: JSONObject?, todayKey: String, modifier: Modifier, onOpenDay: (String) -> Unit) {
    val p = LocalPal.current
    val k = date.toString()
    val today = k == todayKey
    val picked = k == UiState.pickKey
    val dt = if (a == null) Logic.todos().filter { it.optString("date") == k } else emptyList()
    val allDone = dt.isNotEmpty() && dt.all { it.optBoolean("done") }
    var cls = ""
    var shownMark: String? = null
    var off = false
    if (a != null) {
        val id = a.optLong("id")
        val marks = Logic.marksOf(id)
        val nos = Store.s.getJSONObject("nos").optJSONObject(id.toString())
        if (marks.has(k)) shownMark = "ck"
        else if (nos?.has(k) == true) shownMark = "x"
        else if (!Logic.isSched(a, date)) off = true
        else if (k >= a.optString("start") && k < todayKey) shownMark = "x"
    }
    val tick = allDone && shownMark == null
    val pending = dt.isNotEmpty() && !allDone
    val hinfo = Hol.info(k)
    val isHol = hinfo.g.isNotEmpty()
    val shape = RoundedCornerShape(12.dp)
    val bd = if (today || picked) 2.dp else 1.dp
    Box(
        modifier.height(44.dp).alpha(if (off) .4f else 1f).clip(shape)
            .background(if (tick) (if (p.dark) Color(0xFF1F1F1F) else Color(0x0F10253A)) else p.daybg)
            .border(if (tick) 0.dp else bd, if (today) p.mono else if (picked) Color(0xFFC9B6FF) else p.edge, shape)
            .tap { UiState.pickKey = k; onOpenDay(k) },
        contentAlignment = Alignment.Center,
    ) {
        Tx(
            Str.bn(date.dayOfMonth), size = 14.sp,
            color = if (pending) Color(0xFF38BDF8) else if (isHol) Color(0xFFFF0000) else if (hinfo.o.isNotEmpty()) Color(0xFFFFB800) else p.ink,
            weight = if (tick) FontWeight.Bold else FontWeight.Normal,
        )
        when (shownMark) {
            "ck" -> Ic("check", size = 34.dp, tint = Color(0xA600FF00), stroke = 2.6f)
            "x" -> Ic("x", size = 34.dp, tint = Color(0xA6FF0000), stroke = 2.6f)
        }
        if (pending) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(5.dp).clip(androidx.compose.foundation.shape.CircleShape).background(p.mono))
    }
}

@Composable
fun HabitInfo(a: JSONObject, vy: Int, vm: Int, todayKey: String) {
    val p = LocalPal.current
    val color = parseColor(a.optString("color"))
    val id = a.optLong("id")
    val mk = Logic.marksOf(id)
    val nk = Store.s.getJSONObject("nos").optJSONObject(id.toString()) ?: JSONObject()
    val start = a.optString("start")
    val ym = YearMonth.of(vy, vm + 1)
    var ck = 0
    var cx = 0
    var rt: Int? = null
    val goalM = a.optInt("goalM", 0)
    if (goalM == 0) {
        val lk = ym.atEndOfMonth().toString()
        val ek = if (lk < todayKey) lk else todayKey
        val it = mk.keys()
        while (it.hasNext()) { val k = it.next(); if (k >= start && k <= ek) ck++ }
        var r = 0
        var c = try { LocalDate.parse(start) } catch (e: Exception) { LocalDate.now().plusDays(1) }
        val e = LocalDate.parse(ek)
        var i = 0
        while (i < 20000 && !c.isAfter(e)) {
            val k = c.toString()
            val sc = Logic.isSched(a, c)
            if (!mk.has(k) && (nk.has(k) || (sc && k < todayKey))) cx++
            if ((sc && k < todayKey) || mk.has(k)) r++
            c = c.plusDays(1); i++
        }
        rt = r
    } else {
        for (d in 1..ym.lengthOfMonth()) {
            val dt = ym.atDay(d)
            val k = dt.toString()
            if (mk.has(k)) ck++ else if (nk.has(k) || (Logic.isSched(a, dt) && k >= start && k < todayKey)) cx++
        }
    }
    val s = Logic.stats(a, vy, vm - 0)
    val gm = Logic.mgoal(a, vy, vm)
    val gt = a.optInt("goalT", 0)

    @Composable fun Cnt(ic: String, n: Int, tint: Color) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Ic(ic, size = 16.dp, tint = tint, stroke = 2.8f)
            Tx(Str.bn(n), size = 14.sp, color = tint)
        }
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).glass().padding(14.dp)) {
        if (gm == 0) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { Cnt("check", ck, Color(0xFF00C000)) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Tx(Str.bn(rt ?: s.total), size = 20.sp, weight = FontWeight.Bold)
                    Tx(Str.t("total"), size = 12.sp, color = p.muted)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { Cnt("x", cx, Color(0xFFFF0000)) }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Tx(Str.bn(rt ?: s.total), size = 20.sp, weight = FontWeight.Bold)
                    Tx(Str.t("total"), size = 12.sp, color = p.muted)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Tx(Str.bn(minOf(maxOf(0, gm - s.month), LogicExt.remDays(a, vy, vm))), size = 20.sp, weight = FontWeight.Bold)
                    Tx(Str.t("left"), size = 12.sp, color = p.muted)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Tx(Str.f("monthGoal", Str.bn(minOf(s.month, gm)), Str.bn(gm)), size = 13.5.sp)
                Spacer(Modifier.width(12.dp))
                Cnt("check", ck, Color(0xFF00C000)); Spacer(Modifier.width(12.dp)); Cnt("x", cx, Color(0xFFFF0000))
                Spacer(Modifier.weight(1f))
                Tx(Str.bn(Logic.pct(s.month, gm)) + "%", size = 13.5.sp)
            }
            Bar(Logic.pct(s.month, gm) / 100f, color)
        }
        if (gt > 0) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 5.dp)) {
                Tx(Str.f("totalGoal", Str.bn(minOf(s.total, gt)), Str.bn(gt)), Modifier.weight(1f), size = 13.5.sp)
                Tx(Str.bn(Logic.pct(s.total, gt)) + "%", size = 13.5.sp)
            }
            Bar(Logic.pct(s.total, gt) / 100f, color)
        }
    }
}

@Composable
fun Bar(frac: Float, color: Color) {
    val p = LocalPal.current
    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(6.dp)).background(p.glass2)) {
        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(color))
    }
}
