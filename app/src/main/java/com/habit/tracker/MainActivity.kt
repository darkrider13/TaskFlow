package com.habit.tracker

import android.graphics.Color as AColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        HolRemote.init(applicationContext)
        LockState.checkBio(this)
        if (Store.s.optJSONObject("lock") != null) LockState.show("unlock")
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { Root() }
    }

    override fun onResume() {
        super.onResume()
        Notifs.drain(this)
    }

    override fun onStop() {
        super.onStop()
        if (LockState.hiddenAt == 0L) LockState.hiddenAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        val h = LockState.hiddenAt
        if (Store.s.optJSONObject("lock") != null && h != 0L && System.currentTimeMillis() - h > 5000 &&
            System.currentTimeMillis() - LockState.lastBio > 2000 && LockState.overlay == null
        ) LockState.show("unlock")
        else if (LockState.overlay == null) LockState.hiddenAt = 0L
    }
}

private val TABS = listOf("home", "cal", "up", "rep", "more")

@Composable
fun Root() {
    val rev = Store.rev
    val pal = if (Store.dark) DarkPal else LightPal
    val activity = LocalContext.current as ComponentActivity

    SideEffect {
        val w = activity.window
        w.statusBarColor = AColor.TRANSPARENT
        w.navigationBarColor = AColor.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) w.isNavigationBarContrastEnforced = false
        val c = WindowInsetsControllerCompat(w, w.decorView)
        c.isAppearanceLightStatusBars = !pal.dark
        c.isAppearanceLightNavigationBars = !pal.dark
    }

    var tab by rememberSaveable { mutableStateOf("home") }
    var showSettings by remember { mutableStateOf(false) }
    var showName by remember { mutableStateOf(false) }
    var toastMsg by remember { mutableStateOf<String?>(null) }
    var todayKey by remember { mutableStateOf(Logic.todayKey()) }

    // if the app stays open past midnight, move "today" forward
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            val k = Logic.todayKey()
            if (k != todayKey) todayKey = k
        }
    }
    LaunchedEffect(toastMsg) {
        if (toastMsg != null) {
            delay(3500)
            toastMsg = null
        }
    }
    val toast: (String) -> Unit = { toastMsg = it }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) Backup.export(activity, uri) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) Backup.readFile(activity, uri) }

    // first launch: ask for every permission the app needs, one after another
    var permStep by rememberSaveable { mutableIntStateOf(if (Perms.done(activity)) Perms.LAST + 1 else 0) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permStep++ }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { permStep++ }
    LaunchedEffect(permStep) {
        if (permStep > Perms.LAST) { Perms.markDone(activity); return@LaunchedEffect }
        if (permStep == 0) {
            if (Perms.needNotif(activity)) permLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) else permStep++
        } else {
            val i = Perms.intent(activity, permStep)
            if (i == null) permStep++
            else try { settingsLauncher.launch(i) } catch (e: Exception) { permStep++ }
        }
    }
    // keep the phone alarms in step with the data (a short pause lets quick edits settle)
    LaunchedEffect(rev) {
        delay(600)
        val text = Store.s.toString()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { Notifs.scheduleAll(activity.applicationContext, org.json.JSONObject(text)) }
    }

    // check the online holiday list now, then again every 6 hours while the app stays open
    LaunchedEffect(Unit) {
        while (true) {
            val changed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { HolRemote.fetch() }
            if (changed) Store.refresh()
            delay(6 * 60 * 60 * 1000L)
        }
    }

    // work out the holiday tables in the background so the Calendar tab opens instantly
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val y0 = java.time.LocalDate.now().year
            for (yy in listOf(y0, y0 + 1, y0 - 1)) {
                try { Hol.holYear(yy) } catch (e: Exception) { }
            }
        }
    }

    // focus timer keeps running on every screen
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            if (Focus.run) {
                if (System.currentTimeMillis() >= Focus.end) {
                    toastMsg = Focus.finish()
                    try {
                        @Suppress("DEPRECATION")
                        val vib = activity.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
                        if (Build.VERSION.SDK_INT >= 26) vib.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
                        else vib.vibrate(longArrayOf(0, 300, 150, 300), -1)
                    } catch (e: Exception) {
                    }
                }
                Focus.tick = System.currentTimeMillis()
            }
        }
    }

    // blur the screen behind while a popup / sheet is open
    val blurOn = UiState.dayKey != null || UiState.edit != null || UiState.showCats || UiState.editCat != null || UiState.remSheet ||
        showSettings || showName || LockState.typeDlg || UiState.dlg != null || UiState.pickers > 0 || UiState.delHabit != null

    CompositionLocalProvider(LocalPal provides pal) {
        Box(Modifier.fillMaxSize().appBg(pal)) {
            if (pal.dark) ThemeBackdrop()
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            Box(Modifier.fillMaxSize().then(if (blurOn) Modifier.blur(14.dp) else Modifier)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(start = 14.dp, end = 14.dp, top = 12.dp)) {
                TopBar(rev, onUser = { showName = true }, onGear = { showSettings = true })
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        "home" -> HomeScreen(
                            rev, todayKey, 134.dp + navBottom,
                            onGoUp = { tab = "up" },
                            onToast = toast,
                            onOpenHabit = { UiState.backDay = null; UiState.edit = FormSpec("habit", it, null) },
                            onEditTask = { UiState.backDay = null; UiState.edit = FormSpec("task", null, it) },
                        )
                        "up" -> UpcomingScreen(
                            rev, todayKey, 134.dp + navBottom,
                            onEditTask = { UiState.backDay = null; UiState.edit = FormSpec("task", null, it) },
                            onEditHabit = { UiState.backDay = null; UiState.edit = FormSpec("habit", it, null) },
                        )
                        "rep" -> ReportsScreen(rev, todayKey, 134.dp + navBottom)
                        "cal" -> key(UiState.calReset) { CalendarScreen(rev, todayKey, 134.dp + navBottom, onOpenDay = { UiState.dayKey = it }) }
                        "more" -> MoreScreen(rev, todayKey, 134.dp + navBottom, toast)
                        else -> ComingSoon(rev)
                    }
                }
            }
            NavBar(rev, tab) {
                // leaving the Calendar, or tapping its button again, brings it back to the default page
                if (tab == "cal" || it == "cal") UiState.resetCalendar()
                tab = it
            }
            }

            // popups that sit under a picker / second popup are blurred too
            Box(Modifier.fillMaxSize().then(if (UiState.pickers > 0 || UiState.dlg != null || UiState.exportChoose) Modifier.blur(14.dp) else Modifier)) {
            UiState.dayKey?.let { DaySheet(rev, it, onClose = { UiState.dayKey = null }, onToast = toast) }
            UiState.edit?.let { FormSheet(rev, it, toast) }
            if (UiState.showCats) CatSheet(rev) { UiState.showCats = false }
            UiState.editCat?.let { CatEditSheet(it, rev) { UiState.editCat = null } }
            if (UiState.remSheet) ReminderSheet(rev, UiState.remObj, onClose = { UiState.remSheet = false }, onToast = toast)
            if (showSettings) SettingsSheet(
                rev, onClose = { showSettings = false }, onToast = toast,
                onExport = { UiState.exportChoose = true },
                onImport = { showSettings = false; importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
            )
            if (showName) NameSheet(rev, onClose = { showName = false })
            }
            if (Store.name.isBlank()) Onboarding(rev)
            if (Focus.fs) FocusFullscreen { Focus.fs = false }
            if (LockState.typeDlg) LockTypeDlg(onClose = { LockState.typeDlg = false }, onToast = toast)
            if (UiState.exportChoose) ExportDlg(
                onSave = { UiState.exportChoose = false; exportLauncher.launch(Backup.fileName()) },
                onShare = { UiState.exportChoose = false; Backup.share(activity) },
                onClose = { UiState.exportChoose = false },
            )
            UiState.dlg?.let { AppDlg(it) }
            UiState.delHabit?.let { a ->
                ConfirmDlg(Str.t("delT"), Str.f("confirmDel", a.optString("name")), Str.t("bkOkB"), Str.t("cancelT"),
                    onOk = { UiState.delHabit = null; UiState.removingHabit = a }, onCancel = { UiState.delHabit = null })
            }
            LockState.overlay?.let { LockOverlay(it, toast) }

            toastMsg?.let { msg ->
                Box(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 10.dp)) {
                    Box(
                        Modifier.widthIn(max = 340.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (pal.dark) Color(0xF2161618) else Color(0xF2F4F9FF))
                            .border(1.dp, pal.edge, RoundedCornerShape(16.dp))
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    ) { Tx(msg, size = 14.sp, align = TextAlign.Center) }
                }
            }
        }
    }
}

@Composable
fun TopBar(rev: Int, onUser: () -> Unit, onGear: () -> Unit) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).tap { onUser() }, verticalAlignment = Alignment.CenterVertically) {
            if (Store.name.isNotEmpty()) {
                Ic("user", size = 23.dp)
                Spacer(Modifier.width(8.dp))
                Tx(Store.name, size = 26.sp, weight = FontWeight.Bold, maxLines = 1)
            } else {
                Tx(Str.t("userPh"), size = 15.sp, color = p.muted, weight = FontWeight.Medium)
            }
        }
        Box(Modifier.height(38.dp).widthIn(min = 42.dp).clip(RoundedCornerShape(19.dp)).tap { onGear() }, contentAlignment = Alignment.Center) {
            Ic("gear", size = 20.dp)
        }
    }
}

@Composable
fun NavBar(rev: Int, tab: String, onTab: (String) -> Unit) {
    val p = LocalPal.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(if (p.dark) Color(0xFF0B0F1A) else Color(0xFFE9F1F9)) // solid base so content never shows through
                .glass(22.dp)
                .padding(4.dp),
        ) {
            for (k in TABS) {
                val on = tab == k
                val label = Str.t(mapOf("home" to "tHome", "up" to "tUp", "cal" to "tCal", "rep" to "tRep", "more" to "tMore")[k]!!)
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (on) p.ac else Color.Transparent)
                        .tap { onTab(k) }
                        .padding(top = 5.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Ic("nav_$k", size = 19.dp, tint = if (on) p.tink else p.muted)
                    Tx(label, size = 10.5.sp, color = if (on) p.tink else p.muted, weight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun ComingSoon(rev: Int) {
    val p = LocalPal.current
    Box(Modifier.fillMaxSize().padding(bottom = 134.dp), contentAlignment = Alignment.Center) {
        Tx(
            if (Str.isBn) "এই স্ক্রিনটি পরের ধাপে যোগ হবে" else "This screen will be added in the next step",
            size = 14.sp, color = p.muted, align = TextAlign.Center,
        )
    }
}

@Composable
fun NameSheet(rev: Int, onClose: () -> Unit) {
    var v by remember { mutableStateOf(Store.name) }
    val save = {
        Store.set.put("name", v.trim())
        Store.touch()
        onClose()
    }
    SheetFrame(Str.t("nameTitle"), onClose) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TfInput(v, { v = it }, Str.t("userPh2"), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            RowBtn(Str.t("start"), Modifier.weight(1f)) { save() }
        }
    }
}

/** Dark-mode photo background shared by every screen. The dark veil keeps the picture soft and text easy to read. */
@Composable
fun ThemeBackdrop() {
    Image(
        painter = androidx.compose.ui.res.painterResource(R.drawable.bg_night),
        contentDescription = null,
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
    val veil = listOf(Color(0xA6000000), Color(0x99000000), Color(0xA6000000))
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(veil)))
}

/** "Check for update" block of the Settings popup. */
@Composable
fun UpdateBox(onToast: (String) -> Unit) {
    val p = LocalPal.current
    val ctx = LocalContext.current.applicationContext
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val bn = Str.isBn
    val st = Updater.status
    val io = kotlinx.coroutines.Dispatchers.IO
    val info = when (st) {
        "checking" -> if (bn) "চেক করা হচ্ছে…" else "Checking…"
        "uptodate" -> if (bn) "আপনি সর্বশেষ ভার্সনে আছেন।" else "You have the latest version."
        "avail" -> if (bn) "নতুন আপডেট পাওয়া গেছে (বিল্ড ${Updater.remoteBuild})।" else "A new update is available (build ${Updater.remoteBuild})."
        "downloading" -> if (bn) "ডাউনলোড হচ্ছে… ${Updater.progress}%" else "Downloading… ${Updater.progress}%"
        "ready" -> if (bn) "ডাউনলোড শেষ। ইনস্টল করুন।" else "Download finished. Tap install."
        "nonet" -> if (bn) "ইন্টারনেট নেই। নেট চালু করে আবার চেষ্টা করুন।" else "No internet. Turn it on and try again."
        "error" -> (if (bn) "আপডেট চেক করা যায়নি। একটু পরে আবার চেষ্টা করুন।" else "Could not check for updates. Try again later.") + (if (Updater.errCode != 0) " (${Updater.errCode})" else "")
        else -> ""
    }
    Tx((if (bn) "বর্তমান ভার্সন: " else "Current version: ") + Updater.versionLabel(ctx), Modifier.fillMaxWidth(), size = 13.sp, color = p.muted, align = TextAlign.Center)
    if (info.isNotEmpty()) Tx(info, size = 13.sp, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
    if (st == "downloading") {
        val frac by androidx.compose.animation.core.animateFloatAsState(Updater.progress / 100f, label = "dl")
        Box(
            Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(p.glass2)
        ) {
            Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(p.ac))
        }
    }
    Spacer(Modifier.height(8.dp))
    when (st) {
        "checking", "downloading" -> {}
        "avail" -> RowBtn(if (bn) "ডাউনলোড ও ইনস্টল" else "Download & install", Modifier.fillMaxWidth(), icon = "import") {
            scope.launch(io) {
                Updater.download(ctx)
                if (Updater.status == "ready") kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (!Updater.install(ctx)) onToast(if (bn) "ইনস্টলের অনুমতি দিয়ে ফিরে এসে আবার ইনস্টল চাপুন" else "Allow installs, come back and tap install again")
                }
            }
        }
        "ready" -> RowBtn(if (bn) "ইনস্টল করুন" else "Install", Modifier.fillMaxWidth(), icon = "import") {
            if (!Updater.install(ctx)) onToast(if (bn) "ইনস্টলের অনুমতি দিয়ে ফিরে এসে আবার ইনস্টল চাপুন" else "Allow installs, come back and tap install again")
        }
        else -> RowBtn(if (bn) "আপডেট চেক করুন" else "Check for update", Modifier.fillMaxWidth(), icon = "refresh") {
            scope.launch(io) { Updater.check(ctx) }
        }
    }
}

@Composable
fun SettingsSheet(rev: Int, onClose: () -> Unit, onToast: (String) -> Unit, onExport: () -> Unit, onImport: () -> Unit) {
    val p = LocalPal.current
    var v by remember { mutableStateOf(Store.name) }
    val later = if (Str.isBn) "এই অংশ পরের ধাপে আসছে" else "This part comes in a later step"
    SheetFrame(Str.t("setT"), onClose) {
        SectionTitle(Str.t("yourName"), first = true)
        Row(Modifier.fillMaxWidth()) { TfInput(v, { v = it }, Str.t("userPh2"), Modifier.weight(1f)) }
        SectionTitle(Str.t("appear"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RowBtn(
                Str.t(if (Store.dark) "thD" else "thL"), Modifier.weight(1f),
                icon = if (Store.dark) "moon" else "sun",
            ) {
                Store.set.put("theme", if (Store.dark) "light" else "dark")
                Store.touch()
            }
            RowBtn(if (Str.isBn) "English" else "বাংলা", Modifier.weight(1f)) {
                Str.setLang(if (Str.isBn) "en" else "bn")
            }
        }
        SectionTitle(Str.t("dataT"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RowBtn(Str.t("expT"), Modifier.weight(1f), icon = "export") { onExport() }
            RowBtn(Str.t("impT"), Modifier.weight(1f), icon = "import") { onImport() }
        }
        Spacer(Modifier.height(18.dp))
        UpdateBox(onToast)
        Spacer(Modifier.height(22.dp))
        BigBtn(Str.t("save")) {
            Store.set.put("name", v.trim())
            Store.touch()
            onClose()
        }
    }
}

@Composable
fun Onboarding(rev: Int) {
    val p = LocalPal.current
    var v by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    val go = {
        if (v.trim().isEmpty()) err = true
        else {
            Store.set.put("name", v.trim())
            Store.touch()
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .appBg(p)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 22.dp, end = 22.dp, top = 56.dp, bottom = 22.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Tx(Str.t("obHi"), size = 34.sp, color = p.muted, align = TextAlign.Center)
            Tx("TaskFlow", size = 44.sp, weight = FontWeight.ExtraBold, align = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
            Tx(Str.t("obSub"), size = 17.sp, color = p.muted, weight = FontWeight.Medium, align = TextAlign.Center)
        }
        Column(
            Modifier
                .padding(top = 56.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(if (p.dark) Color(0xFF1C1C1E) else Color(0xBFFFFFFF))
                .padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 28.dp),
        ) {
            Tx(Str.t("obProf"), size = 26.sp, weight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp, bottom = 20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(p.glass2), contentAlignment = Alignment.Center) {
                    Ic("user", size = 26.dp, tint = p.muted)
                }
                Spacer(Modifier.width(16.dp))
                BasicTextField(
                    value = v,
                    onValueChange = { if (it.length <= 40) { v = normDigits(it); err = false } },
                    visualTransformation = BnDigits,
                    singleLine = true,
                    textStyle = TextStyle(color = p.ink, fontSize = 18.sp),
                    cursorBrush = SolidColor(p.ink),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Column {
                            Box(Modifier.padding(vertical = 10.dp)) {
                                if (v.isEmpty()) Tx(Str.t("nameTitle"), size = 18.sp, color = p.muted)
                                inner()
                            }
                            Box(Modifier.fillMaxWidth().height(1.dp).background(if (err) Color(0xFFFF0000) else p.mono))
                        }
                    },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .padding(top = 28.dp)
                .fillMaxWidth()
                .height(66.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .tap { go() },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ic("checkc", size = 24.dp, tint = Color.Black, stroke = 2.4f)
            Spacer(Modifier.width(12.dp))
            Tx(Str.t("start"), size = 20.sp, color = Color.Black, weight = FontWeight.Bold)
        }
    }
}
