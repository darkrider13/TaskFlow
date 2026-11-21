package com.habit.tracker

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity

/** Full-screen focus clock: black, landscape, screen stays on. Tap the screen to show the close button. */
@Composable
fun FocusFullscreen(onClose: () -> Unit) {
    val activity = LocalContext.current as FragmentActivity
    val view = LocalView.current
    @Suppress("UNUSED_VARIABLE") val tickRead = Focus.tick
    var showX by remember { mutableStateOf(false) }
    val ink = Color(0xFFF4F4F5)

    DisposableEffect(Unit) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        view.keepScreenOn = true
        val c = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            view.keepScreenOn = false
            WindowInsetsControllerCompat(activity.window, activity.window.decorView).show(WindowInsetsCompat.Type.systemBars())
        }
    }
    BackHandler(onBack = onClose)

    val appCtx = activity.applicationContext
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).tap { showX = !showX }) {
        val w = maxWidth
        val h = maxHeight
        val fsDp = if (w > h) minOf(h.value * .52f, w.value * .28f) else minOf(w.value * .26f, 180f)
        val r = Focus.remaining()
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClockText(
                    Str.dig(Str.pad2(r / 60) + ":" + Str.pad2(r % 60)),
                    Modifier.padding(vertical = 10.dp).tap { Focus.toggle(); Notifs.focus(appCtx, Focus.run, Focus.end, Focus.mode == "focus") },
                    size = with(LocalDensity.current) { fsDp.dp.toSp() }, color = ink, weight = FontWeight.Bold,
                )
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).tap { Focus.reset(); Notifs.focus(appCtx, Focus.run, Focus.end, Focus.mode == "focus") },
                    contentAlignment = Alignment.Center,
                ) { Ic("reset", size = 22.dp, tint = ink) }
            }
            Tx(
                Str.f("fToday", Str.bn(Store.s.getJSONObject("focus").optInt(Logic.todayKey(), 0))),
                Modifier.padding(top = minOf(h.value * .02f, 12f).dp), size = 22.sp, color = Color(0xD9FFFFFF), weight = FontWeight.ExtraBold,
            )
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp).size(48.dp).alpha(if (showX) 1f else 0f)
                .tap { if (showX) onClose() },
            contentAlignment = Alignment.Center,
        ) { Ic("x", size = 26.dp, tint = ink) }
    }
}
