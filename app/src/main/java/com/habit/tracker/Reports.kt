package com.habit.tracker

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

private fun periodDays(mode: String, now: LocalDate): List<LocalDate> {
    val o = ArrayList<LocalDate>()
    when (mode) {
        "w" -> for (i in 6 downTo 0) o.add(now.minusDays(i.toLong()))
        "y" -> for (m in 1..now.monthValue) {
            val last = if (m == now.monthValue) now.dayOfMonth else java.time.YearMonth.of(now.year, m).lengthOfMonth()
            for (d in 1..last) o.add(LocalDate.of(now.year, m, d))
        }
        else -> for (d in 1..now.dayOfMonth) o.add(LocalDate.of(now.year, now.monthValue, d))
    }
    return o
}

/** Reports tab: week / month / year progress. */
@Composable
fun ReportsScreen(rev: Int, todayKey: String, bottomPad: Dp) {
    val p = LocalPal.current
    var rmode by rememberSaveable { mutableStateOf("w") }
    val now = LocalDate.parse(todayKey)
    val days = periodDays(rmode, now)
    val acts = Logic.acts()
    fun on(a: org.json.JSONObject, k: String) = Logic.marksOf(a.optLong("id")).has(k)
    fun cnt(a: org.json.JSONObject, d: LocalDate, k: String) = k >= a.optString("start") && k < todayKey && Logic.isSched(a, d)
    val ks = days.map { it.toString() }.toSet()
    val tt = Logic.todos().filter { it.optString("date") in ks }
    val tdone = tt.count { it.optBoolean("done") }

    Column(Modifier.fillMaxSize()) {
        Seg(listOf(SegItem("w", Str.t("rW")), SegItem("m", Str.t("rM")), SegItem("y", Str.t("rY"))), rmode, { rmode = it }, Modifier.padding(bottom = 6.dp))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            if (acts.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp).glass().padding(14.dp)) {
                    if (tt.isNotEmpty()) {
                        Tx(Str.bn(Logic.pct(tdone, tt.size)) + "%", size = 34.sp, weight = FontWeight.Bold)
                        Tx(Str.f("rTasks", Str.bn(tdone), Str.bn(tt.size)), size = 13.sp, color = p.muted, modifier = Modifier.padding(top = 2.dp))
                    } else Tx(Str.t("rNone"), size = 13.sp, color = p.muted)
                }
            } else {
                val groups: List<List<LocalDate>> = if (rmode == "y") (1..now.monthValue).map { m -> days.filter { it.monthValue == m } } else days.map { listOf(it) }
                fun ratio(g: List<LocalDate>): Float? {
                    var sc = 0; var dn = 0
                    for (d in g) { val k = d.toString(); for (a in acts) { val o = on(a, k); if (cnt(a, d, k) || o) { sc++; if (o) dn++ } } }
                    return if (sc > 0) dn.toFloat() / sc else null
                }
                val vals: List<Float?> = groups.map { ratio(it) }
                data class Row2(val a: org.json.JSONObject, val sc: Int, val dn: Int)
                val rows = acts.map { a ->
                    var sc = 0; var dn = 0
                    for (d in days) { val k = d.toString(); val o = on(a, k); if (cnt(a, d, k) || o) { sc++; if (o) dn++ } }
                    Row2(a, sc, dn)
                }
                val tsc = rows.sumOf { it.sc }
                val tdn = rows.sumOf { it.dn }

                Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp).glass().padding(14.dp)) {
                    Tx(Str.bn(Logic.pct(tdn, tsc)) + "%", size = 34.sp, weight = FontWeight.Bold)
                    Tx(Str.f("rHero", Str.bn(Logic.pct(tdn, tsc))), size = 13.sp, color = p.muted, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
                    Tx(Str.f("rTasks", Str.bn(tdone), Str.bn(tt.size)), size = 13.sp, color = p.muted)
                }
                SectionTitle(Str.t(if (rmode == "y") "rChartM" else "rChart"))
                if (rmode == "m") {
                    // whole month, 10 days per slide (the last slide takes the rest: 8 to 11 days)
                    val mDays = (1..now.lengthOfMonth()).map { LocalDate.of(now.year, now.monthValue, it) }
                    val mVals = mDays.map { if (it.isAfter(now)) null else ratio(listOf(it)) }
                    MonthSlides(mDays.map { it.dayOfMonth }, mVals, (now.dayOfMonth - 1) / 10)
                } else Box(Modifier.fillMaxWidth().padding(bottom = 12.dp).glass().padding(14.dp)) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val sc = maxWidth.value / 320f
                        val n = groups.size
                        Column(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().height((96 * sc).dp)) {
                                vals.forEach { v ->
                                    val hh = if (v == null) 2f else maxOf(2f, v * 84f)
                                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                                        Box(Modifier.fillMaxWidth(.7f).height((hh * sc).dp).clip(RoundedCornerShape((3 * sc).dp)).background(if (v == null) p.glass2 else p.mono))
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth().height((22 * sc).dp)) {
                                groups.forEachIndexed { i, g ->
                                    val lab = when (rmode) {
                                        "w" -> Str.arr("wds")[g[0].dayOfWeek.value % 7]
                                        "y" -> Str.dig(i + 1)
                                        else -> Str.dig(i + 1)
                                    }
                                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                        Tx(lab, size = 10f.times(sc).sp, color = p.muted, align = TextAlign.Center, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
                SectionTitle(Str.t("rHab"))
                for (r in rows) {
                    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).glass().padding(12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 5.dp)) {
                            Tx(r.a.optString("name"), Modifier.weight(1f), size = 13.5.sp)
                            Tx(Str.dig(r.dn) + "/" + Str.dig(r.sc) + " · " + Str.bn(Logic.pct(r.dn, r.sc)) + "%", size = 13.5.sp)
                        }
                        Bar(Logic.pct(r.dn, r.sc) / 100f, parseColor(r.a.optString("color")))
                    }
                }
            }
            Spacer(Modifier.height(bottomPad))
        }
    }
}

/** Month chart: 3 slides - days 1-10, 11-20, 21-end (28 to 31 days, so the last slide shows 8 to 11 days). Every bar has its day number under it. */
@Composable
private fun MonthSlides(dayNums: List<Int>, vals: List<Float?>, startPage: Int) {
    val p = LocalPal.current
    val pageCount = 3
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = startPage.coerceIn(0, pageCount - 1), pageCount = { pageCount })
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).glass().padding(14.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val sc = maxWidth.value / 320f
            androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxWidth()) { page ->
                val from = page * 10
                val to = if (page == pageCount - 1) dayNums.size else minOf(from + 10, dayNums.size)
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().height((96 * sc).dp)) {
                        for (i in from until to) {
                            val v = vals[i]
                            val hh = if (v == null) 2f else maxOf(2f, v * 84f)
                            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                                Box(Modifier.fillMaxWidth(.7f).height((hh * sc).dp).clip(RoundedCornerShape((3 * sc).dp)).background(if (v == null) p.glass2 else p.mono))
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().height((22 * sc).dp)) {
                        for (i in from until to) {
                            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                Tx(Str.dig(dayNums[i]), size = (10f * sc).sp, color = p.muted, align = TextAlign.Center, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            for (i in 0 until pageCount) {
                Box(Modifier.padding(horizontal = 3.dp).size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (pager.currentPage == i) p.mono else p.glass2))
            }
        }
    }
}
