package com.habit.tracker

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Reminders: habit time, task time, interval reminders and the focus timer end.
 * Each item keeps ONE alarm for its next time; when it rings, the next one is set.
 * Yes / No / Snooze buttons work without opening the app (answers are saved and applied when the app opens).
 */
object Notifs {
    private const val PREFS = "taskflow_native"
    private const val KEY_CODES = "codes"
    private const val KEY_Q = "pending"
    const val ACT_FIRE = "taskflow.FIRE"
    const val ACT_BTN = "taskflow.BTN"
    private const val SNOOZE_MS = 10 * 60 * 1000L
    private const val FOCUS_CODE = 4_000_000

    private fun lang(d: JSONObject) = d.optJSONObject("set")?.optString("lang", "bn") ?: "bn"
    private fun lt(l: String, k: String) = (if (l == "bn") StrData.bn else StrData.en)[k] ?: StrData.en[k] ?: k
    private fun nlb(l: String, k: String, b: String) = lt(l, k) + " · " + b
    private fun notifB(l: String, n: String) = if (l == "bn") "আজকের $n সম্পন্ন হয়েছে কি?" else "Did you complete today's $n?"
    private fun todoB(l: String, n: String) = if (l == "bn") "$n - কাজটি করেছেন কি?" else "$n - did you do it?"

    private fun remBody(l: String, name: String): String {
        val n = name.trim().replace(Regex("[,।.?\\s]+$"), "")
        if (l != "bn") return "Time for $n - remember?"
        return when {
            Regex("সময়\\s*হয়েছে$").containsMatchIn(n) -> "$n, মনে আছে তো?"
            Regex("সময়$").containsMatchIn(n) -> "$n হয়েছে, মনে আছে তো?"
            Regex("ার$").containsMatchIn(n) -> "$n সময় হয়েছে, মনে আছে তো?"
            else -> "$n: সময় হয়েছে, মনে আছে তো?"
        }
    }

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel("default", "Default", NotificationManager.IMPORTANCE_HIGH))
            val ch = NotificationChannel("reminder", "Reminder", NotificationManager.IMPORTANCE_HIGH)
            val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            ch.setSound(uri, android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            ch.enableVibration(true)
            ch.vibrationPattern = longArrayOf(0, 500, 300, 500, 300, 500)
            ch.lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(ch)
        }
    }

    /** Android 14+: the reminder screen needs the "full screen notification" permission (usually already allowed). */
    fun askFullScreen(ctx: Context, onToast: (String) -> Unit) {
        if (Build.VERSION.SDK_INT < 34) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.canUseFullScreenIntent()) return
        onToast(lt(lang(Store.s), "fsiAsk"))
        try {
            ctx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                .setData(android.net.Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { }
    }

    /** Alarm-like reminder: rings and opens the full reminder screen (even on the lock screen). */
    fun showReminder(ctx: Context, nid: Int, kind: String, eid: Long, title: String, body: String, time: String, withSnooze: Boolean) {
        ensureChannel(ctx)
        val i = Intent(ctx, ReminderActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("nid", nid).putExtra("kind", kind).putExtra("eid", eid)
            .putExtra("title", title).putExtra("body", body).putExtra("time", time).putExtra("snooze", withSnooze)
        val pi = PendingIntent.getActivity(ctx, nid, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b = NotificationCompat.Builder(ctx, "reminder")
            .setSmallIcon(R.drawable.ic_stat_icon)
            .setColor(0xFF2196F3.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setTimeoutAfter(3 * 60 * 1000L)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
        val n = b.build()
        n.flags = n.flags or android.app.Notification.FLAG_INSISTENT // keep ringing until it is turned off
        try { NotificationManagerCompat.from(ctx).notify(nid, n) } catch (e: SecurityException) { }
    }

    /** Show a habit / task alert the way the user chose for it (notification or reminder screen). */
    private fun alert(ctx: Context, item: JSONObject, nid: Int, kind: String, eid: Long, title: String, body: String, time: String, withSnooze: Boolean) {
        if (item.optString("alert") == "rem") showReminder(ctx, nid, kind, eid, title, body, time, withSnooze)
        else show(ctx, nid, "", body, kind, eid, withSnooze)
    }

    private fun fireIntent(ctx: Context, kind: String, eid: Long): Intent =
        Intent(ctx, AlarmReceiver::class.java).setAction(ACT_FIRE).putExtra("kind", kind).putExtra("eid", eid)

    /**
     * clock = true: set as a real "alarm clock" alarm (same kind the phone's own Clock app uses).
     * The system treats it as top priority: it rings on time even in sleep/Doze, nothing has to run in the background.
     */
    private fun setAlarm(ctx: Context, code: Int, atMs: Long, intent: Intent, clock: Boolean = false) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(ctx, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (clock) {
            val show = openPi(ctx, code)
            if (show != null) {
                try { am.setAlarmClock(AlarmManager.AlarmClockInfo(atMs, show), pi); return } catch (e: SecurityException) { }
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
            else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
        }
    }

    private fun cancelCode(ctx: Context, code: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(ctx, code, Intent(ctx, AlarmReceiver::class.java).setAction(ACT_FIRE), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pi != null) { am.cancel(pi); pi.cancel() }
    }

    private fun ms(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun parseHm(s: String?, def: String = "00:00"): Pair<Int, Int> {
        val q = (s ?: def).ifEmpty { def }.split(":")
        return (q.getOrNull(0)?.toIntOrNull() ?: 0) to (q.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    /** Next time (after "after") the habit should ring. */
    private fun nextHabit(a: JSONObject, after: LocalDateTime): LocalDateTime? {
        val (h, m) = parseHm(a.optString("time"))
        val days = a.optJSONArray("days")
        for (off in 0..8) {
            val d = after.toLocalDate().plusDays(off.toLong())
            val ok = days == null || days.length() == 0 || (0 until days.length()).any { days.optInt(it) == d.dayOfWeek.value % 7 }
            if (!ok) continue
            val t = d.atTime(h, m)
            if (t.isAfter(after)) return t
        }
        return null
    }

    private fun remSlots(x: JSONObject): List<Int> {
        val (fh, fm) = parseHm(x.optString("from"))
        val (th, tm) = parseHm(x.optString("to"), "23:59")
        val a = fh * 60 + fm
        var b = th * 60 + tm
        if (b <= a) b += 1440
        val im = remInt(x).coerceAtLeast(1)
        val o = ArrayList<Int>()
        var m = a
        while (m <= b && o.size < 150) { val v = m % 1440; if (!o.contains(v)) o.add(v); m += im }
        return o
    }

    private fun nextRem(x: JSONObject, after: LocalDateTime): LocalDateTime? {
        val slots = remSlots(x)
        var best: LocalDateTime? = null
        for (off in 0..1) {
            val d = after.toLocalDate().plusDays(off.toLong())
            for (s in slots) {
                val t = d.atTime(s / 60, s % 60)
                if (t.isAfter(after) && (best == null || t.isBefore(best))) best = t
            }
        }
        return best
    }

    /** Cancel everything we set before and set the next alarm of every habit / task / reminder. */
    @Synchronized
    fun scheduleAll(ctx: Context, d: JSONObject, skipKind: String? = null, skipId: Long = 0L, leadMs: Long = 0L) {
        ensureChannel(ctx)
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        for (c in sp.getStringSet(KEY_CODES, emptySet()) ?: emptySet()) c.toIntOrNull()?.let { cancelCode(ctx, it) }
        val codes = HashSet<String>()
        val now = LocalDateTime.now().plusNanos(leadMs * 1_000_000)
        for (a in d.optJSONArray("acts").list()) {
            val t = nextHabit(a, now) ?: continue
            val code = 1_000_000 + a.optInt("nid")
            setAlarm(ctx, code, ms(t), fireIntent(ctx, "habit", a.optLong("id")), a.optString("alert") == "rem")
            codes.add(code.toString())
        }
        for (x in d.optJSONArray("todos").list()) {
            if (x.optBoolean("done") || x.optString("time").isEmpty()) continue
            if (skipKind == "todo" && skipId == x.optLong("id")) continue
            val t = try { LocalDate.parse(x.optString("date")).atTime(LocalTime.parse(x.optString("time"))) } catch (e: Exception) { continue }
            if (!t.isAfter(LocalDateTime.now())) continue
            val code = 2_000_000 + x.optInt("nid")
            setAlarm(ctx, code, ms(t), fireIntent(ctx, "todo", x.optLong("id")), x.optString("alert") == "rem")
            codes.add(code.toString())
        }
        for (x in d.optJSONArray("rems").list()) {
            if (!x.optBoolean("on")) continue
            val t = nextRem(x, now) ?: continue
            val code = 3_000_000 + x.optInt("nid")
            setAlarm(ctx, code, ms(t), fireIntent(ctx, "rem", x.optLong("id")))
            codes.add(code.toString())
        }
        sp.edit().putStringSet(KEY_CODES, codes).apply()
    }

    /** Focus timer: ring when the session ends. */
    fun focus(ctx: Context, on: Boolean, endMs: Long, focusMode: Boolean) {
        cancelCode(ctx, FOCUS_CODE)
        if (!on) return
        ensureChannel(ctx)
        val d = Store.s
        val l = lang(d)
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(ACT_FIRE).putExtra("kind", "focus")
            .putExtra("title", lt(l, if (focusMode) "fDoneT" else "fBrkT")).putExtra("body", lt(l, if (focusMode) "fDoneB" else "fBrkB"))
        setAlarm(ctx, FOCUS_CODE, endMs, i)
    }

    private fun openPi(ctx: Context, code: Int): PendingIntent? {
        val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: return null
        return PendingIntent.getActivity(ctx, code, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun btnPi(ctx: Context, nid: Int, kind: String, eid: Long, aid: String): PendingIntent {
        val i = Intent(ctx, ActionReceiver::class.java).setAction(ACT_BTN + "." + nid + "." + aid)
            .putExtra("nid", nid).putExtra("kind", kind).putExtra("eid", eid).putExtra("aid", aid)
        return PendingIntent.getBroadcast(ctx, nid * 7 + aid.hashCode() % 1000, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun show(ctx: Context, nid: Int, title: String, body: String, kind: String?, eid: Long, withSnooze: Boolean) {
        ensureChannel(ctx)
        val b = NotificationCompat.Builder(ctx, "default")
            .setSmallIcon(R.drawable.ic_stat_icon)
            .setColor(0xFF2196F3.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        openPi(ctx, nid)?.let { b.setContentIntent(it) }
        if (kind != null) {
            val l = lang(Store.readFile(ctx))
            b.addAction(0, lt(l, "yesA"), btnPi(ctx, nid, kind, eid, "yes"))
            b.addAction(0, lt(l, "noA"), btnPi(ctx, nid, kind, eid, "no"))
            if (withSnooze) b.addAction(0, lt(l, "snooze"), btnPi(ctx, nid, kind, eid, "snooze"))
        }
        try { NotificationManagerCompat.from(ctx).notify(nid, b.build()) } catch (e: SecurityException) { }
    }

    /** An alarm rang. */
    fun onFire(ctx: Context, kind: String, eid: Long, extras: Intent) {
        val d = Store.readFile(ctx)
        val l = lang(d)
        when (kind) {
            "focus" -> { show(ctx, FOCUS_CODE, extras.getStringExtra("title") ?: "", extras.getStringExtra("body") ?: "", null, 0L, false); return }
            "habit" -> {
                val a = d.optJSONArray("acts").list().find { it.optLong("id") == eid }
                if (a != null) alert(ctx, a, 1_000_000 + a.optInt("nid"), "habit", eid, a.optString("name"),
                    nlb(l, "lbHabit", notifB(l, a.optString("name"))), a.optString("time"), false)
                scheduleAll(ctx, d, leadMs = 30_000)
            }
            "todo" -> {
                val x = d.optJSONArray("todos").list().find { it.optLong("id") == eid }
                if (x != null && !x.optBoolean("done")) alert(ctx, x, 2_000_000 + x.optInt("nid"), "todo", eid, x.optString("text"),
                    nlb(l, "lbTodo", todoB(l, x.optString("text"))), x.optString("time"), true)
                scheduleAll(ctx, d, "todo", eid)
            }
            "todo_snooze" -> {
                val x = d.optJSONArray("todos").list().find { it.optLong("id") == eid }
                if (x != null && !x.optBoolean("done")) alert(ctx, x, 2_000_000 + x.optInt("nid"), "todo", eid, x.optString("text"),
                    nlb(l, "lbTodo", todoB(l, x.optString("text"))), x.optString("time"), true)
            }
            "rem" -> {
                val x = d.optJSONArray("rems").list().find { it.optLong("id") == eid }
                if (x != null && x.optBoolean("on")) show(ctx, 3_000_000 + x.optInt("nid"), "", nlb(l, "lbRem", remBody(l, x.optString("name"))), "rem", eid, false)
                scheduleAll(ctx, d, leadMs = 30_000)
            }
        }
    }

    /** A notification button was pressed. */
    @Synchronized
    fun onButton(ctx: Context, nid: Int, kind: String, eid: Long, aid: String) {
        NotificationManagerCompat.from(ctx).cancel(nid)
        val yes = aid == "yes"
        if (yes || aid == "no") {
            if (kind != "habit" && kind != "todo") return
            if (!yes && kind != "habit") return // "No" is recorded only for habits (red cross)
            val item = JSONObject().put("kind", kind).put("id", eid).put("act", if (yes) "yes" else "no")
            var date = LocalDate.now()
            if (kind == "habit") {
                val a = Store.readFile(ctx).optJSONArray("acts").list().find { it.optLong("id") == eid }
                val (h, m) = parseHm(a?.optString("time"))
                val nowMin = LocalTime.now().hour * 60 + LocalTime.now().minute
                if (nowMin < h * 60 + m) date = date.minusDays(1) // answered after midnight -> belongs to the day before
            }
            item.put("date", date.toString())
            val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val q = try { JSONArray(sp.getString(KEY_Q, "[]")) } catch (e: Exception) { JSONArray() }
            q.put(item)
            sp.edit().putString(KEY_Q, q.toString()).commit()
        } else if (aid == "snooze") {
            val isRem = Store.readFile(ctx).optJSONArray("todos").list().find { it.optLong("id") == eid }?.optString("alert") == "rem"
            setAlarm(ctx, 6_000_000 + nid, System.currentTimeMillis() + SNOOZE_MS, fireIntent(ctx, "todo_snooze", eid), isRem)
        }
    }

    /** Apply the answers given from notifications while the app was closed. */
    fun drain(ctx: Context) {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = sp.getString(KEY_Q, "[]") ?: "[]"
        if (raw == "[]") return
        sp.edit().putString(KEY_Q, "[]").commit()
        val q = try { JSONArray(raw) } catch (e: Exception) { return }
        var ch = false
        for (it in q.list()) {
            if (it.optString("kind") == "habit") {
                val a = Logic.acts().find { z -> z.optLong("id") == it.optLong("id") } ?: continue
                val date = it.optString("date")
                if (date.isEmpty()) continue
                Logic.habAns(a.optLong("id"), date, it.optString("act") != "no")
                ch = true
            } else if (it.optString("kind") == "todo") {
                val x = Logic.todos().find { z -> z.optLong("id") == it.optLong("id") }
                if (x != null && !x.optBoolean("done")) { Logic.setDone(x, true); ch = true }
            }
        }
        if (ch) Store.touch()
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        try { Notifs.onFire(c, i.getStringExtra("kind") ?: return, i.getLongExtra("eid", 0L), i) } catch (e: Exception) { }
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        try { Notifs.onButton(c, i.getIntExtra("nid", 0), i.getStringExtra("kind") ?: "", i.getLongExtra("eid", 0L), i.getStringExtra("aid") ?: "") } catch (e: Exception) { }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        try { Notifs.scheduleAll(c, Store.readFile(c)) } catch (e: Exception) { }
    }
}
