package com.habit.tracker

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

private val FOOT_LINKS = mapOf(
    "fb" to "https://www.facebook.com/insanul.islam.abirr",
    "wa" to "https://wa.me/insanul.islam.abir",
    "ig" to "https://www.instagram.com/md.insanulislam",
    "tg" to "https://t.me/insanul13",
)

fun remInt(x: JSONObject): Int = if (x.optInt("im", 0) > 0) x.optInt("im") else (if (x.optInt("every", 0) > 0) x.optInt("every") else 2) * 60

fun remLbl(m: Int): String {
    val h = m / 60
    val n = m % 60
    val b = Str.isBn
    val a = ArrayList<String>()
    if (h > 0) a.add(h.toString() + (if (b) " ঘণ্টা" else " h"))
    if (n > 0) a.add(n.toString() + (if (b) " মিনিট" else " min"))
    return a.joinToString(" ")
}

/** More tab: focus timer, interval reminders, footer. (Screen lock comes in a later step.) */
@Composable
fun MoreScreen(rev: Int, todayKey: String, bottomPad: Dp, onToast: (String) -> Unit) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val tickRead = Focus.tick
    val appCtx = ctx.applicationContext
    val syncAlarm = { Notifs.focus(appCtx, Focus.run, Focus.end, Focus.mode == "focus") }
    var showCustom by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionTitle(Str.t("focusT"), first = true)
        Column(Modifier.fillMaxWidth().glass().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Seg(
                listOf("focus" to "fFocus", "short" to "fShort", "long" to "fLong").map { (k, l) -> SegItem(k, Str.t(l) + " " + Str.bn(Focus.FM[k] ?: 0)) },
                Focus.mode, { Focus.switchMode(it); syncAlarm() },
            )
            val r = Focus.remaining()
            Box(Modifier.fillMaxWidth()) {
                ClockText(
                    Str.dig(Str.pad2(r / 60) + ":" + Str.pad2(r % 60)),
                    Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp).tap { if (!Focus.run) showCustom = true },
                    size = 56.sp, weight = FontWeight.Bold,
                )
                Box(Modifier.align(Alignment.BottomEnd).size(34.dp).clip(RoundedCornerShape(9.dp)).tap { Focus.fs = true }, contentAlignment = Alignment.Center) { Ic("expand", size = 20.dp) }
            }
            Bar((100 - Logic.pct(r, Focus.total())) / 100f, p.mono)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val label = if (Focus.run) Str.t("fPause") else if (Focus.left < Focus.total()) Str.t("fResume") else Str.t("fStart")
                Box(
                    Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(12.dp)).background(p.ac).tap { Focus.toggle(); syncAlarm() }.padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) { Tx(label, size = 14.sp, color = p.tink, weight = FontWeight.Bold, maxLines = 1) }
                val shape = RoundedCornerShape(12.dp)
                Box(
                    Modifier.weight(1f).height(42.dp).clip(shape).background(p.glass2).border(1.dp, p.edge, shape).padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Tx(Str.f("fToday", Str.bn(Store.s.getJSONObject("focus").optInt(todayKey, 0))), size = 12.5.sp, align = TextAlign.Center)
                }
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).tap { Focus.reset(); syncAlarm() }, contentAlignment = Alignment.Center) { Ic("reset", size = 20.dp) }
            }
        }

        // interval reminders
        SectionTitle(Str.t("remT"))
        val rems = Store.s.optJSONArray("rems").list()
        if (rems.isNotEmpty()) {
            val pager = rememberPagerState(pageCount = { rems.size })
            var lastN by remember { mutableIntStateOf(rems.size) }
            LaunchedEffect(rems.size) {
                if (rems.size > lastN) pager.animateScrollToPage(rems.size - 1)
                lastN = rems.size
            }
            HorizontalPager(pager, Modifier.fillMaxWidth(), pageSpacing = 8.dp) { i ->
                val r = rems[i]
                Row(
                    Modifier.fillMaxWidth().glass().tap { UiState.remObj = r; UiState.remSheet = true }.height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(4.dp).fillMaxHeight().background(Color(0xFF0000FF)))
                    Row(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Ic("clock", size = 15.dp)
                                Spacer(Modifier.width(6.dp))
                                Tx(r.optString("name"), size = 15.sp, weight = FontWeight.Bold, maxLines = 1)
                            }
                            Tx(Str.f("remInfo", remLbl(remInt(r)), Str.t12(r.optString("from")), Str.t12(r.optString("to"))), size = 12.5.sp, color = p.muted)
                        }
                        CheckBox(r.optBoolean("on"), 26) {
                            r.put("on", !r.optBoolean("on"))
                            Store.touch()
                        }
                    }
                }
            }
            if (rems.size > 1) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    for (i in rems.indices) {
                        val on = i == pager.currentPage
                        Box(
                            Modifier.padding(horizontal = 3.dp).height(6.dp).width(if (on) 16.dp else 6.dp)
                                .clip(RoundedCornerShape(if (on) 3.dp else 3.dp)).background(p.ink.copy(alpha = if (on) 1f else .3f)),
                        )
                    }
                }
            } else Spacer(Modifier.height(6.dp))
        }
        val addShape = RoundedCornerShape(20.dp)
        Box(
            Modifier.padding(top = 4.dp).height(34.dp).clip(addShape).background(p.glass)
                .border(1.dp, p.muted, addShape).tap { UiState.remObj = null; UiState.remSheet = true }.padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Tx(Str.t("remAdd"), size = 13.sp, color = p.muted) }

        LockCard(onToast)

        // footer
        Column(Modifier.fillMaxWidth().padding(top = 18.dp).glass().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Tx(Str.t("ftDev"), size = 14.sp, weight = FontWeight.Bold, align = TextAlign.Center)
            Row(Modifier.padding(horizontal = 6.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                for ((k, url) in FOOT_LINKS) {
                    Box(Modifier.size(40.dp).clip(CircleShape).tap {
                        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) {}
                    }, contentAlignment = Alignment.Center) { Ic(k, size = 22.dp, tint = p.ink) }
                }
            }
            Tx(Str.t("ftCopy"), size = 10.sp, color = p.muted, weight = FontWeight.SemiBold, align = TextAlign.Center)
        }
        Spacer(Modifier.height(bottomPad))
    }

    if (showCustom) {
        val cur = maxOf(1, Focus.left)
        var m by remember { mutableIntStateOf(minOf(180, cur / 60)) }
        var s by remember { mutableIntStateOf(cur % 60) }
        PickerCard("clock", if (Str.isBn) "টাইমার সেট করুন" else "Set timer", { showCustom = false }, {
            val tot = m * 60 + s
            if (tot >= 1) {
                Focus.tot = tot; Focus.left = tot; Focus.run = false
                syncAlarm()
                showCustom = false
            }
        }) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                Tx(if (Str.isBn) "মিনিট" else "Minutes", Modifier.weight(1f), size = 13.sp, color = p.muted, align = TextAlign.Center)
                Spacer(Modifier.width(28.dp))
                Tx(if (Str.isBn) "সেকেন্ড" else "Seconds", Modifier.weight(1f), size = 13.sp, color = p.muted, align = TextAlign.Center)
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                WheelBand()
                Row(Modifier.fillMaxWidth()) {
                    WheelColumn(List(181) { Str.dig(Str.pad2(it)) }, m, false, Modifier.weight(1f)) { m = it }
                    Box(Modifier.width(28.dp).padding(top = 44.dp).height(44.dp), contentAlignment = Alignment.Center) { Tx(":", size = 26.sp, weight = FontWeight.Bold) }
                    WheelColumn(List(60) { Str.dig(Str.pad2(it)) }, s, true, Modifier.weight(1f)) { s = it }
                }
            }
        }
    }
}

/** Add / edit an interval reminder. (The phone alarms for it come in a later step.) */
@Composable
fun ReminderSheet(rev: Int, r: JSONObject?, onClose: () -> Unit, onToast: (String) -> Unit) {
    val p = LocalPal.current
    var name by remember { mutableStateOf(r?.optString("name") ?: "") }
    var hh by remember { mutableStateOf(r?.let { Str.dig(Str.pad2(remInt(it) / 60)) } ?: "") }
    var mm by remember { mutableStateOf(r?.let { Str.dig(Str.pad2(remInt(it) % 60)) } ?: "") }
    var from by remember { mutableStateOf(r?.optString("from") ?: "00:00") }
    var to by remember { mutableStateOf(r?.optString("to") ?: "23:59") }
    var errName by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf<String?>(null) }
    var confirmDel by remember { mutableStateOf(false) }

    fun toLat(s: String): Int = s.map { c -> if (c in '০'..'৯') ('0' + (c - '০')) else c }.filter { it.isDigit() }.joinToString("").toIntOrNull() ?: 0

    Box(Modifier.fillMaxSize().then(if (confirmDel) Modifier.blur(14.dp) else Modifier)) {
    SheetFrame(Str.t(if (r != null) "remEdit" else "remNew"), onClose) {
        RemBox(name, { name = it; errName = false }, Str.t("remNamePh"), Modifier.fillMaxWidth(), err = errName)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tx(Str.t("remEvery"), Modifier.weight(1f), size = 13.sp)
            RemBox(hh, { v -> if (v.length <= 2) hh = v }, Str.t("remHr"), Modifier.width(70.dp), numeric = true)
            RemBox(mm, { v -> if (v.length <= 2) mm = v }, Str.t("remMin"), Modifier.width(70.dp), numeric = true)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tx(Str.t("remFrom"), Modifier.weight(1f), size = 13.sp)
            RemValBtn(Str.t12(from)) { pick = "from" }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tx(Str.t("remTo"), Modifier.weight(1f), size = 13.sp)
            RemValBtn(Str.t12(to)) { pick = "to" }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RowBtn(Str.t("save"), Modifier.weight(1f), white = true) {
                if (name.trim().isEmpty()) { errName = true; return@RowBtn }
                val h = minOf(23, maxOf(0, toLat(hh)))
                val m = minOf(59, maxOf(0, toLat(mm)))
                val im = h * 60 + m
                if (im < 10) { onToast(Str.t("remBad")); return@RowBtn }
                fun mins(s: String): Int { val q = s.split(":"); return (q.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (q.getOrNull(1)?.toIntOrNull() ?: 0) }
                val ra = mins(from)
                var rb = mins(to)
                if (rb <= ra) rb += 1440
                if ((rb - ra) / im + 1 > 150) { onToast(Str.f("remMax", remLbl(149 * im))); return@RowBtn }
                val rems = Store.s.getJSONArray("rems")
                val x: JSONObject
                if (r != null) x = r else {
                    val nid = Store.s.optInt("nid", 1)
                    Store.s.put("nid", nid + 1)
                    x = JSONObject().put("id", System.currentTimeMillis()).put("nid", nid)
                    rems.put(x)
                }
                x.put("name", name.trim()); x.put("im", im); x.put("every", maxOf(1, Math.round(im / 60f)))
                x.put("from", from); x.put("to", to); x.put("on", true)
                Store.touch()
                onClose()
            }
            if (r != null) RowBtn(Str.t("del"), Modifier.weight(1f)) { confirmDel = true }
        }
    }
    }
    if (pick != null) {
        val w = pick
        TimePickerDlg(if (w == "from") from else to, { pick = null }) { v -> if (w == "from") from = v else to = v; pick = null }
    }
    if (confirmDel && r != null) {
        ConfirmDlg(Str.t("delT"), Str.t("remDelQ"), Str.t("bkOkB"), Str.t("cancelT"),
            onOk = {
                val rems = Store.s.getJSONArray("rems")
                for (i in 0 until rems.length()) if (rems.optJSONObject(i) === r) { rems.remove(i); break }
                confirmDel = false
                Store.touch()
                onClose()
            }, onCancel = { confirmDel = false })
    }
}

@Composable
private fun RemBox(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier, err: Boolean = false, numeric: Boolean = false) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(9.dp)
    val fg = if (p.dark) p.ink else Color.Black
    BasicTextField(
        value = value, onValueChange = { onChange(normDigits(it)) }, visualTransformation = BnDigits, singleLine = true,
        textStyle = TextStyle(color = fg, fontSize = 14.sp, textAlign = if (numeric) TextAlign.Center else TextAlign.Start),
        cursorBrush = SolidColor(fg),
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = modifier.height(38.dp).clip(shape).background(if (p.dark) p.glass2 else Color.White)
            .border(if (err) 2.dp else 1.dp, if (err) Color(0xFFFF0000) else if (p.dark) p.edge else Color(0x4010253A), shape),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize().padding(horizontal = if (numeric) 4.dp else 12.dp), contentAlignment = if (numeric) Alignment.Center else Alignment.CenterStart) {
                if (value.isEmpty()) Tx(hint, size = if (numeric) 9.5.sp else 14.sp, color = p.muted)
                inner()
            }
        },
    )
}

@Composable
private fun RemValBtn(label: String, onClick: () -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(9.dp)
    Box(Modifier.width(148.dp).height(34.dp).clip(shape).background(p.glass2).border(1.dp, p.edge, shape).tap(onClick), contentAlignment = Alignment.Center) { Tx(label, size = 12.5.sp) }
}

/** Message popup: round icon on top, centred title + text, buttons below (backup questions / results). */
@Composable
fun AppDlg(d: DlgSpec) {
    val p = LocalPal.current
    val close = { UiState.dlg = null }
    androidx.activity.compose.BackHandler(onBack = close)
    val green = Color(0xFF1AA94C)
    val red = Color(0xFFFF0000)
    val green2 = Color(0xFF22C55E)
    val (outer, inner, ico) = when (d.kind) {
        "ok" -> Triple(green.copy(alpha = .18f), green, "check")
        "err" -> Triple(red.copy(alpha = .18f), Color(0xFFDC2626), "x")
        else -> Triple(p.glass, p.glass2, d.icon)
    }
    val shape = RoundedCornerShape(22.dp)
    Box(Modifier.fillMaxSize().background(p.shade).tap { close() }.padding(16.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().clip(shape)
                .background(if (p.dark) Color(0xF2161618) else Color(0xF2F4F9FF))
                .border(1.dp, p.edge, shape).tap { }
                .padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(75.dp).clip(CircleShape).background(outer), contentAlignment = Alignment.Center) {
                Box(Modifier.size(60.dp).clip(CircleShape).background(inner), contentAlignment = Alignment.Center) {
                    Ic(ico, size = 30.dp, tint = if (d.kind == "ask") p.ink else Color.White, stroke = 2.6f)
                }
            }
            Spacer(Modifier.height(14.dp))
            Tx(d.title, size = 19.sp, weight = FontWeight.Bold, align = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Tx(d.text, size = 15.sp, color = p.muted, align = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            val bs = RoundedCornerShape(14.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                if (d.cancel != null) {
                    Box(Modifier.weight(1f).height(43.dp).clip(bs).background(red).tap { close() }, contentAlignment = Alignment.Center) {
                        Tx(d.cancel, size = 15.sp, color = Color.Black, weight = FontWeight.Bold)
                    }
                }
                Row(
                    (if (d.cancel != null) Modifier.weight(1f) else Modifier.width(140.dp)).height(43.dp).clip(bs).background(if (d.kind == "ask") green2 else Color.White).border(1.dp, p.edge, bs).tap { close(); d.onOk() },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (d.kind == "ask") {
                        Ic(d.icon, size = 18.dp, tint = Color.Black)
                        Spacer(Modifier.width(8.dp))
                    }
                    Tx(d.ok, size = 15.sp, color = Color.Black, weight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}


/** Export chooser: "Save to folder" and "Share" side by side. */
@Composable
fun ExportDlg(onSave: () -> Unit, onShare: () -> Unit, onClose: () -> Unit) {
    val p = LocalPal.current
    androidx.activity.compose.BackHandler(onBack = onClose)
    val shape = RoundedCornerShape(22.dp)
    val bs = RoundedCornerShape(14.dp)
    Box(Modifier.fillMaxSize().background(p.shade).tap { onClose() }.padding(16.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().clip(shape)
                .background(if (p.dark) Color(0xF2161618) else Color(0xF2F4F9FF))
                .border(1.dp, p.edge, shape).tap { }
                .padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(75.dp).clip(CircleShape).background(p.glass), contentAlignment = Alignment.Center) {
                Box(Modifier.size(60.dp).clip(CircleShape).background(p.glass2), contentAlignment = Alignment.Center) {
                    Ic("export", size = 30.dp, tint = p.ink, stroke = 2.6f)
                }
            }
            Spacer(Modifier.height(14.dp))
            Tx(Str.t("expChT"), size = 19.sp, weight = FontWeight.Bold, align = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Tx(Str.t("expChD"), size = 15.sp, color = p.muted, align = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.weight(1f).height(46.dp).clip(bs).background(p.glass2).border(1.dp, p.edge, bs).tap { onSave() },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Ic("save", size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Tx(Str.t("expSaveB"), size = 14.sp, weight = FontWeight.Bold, maxLines = 1)
                }
                Row(
                    Modifier.weight(1f).height(46.dp).clip(bs).background(Color.White).border(1.dp, p.edge, bs).tap { onShare() },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Ic("share", size = 18.dp, tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Tx(Str.t("expShareB"), size = 14.sp, color = Color.Black, weight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}
