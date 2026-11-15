package com.habit.tracker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Online holiday lists, downloaded automatically (and kept on the phone, so they also work offline).
 *
 * Priority for each year:  1) holidays.json from your GitHub (your own manual fix)
 *                          2) the public Bangladesh holiday calendar feed (updates itself when the moon-dependent dates are confirmed)
 *                          3) the dates built into the app
 *
 * holidays.json format (optional, only needed to override the feed):
 * { "2027": { "g": [ { "d": "03-10", "en": "Eid-ul-Fitr", "bn": "ঈদুল ফিতর" } ],     // government holidays (replaces the list)
 *                  "o": [ { "d": "02-14", "en": "Some day", "bn": "কোনো দিবস" } ] } } // extra observances (added)
 */
object HolRemote {
    /** Your own override file. Change user / repository / branch if yours are different. */
    const val URL = "https://raw.githubusercontent.com/darkrider13/TaskFlow-v2/main/holidays.json"

    /** Public calendar feed with all Bangladesh government holidays (the same kind of feed a phone calendar subscribes to). */
    const val ICS_EN = "https://www.bangladatetoday.com/api/ical/holidays-bd.ics"
    const val ICS_BN = "https://www.bangladatetoday.com/api/ical/holidays-bd.ics?lang=bn"

    class RItem(val md: String, val en: String, val bn: String)

    private var dir: File? = null
    private val gJson = HashMap<Int, List<RItem>>()
    private val oJson = HashMap<Int, List<RItem>>()
    private val gIcs = HashMap<Int, List<RItem>>()
    private var lastJson = ""
    private var lastEn = ""
    private var lastBn = ""

    fun g(y: Int): List<RItem>? = synchronized(this) { gJson[y] ?: gIcs[y] }
    fun o(y: Int): List<RItem>? = synchronized(this) { oJson[y] }

    /** Load the copies saved on the phone (no internet needed). */
    fun init(ctx: Context) {
        dir = ctx.filesDir
        try { File(ctx.filesDir, "holidays_remote.json").takeIf { it.exists() }?.readText()?.let { applyJson(it); lastJson = it } } catch (e: Exception) { }
        try {
            val en = File(ctx.filesDir, "holidays_feed_en.ics").takeIf { it.exists() }?.readText()
            val bn = File(ctx.filesDir, "holidays_feed_bn.ics").takeIf { it.exists() }?.readText() ?: ""
            if (en != null) { applyIcs(en, bn); lastEn = en; lastBn = bn }
        } catch (e: Exception) { }
    }

    // ---------------- holidays.json ----------------

    private fun items(a: JSONArray?): List<RItem> {
        val r = ArrayList<RItem>()
        if (a == null) return r
        for (i in 0 until a.length()) {
            val x = a.optJSONObject(i) ?: continue
            val d = x.optString("d")
            if (!Regex("\\d\\d-\\d\\d").matches(d)) continue
            val en = x.optString("en").ifBlank { x.optString("bn") }
            if (en.isBlank()) continue
            r.add(RItem(d, en, x.optString("bn").ifBlank { en }))
        }
        return r
    }

    /** Throws if the text is not a valid list, so a broken file never replaces a good one. */
    private fun applyJson(text: String) {
        val j = JSONObject(text)
        val g = HashMap<Int, List<RItem>>()
        val o = HashMap<Int, List<RItem>>()
        for (k in j.keys()) {
            val y = k.toIntOrNull() ?: continue
            val yo = j.optJSONObject(k) ?: continue
            if (yo.has("g")) g[y] = items(yo.optJSONArray("g"))
            if (yo.has("o")) o[y] = items(yo.optJSONArray("o"))
        }
        synchronized(this) { gJson.clear(); gJson.putAll(g); oJson.clear(); oJson.putAll(o) }
    }

    // ---------------- calendar feed (.ics) ----------------

    private class Ev(val day: LocalDate, val name: String)

    private fun unescape(s: String) = s.replace("\\n", " ").replace("\\N", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\").trim()

    /** Date of an ICS date / date-time value. Times in UTC (...Z) are moved to Bangladesh time (+6). */
    private fun icsDate(v: String): Pair<LocalDate, Boolean>? {
        val t = v.trim()
        if (t.length < 8) return null
        return try {
            if (t.length >= 15 && t[8] == 'T') {
                var dt = LocalDateTime.parse(t.substring(0, 15), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                if (t.endsWith("Z")) dt = dt.plusHours(6)
                Pair(dt.toLocalDate(), dt.toLocalTime().toSecondOfDay() != 0)
            } else Pair(LocalDate.parse(t.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE), false)
        } catch (e: Exception) { null }
    }

    private fun events(text: String): List<Ev> {
        val out = ArrayList<Ev>()
        val lines = text.replace("\r\n", "\n").replace("\r", "\n").replace(Regex("\n[ \t]"), "").split("\n")
        var inEv = false
        var s: LocalDate? = null; var e: LocalDate? = null; var eHasTime = false; var name = ""
        for (ln in lines) {
            if (ln.startsWith("BEGIN:VEVENT")) { inEv = true; s = null; e = null; eHasTime = false; name = ""; continue }
            if (ln.startsWith("END:VEVENT")) {
                val st = s
                if (inEv && st != null && name.isNotBlank()) {
                    var last = st
                    val en = e
                    if (en != null && en.isAfter(st)) last = if (eHasTime) en else en.minusDays(1)   // all-day DTEND is exclusive
                    if (last.isAfter(st.plusDays(14))) last = st
                    var d = st
                    while (!d.isAfter(last)) { out.add(Ev(d, name)); d = d.plusDays(1) }
                }
                inEv = false; continue
            }
            if (!inEv) continue
            val c = ln.indexOf(':')
            if (c < 0) continue
            val key = ln.substring(0, c).substringBefore(';').uppercase()
            val v = ln.substring(c + 1)
            when (key) {
                "DTSTART" -> s = icsDate(v)?.first
                "DTEND" -> icsDate(v)?.let { e = it.first; eHasTime = it.second }
                "SUMMARY" -> name = unescape(v)
            }
        }
        return out
    }

    /** Throws when the feed has nothing usable. A year is only taken when the feed lists at least 5 holidays for it. */
    private fun applyIcs(en: String, bn: String) {
        val ee = events(en)
        val bb = if (bn.isBlank()) emptyList() else events(bn)
        val bnBy = bb.groupBy { it.day }
        val res = HashMap<Int, MutableList<RItem>>()
        val seen = HashMap<LocalDate, Int>()
        for (x in ee) {
            val i = seen.getOrDefault(x.day, 0); seen[x.day] = i + 1
            val b = bnBy[x.day]?.getOrNull(i)?.name ?: x.name
            val md = String.format("%02d-%02d", x.day.monthValue, x.day.dayOfMonth)
            res.getOrPut(x.day.year) { ArrayList() }.add(RItem(md, x.name, b))
        }
        val ok = res.filter { it.value.size >= 5 }
        if (ok.isEmpty()) throw IllegalStateException("empty feed")
        synchronized(this) { gIcs.clear(); for ((y, l) in ok) gIcs[y] = l }
    }

    // ---------------- downloading ----------------

    private fun get(u: String): String? {
        return try {
            val c = URL(u + (if (u.contains("?")) "&" else "?") + "t=" + System.currentTimeMillis()).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.useCaches = false
            if (c.responseCode != 200) null else c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) { null }
    }

    /** Download the newest lists. Returns true when something new was applied. Call from a background thread. */
    fun fetch(): Boolean {
        var changed = false
        // 1) your own override file
        try {
            val t = get(URL)
            if (t != null && t != lastJson) {
                applyJson(t)
                dir?.let { File(it, "holidays_remote.json").writeText(t) }
                lastJson = t; changed = true
            }
        } catch (e: Exception) { }
        // 2) the public holiday feed (English names + Bangla names)
        try {
            val en = get(ICS_EN)
            if (en != null) {
                val bn = get(ICS_BN) ?: ""
                if (en != lastEn || bn != lastBn) {
                    applyIcs(en, bn)
                    dir?.let { File(it, "holidays_feed_en.ics").writeText(en); File(it, "holidays_feed_bn.ics").writeText(bn) }
                    lastEn = en; lastBn = bn; changed = true
                }
            }
        } catch (e: Exception) { }
        if (changed) Hol.reset()
        return changed
    }
}
