package com.habit.tracker

import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Holiday engine, ported from the original app (offline part):
 * fixed government holidays, the 2026 gazette list, Hijri dates, Easter, and Hindu / Buddhist
 * festivals worked out from the real sun and moon positions.
 * (Online list: see HolRemote.kt - holidays.json from GitHub overrides the guessed dates.)
 */
object Hol {
    class Item(val en: String, val bn: String, val u: Int = 0)
    class Year(val g: Map<String, List<Item>>, val o: Map<String, List<Item>>)
    class Info(val g: List<String>, val o: List<String>, val gx: List<String>)

    private const val D = 86_400_000.0
    private const val HR = 3_600_000.0
    private val cache = HashMap<Int, Year>()

    private fun p2(n: Int) = n.toString().padStart(2, '0')

    /** UTC date key "yyyy-MM-dd" of an epoch-millisecond value. */
    private fun hKey(ms: Double): String = LocalDate.ofEpochDay(floor(ms / D).toLong()).toString()

    private fun utc(y: Int, m1: Int, d: Int): Double = LocalDate.of(y, m1, 1).plusDays((d - 1).toLong()).toEpochDay() * D

    /** Hijri (tabular) -> Gregorian, shifted by the Bangladesh moon-sighting offset. */
    private fun hG(hy: Int, hm: Int, hd: Int, off: Int): Double {
        val jd = hd + ceil(29.5 * (hm - 1)) + (hy - 1) * 354 + floor((3 + 11.0 * hy) / 30) + 1948439.5 - 1
        return ((jd - 2440587.5 + off) * D).toLong().toDouble()
    }

    private fun hAdd(d: Double, n: Int) = d + n * D

    private fun utcDow(ms: Double): Int = (LocalDate.ofEpochDay(floor(ms / D).toLong()).dayOfWeek.value) % 7

    private fun hNth(y: Int, m0: Int, wd: Int, n: Int): Double {
        val f = utc(y, m0 + 1, 1)
        val sh = (wd - utcDow(f) + 7) % 7
        return utc(y, m0 + 1, 1 + sh + (n - 1) * 7)
    }

    private fun hEaster(y: Int): Double {
        val a = y % 19; val b = y / 100; val c = y % 100; val d = b / 4; val e = b % 4
        val f = (b + 8) / 25; val g = (b - f + 1) / 3; val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4; val k = c % 4; val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val mo = (h + l - 7 * m + 114) / 31
        val da = ((h + l - 7 * m + 114) % 31) + 1
        return utc(y, mo, da)
    }

    // ---- sun / moon ----
    private fun hxM(x: Double, n: Double) = ((x % n) + n) % n
    private const val R = PI / 180
    private fun s(x: Double) = sin(x * R)
    private fun hxT(t: Double) = (t / D + 2440587.5 - 2451545) / 36525

    private fun hxSun(t: Double): Double {
        val T = hxT(t)
        val M = 357.52911 + 35999.05029 * T
        return hxM(280.46646 + 36000.76983 * T + (1.914602 - 0.004817 * T) * s(M) + 0.019993 * s(2 * M) + 0.000289 * s(3 * M) - 0.00569 - 0.00478 * s(125.04 - 1934.136 * T), 360.0)
    }

    private fun hxMoon(t: Double): Double {
        val T = hxT(t)
        val L = 218.3164477 + 481267.88123421 * T
        val Dd = 297.8501921 + 445267.1114034 * T
        val M = 357.5291092 + 35999.0502909 * T
        val P = 134.9633964 + 477198.8675055 * T
        val F = 93.272095 + 483202.0175233 * T
        return hxM(
            L + 6.288774 * s(P) + 1.274027 * s(2 * Dd - P) + 0.658314 * s(2 * Dd) + 0.213618 * s(2 * P) - 0.185116 * s(M) - 0.114332 * s(2 * F) +
                0.058793 * s(2 * Dd - 2 * P) + 0.057066 * s(2 * Dd - M - P) + 0.053322 * s(2 * Dd + P) + 0.045758 * s(2 * Dd - M) - 0.040923 * s(M - P) -
                0.03472 * s(Dd) - 0.030383 * s(M + P) + 0.015327 * s(2 * Dd - 2 * F) - 0.012528 * s(P + 2 * F) + 0.01098 * s(P - 2 * F) +
                0.010675 * s(4 * Dd - P) + 0.010034 * s(3 * P) + 0.008548 * s(4 * Dd - 2 * P) - 0.007888 * s(2 * Dd + M - P) - 0.006766 * s(2 * Dd + M) -
                0.005163 * s(Dd - P) + 0.004987 * s(Dd + M) + 0.004036 * s(2 * Dd - M + P) + 0.003994 * s(2 * Dd + 2 * P) + 0.003861 * s(4 * Dd) +
                0.003665 * s(2 * Dd - 3 * P),
            360.0,
        )
    }

    private fun hxEl(t: Double) = hxM(hxMoon(t) - hxSun(t), 360.0)
    private fun hxTi(t: Double) = floor(hxEl(t) / 12).toInt() + 1
    private fun hxSid(t: Double) = hxM(hxSun(t) - (23.853 + 0.013969 * ((t / D + 2440587.5 - 2451545) / 365.25)), 360.0)

    private fun hxNew(y: Int): List<Double> {
        val o = ArrayList<Double>()
        val f = { x: Double -> hxM(hxEl(x) + 180, 360.0) - 180 }
        val h6 = 216e5
        var t = utc(y - 1, 11, 20)
        var pf = f(t)
        t += h6
        val end = utc(y + 1, 2, 10)
        while (t < end) {
            val cf = f(t)
            if (pf < 0 && cf >= 0 && pf > -90) {
                var a = t - h6
                var b = t
                for (i in 0 until 22) {
                    val m = (a + b) / 2
                    if (f(m) < 0) a = m else b = m
                }
                o.add(b)
            }
            pf = cf
            t += h6
        }
        return o
    }

    private class Hindu(val g: MutableList<Triple<String, String, String>> = ArrayList(), val o: MutableList<Triple<String, String, String>> = ArrayList())

    private fun holHindu(y: Int): Hindu {
        val nm = hxNew(y)
        val r = Hindu()
        fun put(L: MutableList<Triple<String, String, String>>, d: Double, e: String, b: String) {
            val k = hKey(d)
            if (k.substring(0, 4).toInt() == y) L.add(Triple(k.substring(5), e, b))
        }
        class Mo(val s: Double, val e: Double, val sg: Int, val ok: Boolean)
        val ms = ArrayList<Mo>()
        for (i in 0 until nm.size - 1) {
            val a = floor(hxSid(nm[i]) / 30).toInt()
            ms.add(Mo(nm[i], nm[i + 1], a, a != floor(hxSid(nm[i + 1]) / 30).toInt()))
        }
        val d0 = { t: Double -> floor((t + 6 * HR) / D) * D }
        val bd = { d: Double, h: Int -> d + (h - 6) * HR }
        val tS = { d: Double -> hxTi(bd(d, 6)) }
        val tN = { d: Double -> hxTi(bd(d, 20)) }
        val tM = { d: Double -> hxTi(bd(d, 22)) }
        val days = { m: Mo ->
            val l = ArrayList<Double>()
            var d = d0(m.s)
            val e = d0(m.e) + D
            while (d <= e) { l.add(d); d += D }
            l
        }
        val last = { m: Mo, t: Int ->
            var q: Double? = null
            for (d in days(m)) if (tS(d) == t) q = d
            q
        }
        for (n in nm) {
            if (floor(hxSid(n) / 30).toInt() == 5 && hKey(n).substring(0, 4).toInt() == y) {
                val d = d0(n)
                put(r.o, if (tS(d - D) == 30) d - D else if (tS(d) == 30) d else d - D, "Mahalaya", "মহালয়া")
                break
            }
        }
        for (m in ms) {
            if (!m.ok) continue
            if (m.sg == 5) {
                val list = listOf(
                    listOf(6, "Maha Shashthi", "মহাষষ্ঠী", "o"), listOf(7, "Maha Saptami", "মহাসপ্তমী", "o"), listOf(8, "Maha Ashtami", "মহাঅষ্টমী", "o"),
                    listOf(9, "Durga Puja (Mahanabami)", "দুর্গাপূজা (মহানবমী)", "g"), listOf(10, "Durga Puja (Bijoya Dashami)", "দুর্গাপূজা (বিজয়া দশমী)", "g"),
                )
                for (x in list) {
                    val t = x[0] as Int
                    val d = last(m, t)
                    if (d != null) {
                        put(if (x[3] == "g") r.g else r.o, d, x[1] as String, x[2] as String)
                        if (t == 10) put(r.g, d + D, "Durga Puja holiday", "দুর্গাপূজার ছুটি")
                    }
                }
                var lp: Double? = null
                var kp: Double? = null
                for (d in days(m)) {
                    if (tN(d) == 15 && d >= d0(m.s)) lp = d
                    if (tM(d) == 30 && d >= d0(m.s) + 10 * D) kp = d
                }
                if (lp != null) put(r.o, lp, "Lakshmi Puja", "লক্ষ্মী পূজা")
                if (kp != null) {
                    put(r.o, kp, "Kali Puja / Diwali", "কালী পূজা / দীপাবলি")
                    var d = kp + D
                    while (d <= kp + 6 * D) {
                        if (tS(d) == 2) { put(r.o, d, "Bhai Phota", "ভাইফোঁটা"); break }
                        d += D
                    }
                }
            }
            if (m.sg == 0) {
                val d = last(m, 15)
                if (d != null) put(r.g, d, "Buddha Purnima", "বুদ্ধ পূর্ণিমা")
            }
            if (m.sg == 2) {
                val d = last(m, 2)
                if (d != null) put(r.o, d, "Rath Yatra", "রথযাত্রা")
            }
            if (m.sg == 3) {
                var j: Double? = null
                for (d in days(m)) if (tM(d) == 23 && d >= d0(m.s) + 10 * D) { j = d; break }
                if (j != null) put(r.g, j, "Janmashtami", "জন্মাষ্টমী")
            }
            if (m.sg == 9) {
                val d = last(m, 5)
                if (d != null) put(r.o, d, "Saraswati Puja", "সরস্বতী পূজা")
                var q: Double? = null
                for (x in days(m)) if (tM(x) == 29 && x >= d0(m.s) + 10 * D) q = x
                if (q != null) put(r.o, q, "Maha Shivaratri", "মহাশিবরাত্রি")
            }
            if (m.sg == 10) {
                val d = last(m, 15)
                if (d != null) put(r.o, d, "Dol Jatra", "দোলযাত্রা")
            }
        }
        return r
    }

    private class Calc(val g: MutableList<Triple<String, String, String>> = ArrayList(), val o: MutableList<Triple<String, String, String>> = ArrayList())

    private fun holCalc(y: Int, withG: Boolean): Calc {
        val c = Calc()
        fun put(L: MutableList<Triple<String, String, String>>, d: Double, e: String, b: String) {
            val k = hKey(d)
            if (k.substring(0, 4).toInt() == y) L.add(Triple(k.substring(5), e, b))
        }
        for (hy in (y - 581)..(y - 576)) {
            val barat = hG(hy, 8, 15, 1); val qadr = hG(hy, 9, 27, 1); val fitr = hG(hy, 10, 1, 1)
            val adha = hG(hy, 12, 10, 1); val ash = hG(hy, 1, 10, 0); val mil = hG(hy, 3, 12, 0)
            if (withG) {
                put(c.g, barat, "Shab-e-Barat", "শবে বরাত"); put(c.g, qadr, "Shab-e-Qadr", "শবে কদর")
                var jw = hAdd(fitr, -1)
                while (utcDow(jw) != 5) jw = hAdd(jw, -1)
                put(c.g, jw, "Jumatul Wida", "জুমাতুল বিদা")
                for (i in -2..2) put(c.g, hAdd(fitr, i), if (i != 0) "Eid-ul-Fitr holiday" else "Eid-ul-Fitr", if (i != 0) "ঈদুল ফিতরের ছুটি" else "ঈদুল ফিতর")
                for (i in -3..3) put(c.g, hAdd(adha, i), if (i != 0) "Eid-ul-Adha holiday" else "Eid-ul-Adha", if (i != 0) "ঈদুল আযহার ছুটি" else "ঈদুল আযহা")
                put(c.g, ash, "Ashura", "আশুরা"); put(c.g, mil, "Eid-e-Miladunnabi", "ঈদে মিলাদুন্নবী")
            }
            put(c.o, hG(hy, 9, 1, 1), "Ramadan begins", "রমজান শুরু"); put(c.o, hG(hy, 7, 27, 1), "Shab-e-Meraj", "শবে মেরাজ"); put(c.o, hG(hy, 1, 1, 0), "Islamic New Year", "ইসলামি নববর্ষ")
        }
        val ea = hEaster(y)
        put(c.o, hAdd(ea, -2), "Good Friday", "গুড ফ্রাইডে"); put(c.o, ea, "Easter Sunday", "ইস্টার সানডে")
        put(c.o, hNth(y, 4, 0, 2), "Mother's Day", "মা দিবস"); put(c.o, hNth(y, 5, 0, 3), "Father's Day", "বাবা দিবস"); put(c.o, hNth(y, 7, 0, 1), "Friendship Day", "বন্ধু দিবস")
        val hx = holHindu(y)
        if (withG) c.g.addAll(hx.g)
        c.o.addAll(hx.o)
        return c
    }

    private fun nk(z: String) = z.lowercase().filter { it in 'a'..'z' || it in '\u0980'..'\u09ff' }
    private fun pt(e: String) = e.split(" / ").map { nk(it) }.filter { it.length > 3 }

    /** Forget the worked-out tables (used after a new online list arrives). */
    @Synchronized
    fun reset() { cache.clear(); infoCache.clear() }

    @Synchronized
    fun holYear(y: Int): Year {
        cache[y]?.let { return it }
        val g = LinkedHashMap<String, MutableList<Item>>()
        val o = LinkedHashMap<String, MutableList<Item>>()
        fun add(m: MutableMap<String, MutableList<Item>>, md: String, e: String, b: String, u: Int = 0) {
            val a = m.getOrPut(md) { ArrayList() }
            if (a.none { it.en == e }) a.add(Item(e, b, u))
        }
        for ((md, v) in HolData.FIXED) {
            if (md == "08-05" && y < 2025) continue
            add(g, md, v.en, v.bn)
        }
        val yb = HolData.YEARLY[y]
        val rg = HolRemote.g(y)
        if (rg != null) for (x in rg) add(g, x.md, x.en, x.bn)          // online list for this year: exact dates, never "unconfirmed"
        else if (yb != null) for ((md, v) in yb) add(g, md, v.en, v.bn)
        else for (x in holCalc(y, true).g) add(g, x.first, x.second, x.third, 1)
        for ((md, v) in HolData.OTHER_FIXED) add(o, md, v.en, v.bn)
        for (x in holCalc(y, false).o) add(o, x.first, x.second, x.third)
        HolRemote.o(y)?.forEach { add(o, it.md, it.en, it.bn) }
        // an observance that is also a government holiday moves out of "other"
        val gk = ArrayList<String>()
        for (list in g.values) for (x in list) { gk.addAll(pt(x.en)); gk.addAll(pt(x.bn)) }
        val same = { a: String, b: String -> a == b || (a.length > 5 && b.length > 5 && (a.contains(b) || b.contains(a))) }
        for (md in o.keys.toList()) {
            o[md] = o[md]!!.filter { x -> !(pt(x.en) + pt(x.bn)).any { a -> gk.any { b -> same(a, b) } } }.toMutableList()
            if (o[md]!!.isEmpty()) o.remove(md)
        }
        val res = Year(g, o)
        cache[y] = res
        return res
    }

    /** Bangla names for holidays that only have English names. */
    private fun bnFix(en: String, bn: String): String {
        if (bn.any { it in '\u0980'..'\u09ff' }) return bn
        fun tr(p: String): String {
            val q = p.lowercase()
            val h = Regex("holiday|ছুটি").containsMatchIn(q)
            fun m(r: String) = Regex(r).containsMatchIn(q)
            return when {
                m("ekushey|shaheed|mother language|language martyr") -> "শহীদ দিবস ও আন্তর্জাতিক মাতৃভাষা দিবস"
                m("independence") -> "স্বাধীনতা দিবস"
                m("victory") -> "বিজয় দিবস"
                m("christmas eve") -> "বড়দিনের আগের দিন"
                m("christmas") -> "বড়দিন"
                m("bengali new year|pohela|pahela|boishakh|baishakh") -> "পহেলা বৈশাখ"
                m("may day|labou?r day|workers") -> "মে দিবস"
                m("solidarity") -> "জাতীয় বিপ্লব ও সংহতি দিবস"
                m("july|uprising") -> "জুলাই গণঅভ্যুত্থান দিবস"
                m("barat") -> "শবে বরাত"
                m("qadr|qadar|laylat|lailat") -> "শবে কদর"
                m("jumatul|wida|bida|vida") -> "জুমাতুল বিদা"
                m("fitr") -> if (h) "ঈদুল ফিতরের ছুটি" else "ঈদুল ফিতর"
                m("adha|azha|azah") -> if (h) "ঈদুল আযহার ছুটি" else "ঈদুল আযহা"
                m("milad") -> "ঈদে মিলাদুন্নবী"
                m("ashura") -> "আশুরা"
                m("buddha|purnima") -> "বুদ্ধ পূর্ণিমা"
                m("janmashtami") -> "জন্মাষ্টমী"
                m("durga|nabami|navami|dashami|bijoya") -> if (m("nabami|navami")) "দুর্গাপূজা (মহানবমী)" else if (m("dashami|bijoya")) "দুর্গাপূজা (বিজয়া দশমী)" else if (h) "দুর্গাপূজার ছুটি" else "দুর্গাপূজা"
                m("good friday") -> "গুড ফ্রাইডে"
                m("easter") -> "ইস্টার সানডে"
                m("ramadan|ramzan") -> "রমজান শুরু"
                m("meraj|miraj") -> "শবে মেরাজ"
                m("islamic new year|hijri") -> "ইসলামি নববর্ষ"
                else -> p
            }
        }
        return en.split(" / ").joinToString(" / ") { tr(it) }
    }

    /** Everyday short names for the two holidays people call by one name. */
    private fun shortBn(en: String, bn: String): String {
        val t = en + " " + bn
        return when {
            Regex("ekushey|mother language|language martyr|একুশে|মাতৃভাষা", RegexOption.IGNORE_CASE).containsMatchIn(t) -> "একুশে ফেব্রুয়ারি"
            Regex("pohela boishakh|pahela baishakh|pahela boishakh|pohela baishakh|bengali new year|পহেলা বৈশাখ|বাংলা নববর্ষ", RegexOption.IGNORE_CASE).containsMatchIn(t) -> "পহেলা বৈশাখ"
            else -> bn
        }
    }

    private val MN = Regex("barat|qadr|jumatul|wida|bida|fitr|adha|azha|ashura|milad", RegexOption.IGNORE_CASE)

    /** Holidays of one day: g = government, o = other observances, gx = government with "(unconfirmed)" notes. */
    private val infoCache = HashMap<String, Info>()
    private val RX_WIDA = Regex("Jumatul\\s+(?:Bida|Vida)", RegexOption.IGNORE_CASE)
    private val RX_PAREN = Regex("\\s*[(（][^)）]*[)）]")
    private val RX_SPACES = Regex("\\s{2,}")

    fun info(k: String): Info {
        val ck = k + "|" + Str.isBn + "|" + LocalDate.now()
        infoCache[ck]?.let { return it }
        val r = infoCalc(k)
        if (infoCache.size > 4000) infoCache.clear()
        infoCache[ck] = r
        return r
    }

    private fun infoCalc(k: String): Info {
        val y = k.substring(0, 4).toInt()
        val md = k.substring(5)
        val bn = Str.isBn
        val Y = holYear(y)
        fun nmf(x: Item): String {
            val n = (if (bn) shortBn(x.en, bnFix(x.en, x.bn)) else x.en).replace(RX_WIDA, "Jumatul Wida")
            val c = n.replace(RX_PAREN, "").replace(RX_SPACES, " ").trim()
            return c.ifEmpty { n }
        }
        val dd = try { java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(k)).toInt() } catch (e: Exception) { 0 }
        fun nm(a: List<Item>?) = (a ?: emptyList()).map { nmf(it) }.distinct()
        fun nx(a: List<Item>?) = (a ?: emptyList()).map { x ->
            nmf(x) + (if (x.u != 0 || (MN.containsMatchIn(x.en) && dd > 3)) " (" + (if (bn) "অনিশ্চিত" else "unconfirmed") + ")" else "")
        }.distinct()
        return Info(nm(Y.g[md]), nm(Y.o[md]), nx(Y.g[md]))
    }
}
