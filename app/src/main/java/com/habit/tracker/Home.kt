package com.habit.tracker

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import kotlin.math.roundToInt
import org.json.JSONObject
import java.time.LocalDate

/** Home tab: today's progress, tasks of today and the habits still to tick. */
@Composable
fun HomeScreen(
    rev: Int,
    todayKey: String,
    bottomPad: androidx.compose.ui.unit.Dp,
    onGoUp: () -> Unit,
    onToast: (String) -> Unit,
    onOpenHabit: (JSONObject) -> Unit,
    onEditTask: (JSONObject) -> Unit,
) {
    val p = LocalPal.current
    var homeTab by remember { mutableStateOf("task") }
    val openT = remember { mutableStateListOf<Long>() }

    val today = LocalDate.parse(todayKey)
    val all = Logic.todos()
    val td = all.filter { it.optString("date") == todayKey }
    val dn = td.count { it.optBoolean("done") }
    val fcat = UiState.fcat
    fun fl(l: List<JSONObject>) = if (fcat.isEmpty()) l else l.filter { it.optString("cat") == fcat }
    val ha = Logic.acts().filter { Logic.isSched(it, today) && !Logic.marksOf(it.optLong("id")).has(todayKey) }
    val ov = fl(all.filter { !it.optBoolean("done") && it.optString("date") < todayKey })

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // overdue banner
        if (ov.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp).glass().tap { onGoUp() }.padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tx(Str.f("ovN", Str.bn(ov.size), ov.size), size = 15.sp, color = Color(0xFFFF0000), weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Ic("chevr", size = 20.dp, tint = Color(0xFFFF0000))
            }
        }

        // hero card
        Column(Modifier.fillMaxWidth().padding(bottom = 4.dp).glass().padding(16.dp)) {
            Tx(Logic.heroDate(todayKey), size = 13.sp, color = p.muted)
            Spacer(Modifier.height(4.dp))
            if (td.isNotEmpty()) {
                Tx(Str.f("tasksDone", Str.bn(dn), Str.bn(td.size)), size = 18.sp, weight = FontWeight.Bold)
            } else {
                PlusText(Str.t("noToday"), size = 13.sp, color = p.ink, weight = FontWeight.Medium, align = androidx.compose.ui.text.style.TextAlign.Start)
            }
            Spacer(Modifier.height(12.dp))
            val frac = Logic.pct(dn, td.size) / 100f
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(6.dp)).background(p.glass2)) {
                Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(p.mono))
            }
        }

        // Task | Habit switch (red dot = something is waiting in the other list)
        val rdT = homeTab == "hab" && td.size > dn
        val rdH = homeTab == "task" && ha.isNotEmpty()
        Seg(
            listOf(SegItem("task", Str.t("segTask"), rdT), SegItem("hab", Str.t("segHabit"), rdH)),
            homeTab,
            { homeTab = it },
            Modifier.padding(top = 10.dp, bottom = 12.dp),
        )

        if (homeTab == "hab") {
            if (ha.isEmpty()) {
                EmptyCard { Tx(Str.t("noHabH"), size = 14.sp, color = p.muted, align = androidx.compose.ui.text.style.TextAlign.Center) }
            } else {
                for (a in ha) HabitCard(a, today, rev, onToast, onOpenHabit)
            }
        } else {
            if (td.isEmpty()) {
                EmptyCard { PlusText(Str.t("noToday"), size = 14.sp, color = p.muted) }
            } else {
                val chips = listOf("" to Str.t("cAll")) + Logic.catAll().map { it to Logic.catLbl(it) }
                ChipsRow(chips, fcat, onAdd = { UiState.showCats = true }, onSelAgain = { k -> if (k.isNotEmpty() && k !in Logic.builtinCats) UiState.editCat = k }) { UiState.fcat = it }
                val shown = fl(td)
                if (shown.isEmpty()) {
                    EmptyCard { Tx(Str.t("noRes"), size = 14.sp, color = p.muted, align = androidx.compose.ui.text.style.TextAlign.Center) }
                } else {
                    for (x in shown) {
                        val id = x.optLong("id")
                        TaskRow(x, rev, expandable = true, open = openT.contains(id), onToggleOpen = {
                            if (openT.contains(id)) openT.remove(id) else openT.add(id)
                        }, onEdit = { onEditTask(x) })
                    }
                }
            }
        }
        Spacer(Modifier.height(bottomPad))
    }
}

/** Habit card bg like the "pill" look: the habit's colour glows from the top-left corner and fades into dark, with a soft coloured border. */
@Composable
fun Modifier.tintGlass(c: Color, radius: androidx.compose.ui.unit.Dp = 16.dp, strength: Float = 1f): Modifier {
    val p = LocalPal.current
    if (!p.dark) return this.glass(radius)
    val shape = RoundedCornerShape(radius)
    val brush = Brush.linearGradient(listOf(c.copy(alpha = (0.30f * strength).coerceAtMost(1f)), c.copy(alpha = (0.07f * strength).coerceAtMost(1f))))
    return this
        .clip(shape)
        .background(brush)
        .border(1.dp, c.copy(alpha = (0.28f * strength).coerceAtMost(1f)), shape)
}

@Composable
fun HabitCard(a: JSONObject, today: LocalDate, rev: Int, onToast: (String) -> Unit, onOpen: (JSONObject) -> Unit) {
    val p = LocalPal.current
    val color = parseColor(a.optString("color"))
    val id = a.optLong("id")
    val s = Logic.stats(a, today.year, today.monthValue - 1)
    val sub = if (a.optInt("goalM", 0) > 0) {
        val g = Logic.mgoal(a, today.year, today.monthValue - 1)
        Str.f("monthGoal", Str.bn(minOf(s.month, g)), Str.bn(g))
    } else {
        Str.f("habSub", Str.bn(s.total), Str.bn(Logic.habCnt(a)))
    }
    // swipe to the LEFT = delete this habit (asks first, because all its ticks go too)
    SwipeToDelete(
        key = a, radius = 16.dp,
        onSwiped = { UiState.delHabit = a },
        holdOpen = UiState.delHabit === a,
        exit = UiState.removingHabit === a,
        onExited = { UiState.removingHabit = null; LogicExt.deleteHabit(a) },
    ) {
    Row(
        Modifier.fillMaxWidth()
            .tintGlass(color).tap { onOpen(a) }.height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Tx(a.optString("name"), size = 15.sp, weight = FontWeight.Bold)
                Tx(sub, size = 12.5.sp, color = p.muted)
            }
        }
    }
    }
}

@Composable
fun TaskRow(
    x: JSONObject, rev: Int, expandable: Boolean, open: Boolean, onToggleOpen: () -> Unit, onEdit: () -> Unit,
    showDate: Boolean = false, ovr: Boolean = false, left: Boolean = false,
) {
    val p = LocalPal.current
    val subs = x.optJSONArray("sub").list()
    val done = x.optBoolean("done")
    val hasSubs = subs.isNotEmpty()
    val sd = subs.count { it.optBoolean("d") }
    val cat = x.optString("cat")
    val rep = x.optString("rep")
    val repKey = when (rep) { "daily" -> "r1"; "weekly" -> "r2"; "monthly" -> "r3"; else -> "" }
    var gone by remember(x) { mutableStateOf(false) }

    SwipeToDelete(
        key = x, radius = 16.dp,
        onSwiped = { gone = true },
        exit = gone,
        onExited = { LogicExt.deleteTask(x) },
    ) {
        Row(
            Modifier.fillMaxWidth()
                .glass()
                .let { if (expandable && hasSubs) it.tap { onToggleOpen() } else it }
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = if (hasSubs) Alignment.Top else Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CheckBox(done, if (hasSubs) 20 else 26) {
                Logic.setDone(x, !done)
                Store.touch()
            }
            Column(Modifier.weight(1f)) {
                Tx(x.optString("text"), size = 15.sp, color = if (done) p.muted else p.ink, strike = done)
                val parts = ArrayList<@Composable () -> Unit>()
                if (showDate) parts.add { Tx(LogicExt.dayMonth(x.optString("date")), size = 12.sp, color = p.muted) }
                if (cat.isNotEmpty() && Logic.catOk(cat)) parts.add {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ic(Logic.catIc(cat), size = 12.dp, tint = p.muted)
                        Spacer(Modifier.width(4.dp))
                        Tx(Logic.catLbl(cat), size = 12.sp, color = p.muted)
                    }
                }
                if (repKey.isNotEmpty()) parts.add {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ic("repeat", size = 12.dp, tint = p.muted)
                        Spacer(Modifier.width(4.dp))
                        Tx(Str.t(repKey), size = 12.sp, color = p.muted)
                    }
                }
                if (hasSubs) parts.add {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ic("checksq", size = 12.dp, tint = p.muted)
                        Spacer(Modifier.width(4.dp))
                        Tx(Str.bn(sd) + "/" + Str.bn(subs.size), size = 12.sp, color = p.muted)
                    }
                }
                val tm = Str.t12(x.optString("time"))
                if (tm.isNotEmpty()) parts.add { Tx(tm, size = 12.sp, color = p.muted) }
                if (parts.isNotEmpty()) {
                    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        parts.forEachIndexed { i, part ->
                            if (i > 0) Tx("  ·  ", size = 12.sp, color = p.muted)
                            part()
                        }
                    }
                }
                if (hasSubs && (!expandable || open)) {
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        subs.forEach { z ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CheckBox(z.optBoolean("d"), 20) {
                                    z.put("d", !z.optBoolean("d"))
                                    val allDone = subs.all { it.optBoolean("d") }
                                    if (allDone && !x.optBoolean("done")) Logic.setDone(x, true)
                                    else if (!allDone && x.optBoolean("done")) Logic.setDone(x, false)
                                    Store.touch()
                                }
                                Tx(z.optString("t"), size = 14.sp, color = if (z.optBoolean("d")) p.muted else p.ink)
                            }
                        }
                    }
                }
                if (ovr) {
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RowBtn(Str.t("mvT")) { LogicExt.moveTask(x, 0) }
                        RowBtn(Str.t("mvM")) { LogicExt.moveTask(x, 1) }
                    }
                }
            }
            if (left) {
                val dd = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(Logic.todayKey()), LocalDate.parse(x.optString("date"))).toInt()
                val lbl = if (dd <= 0) Str.t("today") else if (dd == 1) Str.t("tmr") else Str.t("dLeft").replace("{n}", Str.bn(dd))
                Tx(lbl, size = 12.sp, color = p.muted)
            }
            Box(Modifier.clip(CircleShape).tap { onEdit() }.padding(6.dp)) {
                Ic("edit", size = 20.dp, tint = p.muted)
            }
        }
    }
}

/** Rounded square tick box used for tasks and subtasks. */
@Composable
fun CheckBox(checked: Boolean, sizeDp: Int, onClick: () -> Unit) {
    val p = LocalPal.current
    val r = if (sizeDp >= 26) 9.dp else 7.dp
    val shape = RoundedCornerShape(r)
    Box(
        Modifier.size(sizeDp.dp).clip(shape).border(2.dp, if (checked) p.mono else p.muted, shape).tap(onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Ic("check", size = (sizeDp - 8).dp, tint = p.mono, stroke = 3.2f)
    }
}
