package com.habit.tracker

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * All app data lives in one JSON object that has exactly the same shape as the old web app's
 * "hb" data (acts, marks, todos, nid, set, nos, diary, focus, rems, cats, lock ...), so the
 * old backup files can be imported later without any conversion.
 */
object Store {
    private var file: File? = null
    var s: JSONObject = JSONObject()

    /** Changes every time data changes; screens take it as a parameter so they redraw. */
    var rev by mutableIntStateOf(0)
        private set

    fun init(ctx: Context) {
        val f = File(ctx.filesDir, "hb.json")
        file = f
        s = try {
            if (f.exists()) JSONObject(f.readText()) else JSONObject()
        } catch (e: Exception) {
            JSONObject()
        }
        normalize()
    }

    /** Read the saved data without touching the running app state (used by alarms in the background). */
    fun readFile(ctx: Context): JSONObject {
        return try {
            val f = File(ctx.filesDir, "hb.json")
            if (f.exists()) JSONObject(f.readText()) else JSONObject()
        } catch (e: Exception) {
            JSONObject()
        }
    }

    fun normalize() {
        if (s.optJSONArray("acts") == null) s.put("acts", JSONArray())
        if (s.optJSONObject("marks") == null) s.put("marks", JSONObject())
        if (s.optJSONArray("todos") == null) s.put("todos", JSONArray())
        if (!s.has("nid")) s.put("nid", 1)
        val set = s.optJSONObject("set") ?: JSONObject().also { s.put("set", it) }
        if (!set.has("theme")) set.put("theme", "dark")
        if (!set.has("lang")) set.put("lang", "bn")
        if (!set.has("name")) set.put("name", "")
        if (s.optJSONObject("nos") == null) s.put("nos", JSONObject())
        if (s.optJSONObject("diary") == null) s.put("diary", JSONObject())
        if (s.optJSONObject("focus") == null) s.put("focus", JSONObject())
        if (s.optJSONArray("rems") == null) s.put("rems", JSONArray())
    }

    private val io = java.util.concurrent.Executors.newSingleThreadExecutor()

    fun save() {
        val f = file ?: return
        val text = try { s.toString() } catch (e: Exception) { return }
        io.execute {
            try {
                val tmp = File(f.path + ".tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(f)) {
                    f.writeText(text)
                    tmp.delete()
                }
            } catch (e: Exception) {
            }
        }
    }

    /** Redraw the screens without saving anything (e.g. a new holiday list arrived). */
    fun refresh() { rev++ }

    /** Call after any change: saves to disk and redraws the screens. */
    fun touch() {
        save()
        rev++
    }

    val set: JSONObject get() = s.getJSONObject("set")
    val dark: Boolean get() = set.optString("theme", "dark") != "light"
    val name: String get() = set.optString("name", "")
}

fun JSONArray?.list(): List<JSONObject> {
    if (this == null) return emptyList()
    val r = ArrayList<JSONObject>(length())
    for (i in 0 until length()) optJSONObject(i)?.let { r.add(it) }
    return r
}
