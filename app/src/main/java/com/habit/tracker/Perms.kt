package com.habit.tracker

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * First-launch permission set-up. Steps run one after another:
 * 0 notifications (pop-up) · 1 exact alarms · 2 full-screen reminder screen.
 * Battery optimisation is NOT touched: the app never runs in the background, the phone wakes it only at alarm time.
 * A step that is already allowed (or not needed on this Android version) is skipped.
 */
object Perms {
    const val LAST = 2
    private const val PREFS = "taskflow_native"
    private const val KEY = "perm_done"

    fun done(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
    fun markDone(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply()

    fun needNotif(ctx: Context): Boolean = Build.VERSION.SDK_INT >= 33 &&
        androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) !=
        android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Settings screen to open for step 1..3, or null when that step is already fine. */
    fun intent(ctx: Context, step: Int): Intent? {
        val pkg = Uri.parse("package:" + ctx.packageName)
        return when (step) {
            1 -> if (Build.VERSION.SDK_INT >= 31 &&
                !(ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
            ) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg) else null
            2 -> if (Build.VERSION.SDK_INT >= 34 &&
                !(ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).canUseFullScreenIntent()
            ) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg) else null
            else -> null
        }
    }
}
