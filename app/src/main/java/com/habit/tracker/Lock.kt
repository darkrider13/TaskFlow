package com.habit.tracker

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import org.json.JSONObject
import java.security.MessageDigest

class LockSpec(val mode: String, val type: String, val next: (() -> Unit)?)

/** Screen lock: PIN / pattern / fingerprint. Same rules as the original app. */
object LockState {
    var overlay by mutableStateOf<LockSpec?>(null)
    var typeDlg by mutableStateOf(false)
    var bioAv by mutableStateOf(false)
    var hiddenAt = 0L
    var lastBio = 0L

    fun lockObj(): JSONObject? = Store.s.optJSONObject("lock")
    fun lkType(): String = lockObj()?.optString("type", "pin")?.ifEmpty { "pin" } ?: "off"

    fun sha(x: String, salt: String): String {
        val b = MessageDigest.getInstance("SHA-256").digest((salt + ":" + x).toByteArray(Charsets.UTF_8))
        return b.joinToString("") { "%02x".format(it) }
    }

    fun show(mode: String, type: String? = null, next: (() -> Unit)? = null) {
        val cur = mode == "unlock" || mode == "check"
        overlay = LockSpec(mode, if (cur) lkType() else (type ?: "pin"), next)
    }

    fun checkBio(a: FragmentActivity) {
        val bm = BiometricManager.from(a)
        bioAv = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun bioAuth(a: FragmentActivity, onOk: () -> Unit, onFail: () -> Unit = {}) {
        try {
            val prompt = BiometricPrompt(a, androidx.core.content.ContextCompat.getMainExecutor(a), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { lastBio = System.currentTimeMillis(); onOk() }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { lastBio = System.currentTimeMillis(); onFail() }
            })
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(Str.t("lockBio"))
                .setNegativeButtonText(Str.t("cancelT"))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build()
            prompt.authenticate(info)
        } catch (e: Exception) {
            onFail()
        }
    }

    /** Called when the person picks a lock type from the list. */
    fun pick(a: FragmentActivity, ty: String, toast: (String) -> Unit) {
        val cur = lkType()
        if (ty == cur) return
        val apply = {
            when (ty) {
                "off" -> { Store.s.remove("lock"); Store.touch(); toast(Str.t("lockOffT")) }
                "bio" -> {
                    if (!bioAv) toast(Str.t("lockNoBio"))
                    else bioAuth(a, onOk = {
                        Store.s.put("lock", JSONObject().put("type", "bio")); Store.touch(); toast(Str.t("lockFpOn"))
                    })
                }
                else -> show("set1", ty)
            }
        }
        if (cur == "off") apply() else show("check", null) { apply() }
    }
}

@Composable
fun LockOverlay(spec: LockSpec, onToast: (String) -> Unit) {
    val p = LocalPal.current
    val activity = LocalContext.current as FragmentActivity
    var mode by remember(spec) { mutableStateOf(spec.mode) }
    val ty = spec.type
    var pin by remember(spec) { mutableStateOf("") }
    var lnew by remember(spec) { mutableStateOf("") }
    var err by remember(spec) { mutableStateOf("") }
    var busy by remember(spec) { mutableStateOf(false) }
    var seq by remember(spec) { mutableStateOf(listOf<Int>()) }
    var drag by remember(spec) { mutableStateOf<Offset?>(null) }
    var bad by remember(spec) { mutableStateOf(false) }
    val cur = mode == "unlock" || mode == "check"
    val pat = ty == "pat"
    val fp = ty == "bio"
    val lk = LockState.lockObj()
    val bb = cur && lk != null && lk.optBoolean("bio", false) && LockState.bioAv
    val vib: () -> Unit = {
        try {
            val v = activity.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            else v.vibrate(200L)
        } catch (e: Exception) {
        }
    }

    fun ok() {
        LockState.hiddenAt = 0
        val n = spec.next
        LockState.overlay = null
        n?.invoke()
    }

    fun fail(m: String) {
        err = m; pin = ""; vib()
        if (pat && seq.isNotEmpty()) bad = true
    }

    fun tryBio() {
        val l = LockState.lockObj()
        if (l != null && (l.optString("type") == "bio" || l.optBoolean("bio", false)) && LockState.bioAv) LockState.bioAuth(activity, onOk = { ok() })
    }

    fun submit(sec: String, isPat: Boolean) {
        if (busy) return
        busy = true
        try {
            if (mode == "unlock" || mode == "check") {
                val l = LockState.lockObj()
                if (l != null && LockState.sha(sec, l.optString("salt")) == l.optString("h")) ok()
                else fail(Str.t(if (isPat) "lockPBad" else "lockBad"))
            } else if (mode == "set1") {
                lnew = sec; pin = ""; mode = "set2"; seq = emptyList()
            } else if (mode == "set2") {
                if (sec == lnew) {
                    val salt = java.lang.Long.toString(Math.abs(java.util.Random().nextLong()), 36) + System.currentTimeMillis()
                    val old = LockState.lockObj()
                    Store.s.put("lock", JSONObject().put("type", ty).put("h", LockState.sha(lnew, salt)).put("salt", salt).put("bio", old?.optBoolean("bio", false) ?: false))
                    Store.touch()
                    LockState.overlay = null
                    onToast(Str.t(if (isPat) "lockPOn" else "lockOn"))
                } else { mode = "set1"; lnew = ""; seq = emptyList(); fail(Str.t(if (isPat) "lockPMis" else "lockMis")) }
            }
        } finally { busy = false }
    }

    LaunchedEffect(spec) {
        val l = LockState.lockObj()
        if (cur && l != null && (l.optString("type") == "bio" || l.optBoolean("bio", false)) && LockState.bioAv) tryBio()
    }
    LaunchedEffect(bad) { if (bad) { kotlinx.coroutines.delay(450); bad = false; seq = emptyList(); drag = null } }
    BackHandler { if (mode != "unlock") LockState.overlay = null }

    val title = if (fp) Str.t("lockFpT") else if (pat) Str.t(if (mode == "set1") "lockPNew" else if (mode == "set2") "lockPConfirm" else "lockPT")
    else Str.t(if (mode == "set1") "lockNew" else if (mode == "set2") "lockConfirm" else "lockT")

    Box(Modifier.fillMaxSize().appBg(p).statusBarsPadding().navigationBarsPadding().tap { }, contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 330.dp).fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Tx(title, size = 19.sp, weight = FontWeight.SemiBold, align = TextAlign.Center, modifier = Modifier.padding(bottom = 18.dp))
            if (!pat && !fp) {
                Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (i in 0 until 4) Box(Modifier.size(14.dp).clip(CircleShape).background(if (i < pin.length) p.ink else Color.Transparent).border(2.dp, p.ink.copy(alpha = .55f), CircleShape))
                }
            }
            Box(Modifier.height(20.dp).padding(bottom = 0.dp), contentAlignment = Alignment.Center) { Tx(err, size = 13.5.sp, color = p.cross) }
            Spacer(Modifier.height(10.dp))
            if (!pat && !fp) {
                val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", if (bb) "bio" else "", "0", "bk")
                for (r in 0 until 4) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (c in 0 until 3) {
                            val k = keys[r * 3 + c]
                            if (k.isEmpty()) Spacer(Modifier.weight(1f).height(56.dp)) else
                                Box(
                                    Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(18.dp)).background(p.field).border(1.dp, p.fieldEdge, RoundedCornerShape(18.dp)).tap {
                                        err = ""
                                        if (k == "bk") pin = pin.dropLast(1)
                                        else if (k == "bio") tryBio()
                                        else if (pin.length < 4) {
                                            pin += k
                                            if (pin.length == 4) submit(pin, false)
                                        }
                                    },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (k == "bk") Ic("back", size = 24.dp) else if (k == "bio") Ic("finger", size = 24.dp) else Tx(Str.dig(k), size = 24.sp)
                                }
                        }
                    }
                }
            }
            if (pat) {
                val col = if (bad) p.cross else p.ink
                Box(
                    Modifier.size(270.dp).pointerInput(spec) {
                        val pc = floatArrayOf(45f, 135f, 225f)
                        val sc = size.width / 270f
                        fun hit(x: Float, y: Float): Int {
                            for (i in 0 until 9) if (Math.hypot((x / sc - pc[i % 3]).toDouble(), (y / sc - pc[i / 3]).toDouble()) < 34) return i
                            return -1
                        }
                        fun add(i: Int) {
                            if (seq.contains(i)) return
                            var l = seq
                            if (l.isNotEmpty()) {
                                val a = l.last(); val ar = a / 3; val ac = a % 3; val br = i / 3; val bc = i % 3
                                if (Math.abs(ar - br) % 2 == 0 && Math.abs(ac - bc) % 2 == 0) {
                                    val m = ((ar + br) / 2) * 3 + (ac + bc) / 2
                                    if (m != a && m != i && !l.contains(m)) l = l + m
                                }
                            }
                            seq = l + i
                        }
                        detectDragGestures(
                            onDragStart = { o -> if (!busy && !bad) { seq = emptyList(); err = ""; drag = o; val i = hit(o.x, o.y); if (i >= 0) add(i) } },
                            onDrag = { change, _ -> change.consume(); if (!bad) { drag = change.position; val i = hit(change.position.x, change.position.y); if (i >= 0) add(i) } },
                            onDragEnd = {
                                drag = null
                                if (seq.isNotEmpty() && !bad) {
                                    if (seq.size < 4) fail(Str.t("lockPShort")) else submit(seq.joinToString(""), true)
                                }
                            },
                            onDragCancel = { drag = null },
                        )
                    },
                ) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        val sc = size.width / 270f
                        val pc = floatArrayOf(45f, 135f, 225f)
                        fun pt(i: Int) = Offset(pc[i % 3] * sc, pc[i / 3] * sc)
                        for (i in 0 until 9) {
                            val on = seq.contains(i)
                            drawCircle(col.copy(alpha = if (on) 1f else .55f), radius = (if (on) 12f else 10f) * sc, center = pt(i), style = Stroke(width = 2f * sc))
                            if (on) drawCircle(col.copy(alpha = .9f), radius = 5f * sc, center = pt(i))
                        }
                        for (k in 0 until seq.size - 1) drawLine(col.copy(alpha = .7f), pt(seq[k]), pt(seq[k + 1]), strokeWidth = 5f * sc, cap = StrokeCap.Round)
                        val d = drag
                        if (d != null && seq.isNotEmpty()) drawLine(col.copy(alpha = .7f), pt(seq.last()), d, strokeWidth = 5f * sc, cap = StrokeCap.Round)
                    }
                }
            }
            if (fp || (pat && bb)) {
                val shape = RoundedCornerShape(18.dp)
                Row(
                    Modifier.padding(top = 18.dp).clip(shape).background(p.field).border(1.dp, p.fieldEdge, shape).tap { tryBio() }.padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Ic("finger", size = 22.dp)
                    if (fp) Tx(Str.t("lockRetry"), size = 15.sp, weight = FontWeight.SemiBold)
                }
                if (fp && cur && !LockState.bioAv) Tx(Str.t("lockNoBio"), size = 13.sp, color = p.cross, align = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
            }
        }
        if (mode != "unlock") {
            Box(Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp).size(48.dp).tap { LockState.overlay = null }, contentAlignment = Alignment.Center) {
                Ic("x", size = 26.dp, tint = p.muted, stroke = 2.2f)
            }
        }
    }
}

/** "Screen lock type" list popup. */
@Composable
fun LockTypeDlg(onClose: () -> Unit, onToast: (String) -> Unit) {
    val p = LocalPal.current
    val activity = LocalContext.current as FragmentActivity
    val cur = LockState.lkType()
    BackHandler(onBack = onClose)
    val shape = RoundedCornerShape(30.dp)
    Box(Modifier.fillMaxSize().background(p.shade).tap { onClose() }.padding(16.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 380.dp).fillMaxWidth().clip(shape).background(p.sheet).border(1.dp, p.fieldEdge, shape).tap { }
                .padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Ic("lock", size = 22.dp); Tx(Str.t("lockTypeT"), size = 19.sp, weight = FontWeight.Bold)
            }
            Tx(Str.t("lockTypeD"), size = 13.5.sp, color = p.muted, modifier = Modifier.padding(top = 8.dp, bottom = 14.dp))
            for ((k, ic, lbl) in listOf(Triple("off", "unlock", "lockNone"), Triple("bio", "finger", "lockFp"), Triple("pin", "hash", "lockPinN"), Triple("pat", "pattern", "lockPatN"))) {
                val sel = k == cur
                val s2 = RoundedCornerShape(20.dp)
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(s2).background(p.field).border(2.dp, if (sel) p.mono else Color.Transparent, s2)
                        .tap { onClose(); LockState.pick(activity, k, onToast) }.padding(horizontal = 14.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Ic(ic, size = 22.dp)
                    Tx(Str.t(lbl), Modifier.weight(1f), size = 17.sp, weight = FontWeight.Bold)
                    if (sel) Ic("check", size = 20.dp)
                }
            }
            val s3 = RoundedCornerShape(18.dp)
            Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(50.dp).clip(s3).background(p.field).border(1.dp, p.fieldEdge, s3).tap { onClose() }, contentAlignment = Alignment.Center) {
                Tx(Str.t("cancelT"), size = 16.sp, weight = FontWeight.Bold)
            }
        }
    }
}

/** The "App Security" card on the More tab. */
@Composable
fun LockCard(onToast: (String) -> Unit) {
    val p = LocalPal.current
    val activity = LocalContext.current as FragmentActivity
    val ty = LockState.lkType()
    val nm = mapOf("off" to "lockNone", "bio" to "lockFp", "pin" to "lockPinN", "pat" to "lockPatN")[ty] ?: "lockNone"
    val icn = mapOf("off" to "unlock", "bio" to "finger", "pin" to "hash", "pat" to "pattern")[ty] ?: "unlock"
    SectionTitle(Str.t("lockH"))
    Column(Modifier.fillMaxWidth().glass().padding(14.dp)) {
        Row(Modifier.fillMaxWidth().tap { LockState.typeDlg = true }.padding(horizontal = 2.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Ic(icn, size = 22.dp)
            Column(Modifier.weight(1f)) {
                Tx(Str.t("lockTypeT"), size = 12.5.sp, color = p.muted)
                Tx(Str.t(nm), size = 15.sp, weight = FontWeight.Bold)
            }
            Ic("chevr", size = 20.dp)
        }
        if (ty == "pin" || ty == "pat") {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                RowBtn(Str.t(if (ty == "pin") "lockChg" else "lockChgP"), Modifier.weight(1f)) {
                    LockState.show("check", null) { LockState.show("set1", ty) }
                }
            }
            if (LockState.bioAv) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Tx(Str.t("lockBio"), Modifier.weight(1f), size = 13.sp)
                    val on = LockState.lockObj()?.optBoolean("bio", false) == true
                    CheckBox(on, 26) {
                        val l = LockState.lockObj()
                        if (l != null) {
                            if (!on) LockState.bioAuth(activity, onOk = { l.put("bio", true); Store.touch() })
                            else { l.put("bio", false); Store.touch() }
                        }
                    }
                }
            }
        }
    }
}
