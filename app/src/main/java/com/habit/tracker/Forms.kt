package com.habit.tracker

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/** The popup that opens when a calendar day is tapped. */
@Composable
fun DaySheet(rev: Int, k: String, onClose: () -> Unit, onToast: (String) -> Unit) {
    val p = LocalPal.current
    val a = UiState.act()
    val openT = remember { mutableStateListOf<Long>() }
    var confirmDel by remember { mutableStateOf(false) }
    var formKey by remember { mutableIntStateOf(0) }

    SheetFrame(LogicExt.longDate(k), onClose) {
        val hi = Hol.info(k)
        val banners = (if (hi.g.isNotEmpty()) hi.gx else emptyList()).map { it to false } + hi.o.map { it to true }
        for ((txt, other) in banners) {
            Box(
                Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 10.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (other) Color(0x29FFB800) else Color(0x24FF0000)).padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Tx(txt, size = 13.sp, color = if (other) Color(0xFFFFB800) else Color(0xFFFF0000), weight = FontWeight.SemiBold, align = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        if (a != null) {
            val id = a.optLong("id")
            val on = Logic.marksOf(id).has(k)
            val disabled = (!on && !Logic.canMark(a, k)) || (k < a.optString("start") && !on)
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.weight(1f).height(42.dp).alpha(if (disabled) .4f else 1f).clip(RoundedCornerShape(12.dp))
                        .background(if (on) p.glass2 else parseColor(a.optString("color")))
                        .tap {
                            if (k < a.optString("start") && !on) return@tap
                            if (!on && !Logic.canMark(a, k)) { onToast(Str.f("tooEarly", Str.t12(a.optString("time")))); return@tap }
                            if (on) Logic.marksOf(id).remove(k) else Logic.habAns(id, k, true)
                            Store.touch(); onClose()
                        }.padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Ic(if (on) "x" else "check", size = 20.dp, tint = if (on) p.ink else p.tink)
                    Spacer(Modifier.width(6.dp))
                    Tx(if (on) Str.t("undo") else Str.f("did", a.optString("name")), size = 15.sp, color = if (on) p.ink else p.tink, weight = FontWeight.Bold, maxLines = 1)
                }
                IconBox("edit", false) { UiState.backDay = k; UiState.dayKey = null; UiState.edit = FormSpec("habit", a, null) }
                IconBox("trash", true) { confirmDel = true }
            }
        } else {
            val tl = Logic.todos().filter { it.optString("date") == k }
            for (x in tl) {
                val id = x.optLong("id")
                TaskRow(x, rev, expandable = true, open = openT.contains(id), onToggleOpen = {
                    if (openT.contains(id)) openT.remove(id) else openT.add(id)
                }, onEdit = { UiState.backDay = k; UiState.dayKey = null; UiState.edit = FormSpec("task", null, x) })
            }
            Spacer(Modifier.height(14.dp))
            key(formKey) {
                TfForm(FormSpec("task", null, null, k), inline = true, onToast = onToast, onSaved = { formKey++ })
            }
        }
    }
    if (confirmDel && a != null) {
        ConfirmDlg(Str.t("delT"), Str.f("confirmDel", a.optString("name")), Str.t("bkOkB"), Str.t("cancelT"),
            onOk = { confirmDel = false; LogicExt.deleteHabit(a); onClose() }, onCancel = { confirmDel = false })
    }
}

@Composable
private fun IconBox(icon: String, red: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.width(44.dp).height(40.dp).clip(shape)
            .background(if (red) Color(0x1AFF0000) else p.glass)
            .border(1.dp, if (red) Color(0x59FF0000) else p.edge, shape).tap(onClick),
        contentAlignment = Alignment.Center,
    ) { Ic(icon, size = 20.dp, tint = if (red) p.cross else p.ink) }
}

@Composable
fun ConfirmDlg(title: String, text: String, ok: String, cancel: String, onOk: () -> Unit, onCancel: () -> Unit) {
    val p = LocalPal.current
    SheetFrame(title, onCancel) {
        Tx(text, size = 14.sp, color = p.ink, modifier = Modifier.padding(bottom = 16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RowBtn(cancel, Modifier.weight(1f)) { onCancel() }
            RowBtn(ok, Modifier.weight(1f)) { onOk() }
        }
    }
}

/** Popup for editing an existing task / habit. */
@Composable
fun FormSheet(rev: Int, spec: FormSpec, onToast: (String) -> Unit) {
    val title = Str.t(if (spec.act != null) "editT" else if (spec.task != null) "editTk" else "newT")
    val close = {
        UiState.edit = null
        val b = UiState.backDay
        UiState.backDay = null
        if (b != null) UiState.dayKey = b
    }
    SheetFrame(title, close) {
        TfForm(spec, inline = false, onToast = onToast, onSaved = { close() })
    }
}

@Composable
private fun FRow(label: String, wide: Boolean = false, content: @Composable RowScope.() -> Unit) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tx(label, if (wide) Modifier.width(76.dp) else Modifier.weight(1f), size = 13.sp, color = p.ink)
        content()
    }
}

@Composable
private fun PillBtn(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier.height(34.dp).clip(shape).background(if (on) p.ac else p.glass2).border(1.dp, if (on) Color.Transparent else p.edge, shape).tap(onClick).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) { Tx(label, size = 12.sp, color = if (on) p.tink else p.ink, weight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1) }
}

/** Task / habit form (used inside the day popup and in the edit popup). */
@Composable
fun TfForm(spec: FormSpec, inline: Boolean, onToast: (String) -> Unit, onSaved: () -> Unit) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    val ea = spec.act
    val et = spec.task
    var kind by remember { mutableStateOf(spec.kind) }
    val isEdit = ea != null || et != null

    // task fields
    var text by remember { mutableStateOf(et?.optString("text") ?: "") }
    var date by remember { mutableStateOf(et?.optString("date") ?: (spec.dateKey ?: Logic.todayKey())) }
    var time by remember { mutableStateOf(et?.optString("time")?.ifEmpty { "00:00" } ?: "00:00") }
    var rep by remember { mutableStateOf(et?.optString("rep")?.ifEmpty { "none" } ?: "none") }
    var cat by remember { mutableStateOf(et?.optString("cat") ?: "") }
    var subs by remember { mutableStateOf(LogicExt.subsToText(et)) }
    // habit fields
    var name by remember { mutableStateOf(ea?.optString("name") ?: "") }
    var atime by remember { mutableStateOf(ea?.optString("time") ?: "00:00") }
    var gm by remember { mutableIntStateOf(ea?.let { if (it.optBoolean("goalAll", false)) 31 else it.optInt("goalM", 0) } ?: 0) }
    var gall by remember { mutableStateOf(ea?.optBoolean("goalAll", false) ?: false) }
    var gt by remember { mutableStateOf(ea?.let { if (it.optInt("goalT", 0) == 0) "00" else it.optInt("goalT", 0).toString() } ?: "00") }
    val initDays = remember { ea?.optJSONArray("days")?.let { arr -> List(arr.length()) { arr.optInt(it) } } ?: emptyList() }
    var wsel by remember { mutableStateOf(initDays) }
    var strict by remember { mutableStateOf(initDays.isNotEmpty()) }
    var errName by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf<String?>(null) } // "task" or "habit"
    var showGoal by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        if (!isEdit) {
            Seg(listOf(SegItem("task", Str.t("segTask")), SegItem("habit", Str.t("segHabit"))), kind, { kind = it }, Modifier.padding(bottom = 6.dp))
        }
        if (kind == "task") {
            FormInput(text, { text = it; errName = false }, Str.t("taskPh"), errName)
            FRow(Str.t("dateL")) { ValBtn(LogicExt.shortDate(date)) { pickDate(ctx, date) { date = it } } }
            FRow(Str.t("timeL")) { ValBtn(if (time == "00:00") Str.dig("00:00") else Str.t12(time)) { showTime = "task" } }
            FRow(Str.t("repL"), wide = true) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    PillBtn(Str.t("r0"), rep == "none", Modifier.weight(1f)) { rep = "none" }
                    PillBtn(Str.t("r1"), rep == "daily", Modifier.weight(1f)) { rep = "daily" }
                    PillBtn(Str.t("r2"), rep == "weekly", Modifier.weight(1f)) { rep = "weekly" }
                    PillBtn(Str.t("r3"), rep == "monthly", Modifier.weight(1f)) { rep = "monthly" }
                }
            }
            FRow(Str.t("catL"), wide = true) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    PillBtn(Str.t("cNone"), cat == "", Modifier.widthIn(min = 70.dp)) { cat = "" }
                    for (c in Logic.catAll()) PillBtn(Logic.catLbl(c), cat == c, Modifier.widthIn(min = 70.dp)) { cat = c }
                }
            }
            Spacer(Modifier.height(8.dp))
            val shape = RoundedCornerShape(12.dp)
            BasicTextField(
                value = subs, onValueChange = { subs = it },
                textStyle = TextStyle(color = p.ink, fontSize = 13.sp), cursorBrush = SolidColor(p.ink),
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).clip(shape).background(p.glass2).border(1.dp, p.edge, shape),
                decorationBox = { inner ->
                    Box(Modifier.padding(11.dp)) {
                        if (subs.isEmpty()) Tx(Str.t("subPh"), size = 13.sp, color = p.muted)
                        inner()
                    }
                },
            )
        } else {
            FormInput(name, { name = it; errName = false }, Str.t("namePh"), errName)
            FRow(Str.t("askTime")) { ValBtn(if (atime == "00:00") Str.dig("00:00") else Str.t12(atime)) { showTime = "habit" } }
            FRow(Str.t("gmL")) {
                ValBtn(if (gall) Str.t("gaL") else if (gm > 0) Str.dig(gm.toString()) + " " + Str.t("gdU") else Str.dig("00")) { showGoal = true }
            }
            FRow(Str.t("gtL")) {
                val shape = RoundedCornerShape(9.dp)
                BasicTextField(
                    value = gt, onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 6) gt = v },
                    singleLine = true, textStyle = TextStyle(color = if (p.dark) p.ink else Color.Black, fontSize = 14.sp),
                    cursorBrush = SolidColor(p.ink), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(140.dp).height(38.dp).clip(shape).background(if (p.dark) p.glass2 else Color.White).border(1.dp, if (p.dark) p.edge else Color(0x4010253A), shape),
                    decorationBox = { inner -> Box(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) { inner() } },
                )
            }
            FRow(Str.t("strL")) { ValBtn(Str.t(if (strict) "strX" else "strF")) { strict = !strict } }
            if (strict) {
                Spacer(Modifier.height(8.dp))
                Tx(Str.t("daysL"), size = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Str.arr("wds").forEachIndexed { i, w ->
                        PillBtn(w, i in wsel, Modifier.weight(1f)) { wsel = if (i in wsel) wsel - i else wsel + i }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            RowBtn(Str.t("save"), Modifier.weight(1f)) {
                if (kind == "task") {
                    if (text.trim().isEmpty()) { errName = true; return@RowBtn }
                    LogicExt.saveTask(et, text.trim(), date, time, rep, cat, subs)
                    onSaved()
                } else {
                    if (name.trim().isEmpty()) { errName = true; return@RowBtn }
                    // FIX: "fixed days" with no day chosen used to silently become "every day"
                    if (strict && wsel.isEmpty()) {
                        onToast(if (Str.isBn) "অন্তত একটি বার বেছে নিন, অথবা নিয়ম \"প্রতিদিন\" করুন" else "Pick at least one day, or set the schedule to \"Every day\"")
                        return@RowBtn
                    }
                    val goalT = gt.toIntOrNull() ?: 0
                    if (ea != null) {
                        val tmp = JSONObject().put("id", ea.optLong("id")).put("start", ea.optString("start"))
                        val dd = org.json.JSONArray(if (!strict || wsel.size == 7) emptyList<Int>() else wsel.sorted())
                        tmp.put("days", dd)
                        val today = java.time.LocalDate.now()
                        val dn = Logic.stats(tmp, today.year, today.monthValue - 1, true)
                        if (goalT > 0 && goalT < dn.total) {
                            onToast(Str.t("wgT") + " — " + Str.f("wgTot", Str.bn(dn.total)))
                            return@RowBtn
                        }
                    }
                    LogicExt.saveHabit(ea, name.trim(), atime, gall, gm, goalT, if (!strict || wsel.size == 7) emptyList() else wsel.sorted())
                    onSaved()
                }
            }
        }
    }

    if (showTime != null) {
        val which = showTime
        TimePickerDlg(if (which == "task") time else atime, { showTime = null }) { v ->
            if (which == "task") time = v else atime = v
            showTime = null
        }
    }
    if (showGoal) {
        GoalPickerDlg(if (gall) 32 else gm, { showGoal = false }) { k ->
            if (k == 32) { gall = true; gm = 31 } else { gall = false; gm = k }
            showGoal = false
        }
    }
}

@Composable
private fun FormInput(value: String, onChange: (String) -> Unit, hint: String, err: Boolean) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(9.dp)
    val bg = if (p.dark) p.glass2 else Color.White
    val fg = if (p.dark) p.ink else Color.Black
    Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(color = fg, fontSize = 14.sp), cursorBrush = SolidColor(fg),
            modifier = Modifier.weight(1f).height(38.dp).clip(shape).background(bg)
                .border(if (err) 2.dp else 1.dp, if (err) Color(0xFFFF0000) else if (p.dark) p.edge else Color(0x4010253A), shape),
            decorationBox = { inner ->
                Box(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Tx(hint, size = 14.sp, color = p.muted)
                    inner()
                }
            },
        )
    }
}

@Composable
private fun ValBtn(label: String, onClick: () -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(9.dp)
    Box(
        Modifier.width(140.dp).height(34.dp).clip(shape).background(p.glass2).border(1.dp, p.edge, shape).tap(onClick).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Tx(label, size = 12.5.sp, maxLines = 1) }
}
