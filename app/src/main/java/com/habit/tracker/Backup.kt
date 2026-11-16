package com.habit.tracker

import android.content.Context
import android.net.Uri
import org.json.JSONObject

/** Save / restore all data as one JSON file (same format as the old web app, so old backups work). */
object Backup {
    private const val MAXB = 10 * 1024 * 1024

    fun dump(): String {
        val o = JSONObject()
        o.put("app", "taskflow")
        o.put("v", 1)
        o.put("at", java.time.Instant.now().toString())
        val d = JSONObject(Store.s.toString())
        d.remove("lock")
        o.put("data", d)
        return o.toString()
    }

    fun fileName(): String = "taskflow-backup-" + Logic.todayKey() + ".json"

    fun export(ctx: Context, uri: Uri) {
        try {
            ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(dump().toByteArray(Charsets.UTF_8)) }
            val na = Store.s.optJSONArray("acts")?.length() ?: 0
            val nt = Store.s.optJSONArray("todos")?.length() ?: 0
            UiState.dlg = DlgSpec(Str.t("exDoneT"), Str.f("exDoneD", na, nt), Str.t("bkOkB"))
        } catch (e: Exception) {
            UiState.dlg = DlgSpec(Str.t("bkBadT"), e.message ?: "", Str.t("bkOkB"), kind = "err")
        }
    }

    /** Write the backup to a cache file and open the system share sheet (Google Drive, WhatsApp, Gmail ...). */
    fun share(ctx: Context) {
        try {
            val dir = java.io.File(ctx.cacheDir, "backup").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val f = java.io.File(dir, fileName())
            f.writeText(dump(), Charsets.UTF_8)
            val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, f.name)
                clipData = android.content.ClipData.newRawUri(f.name, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(android.content.Intent.createChooser(send, Str.t("expShareB")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            UiState.dlg = DlgSpec(Str.t("bkBadT"), e.message ?: "", Str.t("bkOkB"), kind = "err")
        }
    }

    fun readFile(ctx: Context, uri: Uri) {
        try {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
            if (bytes.size > MAXB) {
                UiState.dlg = DlgSpec(Str.t("bkBadT"), Str.t("imBig"), Str.t("bkOkB"), kind = "err")
                return
            }
            doImport(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            UiState.dlg = DlgSpec(Str.t("bkBadT"), Str.t("bkBad"), Str.t("bkOkB"), kind = "err")
        }
    }

    private fun bad() {
        UiState.dlg = DlgSpec(Str.t("bkBadT"), Str.t("bkBad"), Str.t("bkOkB"), kind = "err")
    }

    fun doImport(txt: String) {
        val o = try { JSONObject(txt) } catch (e: Exception) { bad(); return }
        val d = o.optJSONObject("data") ?: o
        if (d.optJSONArray("acts") == null || d.optJSONArray("todos") == null || d.optJSONObject("marks") == null) { bad(); return }
        UiState.dlg = DlgSpec(Str.t("bkQT"), Str.t("bkQ"), Str.t("bkImB"), Str.t("cancelT"), kind = "ask", icon = "import") {
            val oldLock = Store.s.optJSONObject("lock")
            Store.s = d
            if (oldLock != null) d.put("lock", oldLock) else d.remove("lock")
            Store.normalize()
            var nid = d.optInt("nid", 1)
            for (z in d.optJSONArray("acts").list() + d.optJSONArray("todos").list()) nid = maxOf(nid, z.optInt("nid", 0) + 1)
            d.put("nid", nid)
            UiState.sel = Logic.acts().firstOrNull()?.optLong("id")
            Store.touch()
            UiState.dlg = DlgSpec(
                Str.t("bkDoneT"),
                Str.f("bkDoneD", d.optJSONArray("acts")?.length() ?: 0, d.optJSONArray("todos")?.length() ?: 0),
                Str.t("bkOkB"),
            )
        }
    }
}
