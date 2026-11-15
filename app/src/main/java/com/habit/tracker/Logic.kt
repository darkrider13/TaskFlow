package com.habit.tracker

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.LocalTime
import java.util.Locale

/** Habit / task rules ported 1:1 from the original app. Dates are "yyyy-MM-dd" text keys. */
object Logic {
    fun todayKey(): String = LocalDate.now().toString()

    fun acts(): List<JSONObject> = Store.s.optJSONArray("acts").list()
    fun todos(): List<JSONObject> = Store.s.optJSONArray("todos").list()

    fun marksOf(id: Long): JSONObject = Store.s.getJSONObject("marks").optJSONObject(id.toString()) ?: JSONObject()

    fun pct(a: Int, b: Int): Int = if (b > 0) minOf(100, Math.round(a.toFloat() / b * 100)) else 0

    /** Is this habit planned for that day? (no days list = every day) */
    fun isSched(a: JSONObject, d: LocalDate): Boolean {
        val days = a.optJSONArray("days")
        if (days == null || days.length() == 0) return true
        val dow = d.dayOfWeek.value % 7 // Sunday = 0
        for (i in 0 until days.length()) if (days.optInt(i) == dow) return true
        return false
    }

    fun hTimeMin(a: JSONObject?): Int {
        val p = (a?.optString("time", "00:00") ?: "00:00").split(":")
        return (p.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    fun habCnt(a: JSONObject): Int {
        val mk = marksOf(a.optLong("id"))
        val start = a.optString("start")
        val today = todayKey()
        var n = 0
        val it = mk.keys()
        while (it.hasNext()) {
            val k = it.next()
            if (k >= start && k <= today) n++
        }
        return n
    }

    fun canMark(a: JSONObject, k: String): Boolean {
        val today = todayKey()
        if (k < today) return true
        if (k > today) return false
        val n = LocalTime.now()
        return n.hour * 60 + n.minute >= hTimeMin(a)
    }

    fun habAns(id: Long, k: String, yes: Boolean) {
        val a = acts().find { it.optLong("id") == id }
        if (yes && a != null && k < a.optString("start")) return
        val marks = Store.s.getJSONObject("marks")
        val mk = marks.optJSONObject(id.toString()) ?: JSONObject().also { marks.put(id.toString(), it) }
        val nos = Store.s.getJSONObject("nos")
        val no = nos.optJSONObject(id.toString()) ?: JSONObject().also { nos.put(id.toString(), it) }
        if (yes) {
            mk.put(k, 1)
            no.remove(k)
        } else {
            mk.remove(k)
            no.put(k, 1)
        }
    }

    /** Planned days of that month for a habit with a monthly goal. */
    fun mgoal(a: JSONObject, y: Int, m0: Int): Int {
        val goalM = a.optInt("goalM", 0)
        if (goalM == 0) return 0
        val ym = java.time.YearMonth.of(y, m0 + 1)
        val start = a.optString("start")
        var n = 0
        for (d in 1..ym.lengthOfMonth()) {
            val date = ym.atDay(d)
            if (date.toString() >= start && isSched(a, date)) n++
        }
        return if (a.optBoolean("goalAll", false)) n else minOf(goalM, n)
    }

    class Stats(val total: Int, val month: Int)

    fun stats(a: JSONObject, y: Int, m0: Int, incl: Boolean = false): Stats {
        val marks = marksOf(a.optLong("id"))
        val pre = y.toString() + "-" + Str.pad2(m0 + 1) + "-"
        val today = LocalDate.now()
        val todayKey = today.toString()
        var total = 0
        var c = try { LocalDate.parse(a.optString("start")) } catch (e: Exception) { today.plusDays(1) }
        var i = 0
        while (i < 20000 && !c.isAfter(today)) {
            val ck = c.toString()
            if ((isSched(a, c) && (incl || ck < todayKey)) || marks.has(ck)) total++
            c = c.plusDays(1)
            i++
        }
        var month = 0
        val it = marks.keys()
        while (it.hasNext()) if (it.next().startsWith(pre)) month++
        return Stats(total, month)
    }

    // ---------- tasks ----------

    fun nextDate(k: String, r: String, x: JSONObject): String {
        val d = LocalDate.parse(k)
        return when (r) {
            "daily" -> d.plusDays(1).toString()
            "weekly" -> d.plusDays(7).toString()
            else -> {
                val next = d.withDayOfMonth(1).plusMonths(1)
                val rd = x.optInt("rd", 0).let { if (it == 0) d.dayOfMonth else it }
                next.withDayOfMonth(minOf(rd, next.lengthOfMonth())).toString()
            }
        }
    }

    fun nextOf(x: JSONObject): String {
        val today = todayKey()
        var k = nextDate(x.optString("date"), x.optString("rep"), x)
        var guard = 0
        while (k < today && guard++ < 5000) k = nextDate(k, x.optString("rep"), x)
        return k
    }

    /** Tick / untick a task. Repeating tasks create their next copy. (Reminders are added in a later stage.) */
    fun setDone(x: JSONObject, v: Boolean) {
        x.put("done", v)
        val todos = Store.s.getJSONArray("todos")
        val rep = x.optString("rep", "none")
        if (v) {
            if (rep.isNotEmpty() && rep != "none" && x.optLong("nx", 0L) == 0L) {
                val nid = Store.s.optInt("nid", 1)
                Store.s.put("nid", nid + 1)
                val n = JSONObject()
                val newId = System.currentTimeMillis()
                n.put("id", newId)
                n.put("nid", nid)
                n.put("text", x.optString("text"))
                n.put("date", nextOf(x))
                n.put("time", x.optString("time"))
                n.put("done", false)
                n.put("pri", x.optInt("pri", 0))
                n.put("rep", rep)
                n.put("cat", x.optString("cat"))
                val subs = JSONArray()
                for (z in x.optJSONArray("sub").list()) subs.put(JSONObject().put("t", z.optString("t")).put("d", false))
                n.put("sub", subs)
                n.put("rd", x.optInt("rd", 0).let { if (it == 0) x.optString("date").substring(8).toInt() else it })
                x.put("nx", newId)
                todos.put(n)
            }
        } else {
            val nxId = x.optLong("nx", 0L)
            if (nxId != 0L) {
                for (i in 0 until todos.length()) {
                    val n = todos.optJSONObject(i) ?: continue
                    if (n.optLong("id") == nxId) {
                        if (!n.optBoolean("done")) {
                            todos.remove(i)
                            x.remove("nx")
                        }
                        break
                    }
                }
            }
        }
    }

    // ---------- categories ----------

    val builtinCats = listOf("study", "work", "personal")

    fun customCats(): List<JSONObject> = Store.s.optJSONArray("cats").list()
    fun catAll(): List<String> = builtinCats + customCats().map { it.optString("k") }
    fun catOk(k: String): Boolean = k in builtinCats || customCats().any { it.optString("k") == k }
    fun catIc(k: String): String = when (k) {
        "study" -> "book"
        "work" -> "briefcase"
        "personal" -> "home"
        else -> "tag"
    }

    fun catLbl(k: String): String = when (k) {
        "study" -> Str.t("cStudy")
        "work" -> Str.t("cWork")
        "personal" -> Str.t("cPers")
        else -> customCats().find { it.optString("k") == k }?.optString("n") ?: ""
    }

    // ---------- dates ----------

    /** e.g. "Wednesday, November 11" / "বুধবার, ১১ নভেম্বর" */
    fun heroDate(k: String): String {
        val s = LocalDate.parse(k).format(Str.fmt("EEEE, d MMMM"))
        return Str.dig(s)
    }
}

object LogicExt {
    fun deleteTask(x: JSONObject) {
        val todos = Store.s.getJSONArray("todos")
        for (i in 0 until todos.length()) if (todos.optJSONObject(i) === x) { todos.remove(i); break }
        Store.touch()
    }

    /** d = 0 -> today, 1 -> tomorrow */
    fun moveTask(x: JSONObject, d: Int, touch: Boolean = true) {
        x.put("date", if (d == 1) LocalDate.now().plusDays(1).toString() else Logic.todayKey())
        if (touch) Store.touch()
    }

    fun addCat(n: String) {
        val arr = Store.s.optJSONArray("cats") ?: JSONArray().also { Store.s.put("cats", it) }
        arr.put(JSONObject().put("k", "c" + System.currentTimeMillis()).put("n", n))
        Store.touch()
    }

    fun renameCat(k: String, n: String) {
        val arr = Store.s.optJSONArray("cats") ?: return
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { if (it.optString("k") == k) it.put("n", n) }
        Store.touch()
    }

    fun delCat(k: String) {
        val arr = Store.s.optJSONArray("cats") ?: return
        for (i in 0 until arr.length()) if (arr.optJSONObject(i)?.optString("k") == k) { arr.remove(i); break }
        if (UiState.fcat == k) UiState.fcat = ""
        Store.touch()
    }

    fun evLabel(a: JSONObject): String {
        val days = a.optJSONArray("days")
        return if (days != null && days.length() > 0) {
            val w = Str.arr("wds")
            Str.f("onDays", List(days.length()) { w.getOrElse(days.optInt(it)) { "" } }.joinToString(", "), Str.t12(a.optString("time")))
        } else Str.f("every", Str.t12(a.optString("time")))
    }

    val COLORS = listOf("#00FFFF", "#0000FF", "#8000FF", "#FF8000", "#FF0080", "#00FF00", "#FF00FF", "#0080FF", "#80FF00", "#00FF80", "#FFC000", "#FF5070")

    fun pickColor(used: Set<String>): String {
        for (c in COLORS) if (c.uppercase() !in used) return c
        var i = 0
        while (true) {
            val c = "hsl(" + Math.round((i * 137.5 + 20) % 360) + ",100%,55%)"
            if (c.uppercase() !in used) return c
            i++
        }
    }

    /** Days still left in that month (today or later, not ticked, planned). */
    fun remDays(a: JSONObject, y: Int, m0: Int): Int {
        val mk = Logic.marksOf(a.optLong("id"))
        val ym = java.time.YearMonth.of(y, m0 + 1)
        val today = Logic.todayKey()
        var n = 0
        for (d in 1..ym.lengthOfMonth()) {
            val date = ym.atDay(d)
            val k = date.toString()
            if (k >= a.optString("start") && k >= today && !mk.has(k) && Logic.isSched(a, date)) n++
        }
        return n
    }

    fun longDate(k: String): String {
        return Str.dig(LocalDate.parse(k).format(Str.fmt("EEEE, d MMMM")))
    }

    fun monthName(y: Int, m0: Int): String {
        return Str.dig(java.time.YearMonth.of(y, m0 + 1).format(Str.fmt("LLLL yyyy")))
    }

    fun dayMonth(k: String): String {
        return Str.dig(LocalDate.parse(k).format(Str.fmt("d MMM")))
    }

    fun shortDate(k: String): String {
        return Str.dig(LocalDate.parse(k).format(Str.fmt("d MMM yyyy")))
    }

    fun deleteHabit(a: JSONObject) {
        val id = a.optLong("id").toString()
        Store.s.getJSONObject("marks").remove(id)
        Store.s.getJSONObject("nos").remove(id)
        val acts = Store.s.getJSONArray("acts")
        for (i in 0 until acts.length()) if (acts.optJSONObject(i) === a) { acts.remove(i); break }
        UiState.sel = Logic.acts().firstOrNull()?.optLong("id")
        Store.touch()
    }

    private val SB_PFX = Regex("^\\s*[0-9\\u09E6-\\u09EF]+\\s*[.)]\\s?")
    fun sbStrip(z: String) = z.replace(SB_PFX, "")

    fun subsToText(x: JSONObject?): String =
        x?.optJSONArray("sub").list().mapIndexed { i, z -> Str.bn(i + 1) + ". " + z.optString("t") }.joinToString("\n")

    fun parseSubs(text: String, old: List<JSONObject>): JSONArray {
        val arr = JSONArray()
        text.split("\n").map { sbStrip(it).trim() }.filter { it.isNotEmpty() }.forEach { t ->
            arr.put(JSONObject().put("t", t).put("d", old.find { it.optString("t") == t }?.optBoolean("d") == true))
        }
        return arr
    }

    fun saveTask(edit: JSONObject?, text: String, date: String, time: String, rep: String, cat: String, subText: String) {
        val tm = if (time == "00:00") "" else time
        if (edit != null) {
            val old = edit.optJSONArray("sub").list()
            edit.put("text", text); edit.put("date", date.ifEmpty { Logic.todayKey() }); edit.put("time", tm)
            edit.put("rep", rep); edit.put("cat", cat); edit.put("sub", parseSubs(subText, old))
        } else {
            val nid = Store.s.optInt("nid", 1)
            Store.s.put("nid", nid + 1)
            val x = JSONObject()
            x.put("id", System.currentTimeMillis()); x.put("nid", nid); x.put("text", text)
            x.put("date", date.ifEmpty { Logic.todayKey() }); x.put("time", tm); x.put("done", false)
            x.put("pri", 0); x.put("rep", rep); x.put("cat", cat); x.put("sub", parseSubs(subText, emptyList()))
            Store.s.getJSONArray("todos").put(x)
        }
        Store.touch()
    }

    fun saveHabit(edit: JSONObject?, name: String, time: String, gall: Boolean, gm: Int, gt: Int, days: List<Int>, color: String = "") {
        val vals = JSONObject()
        vals.put("name", name); vals.put("time", time.ifEmpty { "00:00" })
        vals.put("goalAll", gall); vals.put("goalM", if (gall) 31 else minOf(31, maxOf(0, gm)))
        vals.put("goalT", gt); vals.put("days", JSONArray(days))
        val a: JSONObject
        if (edit != null) {
            a = edit
            for (k in listOf("name", "time", "goalAll", "goalM", "goalT", "days")) a.put(k, vals.get(k))
            if (color.isNotEmpty()) a.put("color", color)
        } else {
            val nid = Store.s.optInt("nid", 1)
            Store.s.put("nid", nid + 1)
            val used = Logic.acts().map { it.optString("color").uppercase() }.toSet()
            a = JSONObject()
            a.put("id", System.currentTimeMillis()); a.put("nid", nid); a.put("start", Logic.todayKey())
            a.put("color", color.ifEmpty { pickColor(used) })
            for (k in listOf("name", "time", "goalAll", "goalM", "goalT", "days")) a.put(k, vals.get(k))
            Store.s.getJSONArray("acts").put(a)
        }
        UiState.sel = a.optLong("id")
        Store.touch()
    }
}
