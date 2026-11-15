package com.habit.tracker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app update. The GitHub workflow publishes every build as the release "latest" (title "TaskFlow (build N)").
 * "Check for update" compares N with the build number of the installed app (versionName "2.N"),
 * downloads TaskFlow.apk and opens Android's install screen. Needs internet only while checking / downloading.
 */
object Updater {
    /** Change user / repository if yours are different (the repository must be public). */
    const val API = "https://api.github.com/repos/darkrider13/TaskFlow-v2/releases/tags/latest"

    /** Plain web page of the same release; used when the API refuses (e.g. its hourly limit on shared mobile networks). */
    const val WEB = "https://github.com/darkrider13/TaskFlow-v2/releases/tag/latest"
    const val APK = "https://github.com/darkrider13/TaskFlow-v2/releases/download/latest/TaskFlow.apk"

    /** HTTP code of the last failed request, shown in the error text to find the cause. */
    var errCode by mutableIntStateOf(0)

    /** idle | checking | uptodate | avail | downloading | ready | nonet | error */
    var status by mutableStateOf("idle")
    var progress by mutableIntStateOf(0)
    var remoteBuild by mutableIntStateOf(0)
    private var apkUrl = ""

    private fun versionName(ctx: Context): String =
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" }

    fun localBuild(ctx: Context): Int = versionName(ctx).substringAfter('.', "").toIntOrNull() ?: 0

    fun versionLabel(ctx: Context): String = versionName(ctx)

    /** Ask GitHub for the newest build. Call from a background thread. */
    fun check(ctx: Context) {
        if (status == "checking" || status == "downloading") return
        status = "checking"
        errCode = 0
        try {
            val c = URL(API).openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 10000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "TaskFlow-App")
            val code = c.responseCode
            if (code != 200) { errCode = code; checkWeb(ctx); return }
            val j = JSONObject(c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            val build = Regex("build\\s*(\\d+)", RegexOption.IGNORE_CASE).find(j.optString("name"))?.groupValues?.get(1)?.toIntOrNull()
            val assets = j.optJSONArray("assets")
            var url = ""
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                if (a.optString("name").endsWith(".apk", true)) { url = a.optString("browser_download_url"); break }
            }
            if (build == null || url.isEmpty()) { checkWeb(ctx); return }
            remoteBuild = build
            apkUrl = url
            status = if (build > localBuild(ctx)) "avail" else "uptodate"
        } catch (e: IOException) {
            status = "nonet"
        } catch (e: Exception) {
            status = "error"
        }
    }

    /** Second way to find the newest build: read the release page's title ("TaskFlow (build N)"). */
    private fun checkWeb(ctx: Context) {
        try {
            val c = URL(WEB).openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 10000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "TaskFlow-App")
            val code = c.responseCode
            if (code != 200) { errCode = code; status = "error"; return }
            val html = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val title = Regex("<title>([^<]*)</title>", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1) ?: ""
            val build = Regex("build\\s*(\\d+)", RegexOption.IGNORE_CASE).find(title)?.groupValues?.get(1)?.toIntOrNull()
            if (build == null) { errCode = -1; status = "error"; return }
            remoteBuild = build
            apkUrl = APK
            status = if (build > localBuild(ctx)) "avail" else "uptodate"
        } catch (e: IOException) {
            status = "nonet"
        } catch (e: Exception) {
            errCode = -2
            status = "error"
        }
    }

    private fun apkFile(ctx: Context) = File(File(ctx.cacheDir, "update").also { it.mkdirs() }, "TaskFlow.apk")

    /** Download the APK. Call from a background thread. */
    fun download(ctx: Context) {
        if (status == "downloading" || apkUrl.isEmpty()) return
        status = "downloading"
        progress = 0
        try {
            val out = apkFile(ctx)
            val tmp = File(out.parentFile, "TaskFlow.part")
            tmp.delete(); out.delete()
            val c = URL(apkUrl).openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.setRequestProperty("User-Agent", "TaskFlow-App")
            if (c.responseCode != 200) { status = "error"; return }
            val total = c.contentLengthLong
            c.inputStream.use { inp ->
                tmp.outputStream().use { o ->
                    val buf = ByteArray(32 * 1024)
                    var done = 0L
                    while (true) {
                        val n = inp.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        done += n
                        if (total > 0) progress = (done * 100 / total).toInt().coerceIn(0, 100)
                    }
                }
            }
            // safety: it must be a real APK of this same app
            @Suppress("DEPRECATION")
            val pi = ctx.packageManager.getPackageArchiveInfo(tmp.absolutePath, 0)
            if (pi == null || pi.packageName != ctx.packageName) { tmp.delete(); status = "error"; return }
            tmp.renameTo(out)
            progress = 100
            status = "ready"
        } catch (e: IOException) {
            status = "nonet"
        } catch (e: Exception) {
            status = "error"
        }
    }

    /**
     * Open Android's install screen. Returns false when the "install unknown apps" permission is still missing:
     * the settings page for it is opened, and the user presses install again after allowing it.
     */
    fun install(ctx: Context): Boolean {
        val f = apkFile(ctx)
        if (!f.exists()) { status = "idle"; return false }
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
