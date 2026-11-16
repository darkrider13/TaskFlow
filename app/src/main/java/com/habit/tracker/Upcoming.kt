package com.habit.tracker

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.time.LocalDate

/** Upcoming tab: overdue + coming tasks grouped by day, search, and the daily reminders list. */
@Composable
fun UpcomingScreen(rev: Int, todayKey: String, bottomPad: Dp, onEditTask: (JSONObject) -> Unit, onEditHabit: (JSONObject) -> Unit) {
    val p = LocalPal.current
    var upTab by remember { mutableStateOf("task") }
    var q by remember { mutableStateOf("") }
    val openT = remember { mutableStateListOf<Long>() }
    val isT = upTab == "task"
    val fcat = UiState.fcat
    fun fl(l: List<JSONObject>) = if (fcat.isEmpty()) l else l.filter { it.optString("cat") == fcat }
    val all = Logic.todos()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Seg(listOf(SegItem("task", Str.t("segTask")), SegItem("rem", Str.t("dailyR"))), upTab, { upTab = it }, Modifier.padding(bottom = 6.dp))

        if (!isT) {
            val acts = Logic.acts()
            if (acts.isEmpty()) EmptyCard { Tx(Str.t("noDailyR"), size = 14.sp, color = p.muted, align = TextAlign.Center) }
            else for (a in acts) {
                val color = parseColor(a.optString("color"))
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).glass().tap { onEditHabit(a) }.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(4.dp).fillMaxHeight().background(color))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Tx(a.optString("name"), size = 15.sp, weight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Ic("clock", size = 13.dp, tint = p.muted)
                            Spacer(Modifier.width(4.dp))
                            Tx(LogicExt.evLabel(a), size = 12.5.sp, color = p.muted)
                        }
                    }
                }
            }
        } else {
            // search box
            val shape = RoundedCornerShape(12.dp)
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                BasicTextField(
                    value = q, onValueChange = { q = normDigits(it) }, visualTransformation = BnDigits, singleLine = true,
                    textStyle = TextStyle(color = if (p.dark) p.ink else Color.Black, fontSize = 14.sp),
                    cursorBrush = SolidColor(p.ink),
                    modifier = Modifier.fillMaxWidth().height(40.dp).clip(shape).background(if (p.dark) p.glass2 else Color.White)
                        .border(1.dp, if (p.dark) p.edge else Color(0x4010253A), shape),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxSize().padding(start = 38.dp, end = 12.dp), contentAlignment = Alignment.CenterStart) {
                            if (q.isEmpty()) Tx(Str.t("searchPh"), size = 14.sp, color = p.muted)
                            inner()
                        }
                    },
                )
                Box(Modifier.padding(start = 12.dp).height(40.dp), contentAlignment = Alignment.CenterStart) { Ic("search", size = 18.dp, tint = p.muted) }
            }
            Spacer(Modifier.height(4.dp))
            ChipsRow(listOf("" to Str.t("cAll")) + Logic.catAll().map { it to Logic.catLbl(it) }, fcat, onAdd = { UiState.showCats = true }, onSelAgain = { k -> if (k.isNotEmpty() && k !in Logic.builtinCats) UiState.editCat = k }) { UiState.fcat = it }

            val qq = q.trim().lowercase()
            if (qq.isNotEmpty()) {
                val m = fl(all.filter { (it.optString("text") + " " + it.optJSONArray("sub").list().joinToString(" ") { z -> z.optString("t") }).lowercase().contains(qq) })
                    .sortedByDescending { it.optString("date") }
                if (m.isEmpty()) EmptyCard { Tx(Str.t("noRes"), size = 14.sp, color = p.muted, align = TextAlign.Center) }
                else for (x in m) TaskRow(x, rev, expandable = false, open = false, onToggleOpen = {}, onEdit = { onEditTask(x) }, showDate = true)
            } else {
                val pend = fl(all.filter { !it.optBoolean("done") || it.optString("date") >= todayKey })
                    .sortedBy { it.optString("date") + it.optString("time").ifEmpty { "99" } }
                val over = pend.filter { it.optString("date") < todayKey }
                val rest = pend.filter { it.optString("date") >= todayKey }
                if (over.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Tx(Str.t("over") + " (" + Str.bn(over.size) + ")", Modifier.weight(1f), size = 15.sp, color = p.muted, weight = FontWeight.SemiBold)
                        // FIX: the original had the "move all to today" text and handler but never showed the button
                        RowBtn(Str.t("mvAll")) { over.forEach { LogicExt.moveTask(it, 0, touch = false) }; Store.touch() }
                    }
                    for (x in over) {
                        val id = x.optLong("id")
                        TaskRow(x, rev, expandable = true, open = openT.contains(id), onToggleOpen = { if (openT.contains(id)) openT.remove(id) else openT.add(id) }, onEdit = { onEditTask(x) }, ovr = true)
                    }
                }
                val tmrKey = LocalDate.parse(todayKey).plusDays(1).toString()
                val groups = rest.groupBy { it.optString("date") }
                for ((k, list) in groups) {
                    val title = if (k == todayKey) Str.t("today") else if (k == tmrKey) Str.t("tmr") else {
                        Str.wdDate(LocalDate.parse(k), comma = true)
                    }
                    SectionTitle(title)
                    for (x in list) {
                        val id = x.optLong("id")
                        TaskRow(x, rev, expandable = true, open = openT.contains(id), onToggleOpen = { if (openT.contains(id)) openT.remove(id) else openT.add(id) }, onEdit = { onEditTask(x) }, left = true)
                    }
                }
                if (pend.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Ic("clock", size = 56.dp, tint = p.muted)
                        Spacer(Modifier.height(12.dp))
                        Tx(Str.t("noUp"), size = 16.sp, weight = FontWeight.Bold, align = TextAlign.Center)
                    }
                }
            }
        }
        Spacer(Modifier.height(bottomPad))
    }
}

/** Popup to add your own task categories (rename / delete is done by tapping a selected chip). */
@Composable
fun CatSheet(rev: Int, onClose: () -> Unit) {
    val p = LocalPal.current
    var n by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    SheetFrame(Str.t("catNewT"), onClose) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val shape = RoundedCornerShape(9.dp)
            BasicTextField(
                value = n, onValueChange = { n = normDigits(it); err = false }, visualTransformation = BnDigits, singleLine = true,
                textStyle = TextStyle(color = if (p.dark) p.ink else Color.Black, fontSize = 14.sp), cursorBrush = SolidColor(p.ink),
                modifier = Modifier.weight(1f).height(38.dp).clip(shape).background(if (p.dark) p.glass2 else Color.White)
                    .border(if (err) 2.dp else 1.dp, if (err) Color(0xFFFF0000) else if (p.dark) p.edge else Color(0x4010253A), shape),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                        if (n.isEmpty()) Tx(Str.t("catNamePh"), size = 14.sp, color = p.muted)
                        inner()
                    }
                },
            )
            RowBtn(Str.t("save"), white = true) {
                if (n.trim().isEmpty()) err = true else { LogicExt.addCat(n.trim()); n = "" }
            }
        }
    }
}

/** Popup opened by tapping an already-selected custom category chip: rename it or delete it. */
@Composable
fun CatEditSheet(key: String, rev: Int, onClose: () -> Unit) {
    val p = LocalPal.current
    val oldName = remember(key) { Logic.catLbl(key) }
    var n by remember(key) { mutableStateOf(oldName) }
    var err by remember { mutableStateOf(false) }
    SheetFrame(Str.t("catEditT"), onClose) {
        TfInput(n, { n = it; err = false }, Str.t("catNamePh"), Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RowBtn(Str.t("del"), Modifier.weight(1f), icon = "trash", color = Color(0xFFFF4D4D)) {
                UiState.dlg = DlgSpec(
                    Str.t("delT"), oldName, Str.t("del"), cancel = Str.t("cancelT"), kind = "ask", icon = "trash",
                    onOk = { LogicExt.delCat(key); onClose() },
                )
            }
            RowBtn(Str.t("save"), Modifier.weight(1f), white = true) {
                if (n.trim().isEmpty()) err = true else { LogicExt.renameCat(key, n.trim()); onClose() }
            }
        }
    }
}
