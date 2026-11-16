package com.habit.tracker

import android.content.Intent
import android.graphics.Color as AColor
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

class RemData(
    val nid: Int, val kind: String, val eid: Long,
    val title: String, val body: String, val time: String, val snooze: Boolean,
)

/**
 * The "alarm" screen of a Reminder: opens over the lock screen, rings (the notification keeps ringing),
 * shows the time + name. Swipe the screen sideways to turn it off.
 */
class ReminderActivity : ComponentActivity() {
    private var data by mutableStateOf<RemData?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.ensureInit(applicationContext)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AColor.TRANSPARENT
        window.navigationBarColor = AColor.TRANSPARENT
        read(intent)
        setContent {
            val d = data
            if (d != null) ReminderScreen(d, onOff = { off(d) }, onAct = { aid -> act(d, aid) })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        read(intent)
    }

    private fun read(i: Intent?) {
        if (i == null) { finish(); return }
        data = RemData(
            i.getIntExtra("nid", 0), i.getStringExtra("kind") ?: "", i.getLongExtra("eid", 0L),
            i.getStringExtra("title") ?: "", i.getStringExtra("body") ?: "", i.getStringExtra("time") ?: "",
            i.getBooleanExtra("snooze", false),
        )
    }

    /** Turn the reminder off (stops the ringing). */
    private fun off(d: RemData) {
        NotificationManagerCompat.from(this).cancel(d.nid)
        finish()
    }

    /** Yes / No / Remind later: exactly the same as the buttons of a notification. */
    private fun act(d: RemData, aid: String) {
        try { Notifs.onButton(applicationContext, d.nid, d.kind, d.eid, aid) } catch (e: Exception) { }
        NotificationManagerCompat.from(this).cancel(d.nid)
        finish()
    }
}

@Composable
private fun ReminderScreen(d: RemData, onOff: () -> Unit, onAct: (String) -> Unit) {
    val pal = if (Store.dark) DarkPal else LightPal
    val scope = rememberCoroutineScope()
    val dx = remember { Animatable(0f) }
    var w by remember { mutableIntStateOf(1) }

    CompositionLocalProvider(LocalPal provides pal) {
        BackHandler { onOff() }
        // same background as the app: night photo in dark mode, soft glow in day mode
        Box(Modifier.fillMaxSize().appBg(pal).onSizeChanged { w = it.width.coerceAtLeast(1) }) {
            if (pal.dark) ThemeBackdrop()
            Column(
                Modifier
                    .fillMaxSize()
                    .offset { IntOffset(dx.value.roundToInt(), 0) }
                    .alpha(1f - (abs(dx.value) / w).coerceIn(0f, 1f) * 0.85f)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    if (abs(dx.value) > w * 0.3f) {
                                        dx.animateTo(if (dx.value > 0) w.toFloat() else -w.toFloat(), tween(160))
                                        onOff()
                                    } else dx.animateTo(0f, tween(200))
                                }
                            },
                            onDragCancel = { scope.launch { dx.animateTo(0f, tween(200)) } },
                            onHorizontalDrag = { c, amt -> c.consume(); scope.launch { dx.snapTo(dx.value + amt) } },
                        )
                    }
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(36.dp))
                Box(Modifier.size(84.dp).clip(CircleShape).background(pal.glass2).border(1.dp, pal.edge, CircleShape), contentAlignment = Alignment.Center) {
                    Ic("alarm", size = 40.dp, tint = pal.ink)
                }
                Spacer(Modifier.height(14.dp))
                Tx(Str.t("lbRem"), size = 15.sp, color = pal.muted, weight = FontWeight.SemiBold)

                Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if (d.time.isNotEmpty()) {
                        Tx(Str.t12(d.time), size = 52.sp, weight = FontWeight.Bold, align = TextAlign.Center)
                        Spacer(Modifier.height(18.dp))
                    }
                    Tx(d.title, size = 28.sp, weight = FontWeight.Bold, align = TextAlign.Center, maxLines = 4)
                    Spacer(Modifier.height(10.dp))
                    Tx(d.body, size = 15.sp, color = pal.muted, align = TextAlign.Center, maxLines = 4)
                }

                // answer buttons (same as the notification)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val shape = RoundedCornerShape(16.dp)
                    Box(
                        Modifier.weight(1f).height(54.dp).clip(shape).background(pal.ac).tap { onAct("yes") },
                        contentAlignment = Alignment.Center,
                    ) { Tx(Str.t(if (d.kind == "habit") "yesA" else "doneA"), size = 16.sp, color = pal.tink, weight = FontWeight.Bold, maxLines = 1) }
                    if (d.kind == "habit" || d.snooze) {
                        Box(
                            Modifier.weight(1f).height(54.dp).clip(shape).background(pal.glass2).border(1.dp, pal.edge, shape)
                                .tap { onAct(if (d.kind == "habit") "no" else "snooze") },
                            contentAlignment = Alignment.Center,
                        ) { Tx(Str.t(if (d.kind == "habit") "noA" else "snooze"), size = 16.sp, weight = FontWeight.Bold, maxLines = 1) }
                    }
                }

                Spacer(Modifier.height(26.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Ic("chevl", size = 18.dp, tint = pal.muted)
                    Tx(Str.t("slideOff"), size = 14.sp, color = pal.muted, weight = FontWeight.Medium)
                    Ic("chevr", size = 18.dp, tint = pal.muted)
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}
