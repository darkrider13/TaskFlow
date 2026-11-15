package com.habit.tracker

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/** Focus timer state (kept in memory, like the original). */
object Focus {
    val FM = mapOf("focus" to 25, "short" to 5, "long" to 15)
    var mode by mutableStateOf("focus")
    var left by mutableIntStateOf(1500)
    var tot by mutableIntStateOf(0)
    var end by mutableLongStateOf(0L)
    var run by mutableStateOf(false)
    var n = 0
    var fs by mutableStateOf(false)
    var tick by mutableLongStateOf(0L)

    fun total(): Int = if (tot > 0) tot else (FM[mode] ?: 25) * 60

    fun remaining(): Int =
        if (run) maxOf(0, Math.ceil((end - System.currentTimeMillis()) / 1000.0).toInt()) else left

    fun toggle() {
        if (run) {
            left = maxOf(1, Math.ceil((end - System.currentTimeMillis()) / 1000.0).toInt())
            run = false
        } else {
            end = System.currentTimeMillis() + left * 1000L
            run = true
        }
    }

    fun switchMode(m: String) {
        mode = m; tot = 0; left = (FM[m] ?: 25) * 60; run = false
    }

    fun reset() {
        left = total(); run = false
    }

    /** Called when the time is up. Returns the message to show. */
    fun finish(): String {
        val wasFocus = mode == "focus"
        run = false
        if (wasFocus) {
            val f = Store.s.getJSONObject("focus")
            val k = Logic.todayKey()
            f.put(k, f.optInt(k, 0) + 1)
            n++
            Store.save()
            mode = if (n % 4 == 0) "long" else "short"
        } else mode = "focus"
        tot = 0
        left = (FM[mode] ?: 25) * 60
        Store.touch()
        return Str.t(if (wasFocus) "fDoneT" else "fBrkT")
    }
}
