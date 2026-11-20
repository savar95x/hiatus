package dev.savar.hiatus

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/** A GitHub release: version ("0.9.0"), notes (its CHANGELOG section), and the APK. */
data class Release(val version: String, val notes: String, val apkUrl: String, val apkSize: Long)

/**
 * In-app updates from GitHub Releases.
 * - Checks at most once an hour, with the last answer's ETag (a "not modified" reply is free and instant).
 * - Each APK is downloaded once into app storage, resumed if interrupted, and deleted once that version is
 *   installed. On unmetered networks it's fetched ahead, so Update installs straight away.
 * - Installs through Android's PackageInstaller. Once Hiatus has installed itself this way, later updates need
 *   no confirmation on Android 12+.
 */
object Updates {
    private const val API = "https://api.github.com/repos/savar95x/hiatus/releases/latest"
    private const val CHECK_EVERY_MS = 60 * 60 * 1000L

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val release: Release) : State
        data class Downloading(val release: Release, val progress: Float) : State
        data class Ready(val release: Release, val file: File) : State
        data class Installing(val release: Release) : State
        data class Failed(val release: Release?, val message: String) : State
    }

    var state by mutableStateOf<State>(State.Idle)
        private set

    /** For screenshot tests only. */
    internal fun showForPreview(s: State) { state = s }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private fun dir(ctx: Context) = File(ctx.noBackupFilesDir, "updates").apply { mkdirs() }
    private fun apk(ctx: Context, v: String) = File(dir(ctx), "Hiatus-$v.apk")

    fun installed(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "0.0.0"

    /** "0.10.0" > "0.9.3". */
    fun newer(a: String, b: String): Boolean {
        val x = a.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    private fun online(ctx: Context): Boolean = runCatching {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)

    private fun unmetered(ctx: Context): Boolean = runCatching {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        !cm.isActiveNetworkMetered
    }.getOrDefault(false)

    /** Deletes downloaded APKs that are no longer newer than the installed app (after an update, mostly). */
    fun cleanup(ctx: Context) {
        val current = installed(ctx)
        dir(ctx).listFiles()?.forEach { f ->
            val v = f.name.removePrefix("Hiatus-").removeSuffix(".apk").removeSuffix(".part")
            if (!newer(v, current)) f.delete()
        }
    }

    /** On app open. [force] skips the hourly limit (Settings → Check for updates). */
    fun check(ctx: Context, force: Boolean = false) {
        val app = ctx.applicationContext
        val s = state
        if (s is State.Checking || s is State.Downloading || s is State.Installing) return
        if (s is State.Ready && !force) return
        scope.launch {
            cleanup(app)
            val p = prefs(app)
            val fresh = System.currentTimeMillis() - p.getLong("checked_at", 0) < CHECK_EVERY_MS
            val cached = p.getString("json", null)
            val release = if (!force && fresh && cached != null) parse(cached) else {
                if (!online(app)) { if (force) state = State.Failed(null, "No connection"); return@launch }
                state = State.Checking
                fetch(app)
            }
            val current = installed(app)
            state = when {
                release == null -> if (force) State.Failed(null, "Couldn't reach GitHub") else State.Idle
                !newer(release.version, current) -> if (force) State.UpToDate else State.Idle
                apk(app, release.version).let { it.exists() && it.length() == release.apkSize } ->
                    State.Ready(release, apk(app, release.version))
                else -> State.Available(release)
            }
            val now = state
            if (now is State.Available && unmetered(app)) download(app, now.release)
        }
    }

    private fun fetch(ctx: Context): Release? = runCatching {
        val p = prefs(ctx)
        val c = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Hiatus/" + installed(ctx))
            p.getString("etag", null)?.let { setRequestProperty("If-None-Match", it) }
        }
        try {
            val json = when (c.responseCode) {
                304 -> p.getString("json", null)
                200 -> c.inputStream.bufferedReader().readText().also { body ->
                    p.edit().putString("json", body).putString("etag", c.getHeaderField("ETag")).apply()
                }
                else -> null
            }
            p.edit().putLong("checked_at", System.currentTimeMillis()).apply()
            json?.let { parse(it) }
        } finally { c.disconnect() }
    }.getOrNull()

    private fun parse(json: String): Release? = runCatching {
        val o = JSONObject(json)
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
        val assets = o.getJSONArray("assets")
        val a = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.getString("name").endsWith(".apk") }
            ?: return null
        Release(
            version = o.getString("tag_name").removePrefix("v"),
            notes = o.optString("body").trim(),
            apkUrl = a.getString("browser_download_url"),
            apkSize = a.getLong("size"),
        )
    }.getOrNull()

    /** Downloads (or resumes) the APK. Already complete = nothing to do. */
    fun download(ctx: Context, r: Release) {
        val app = ctx.applicationContext
        if (state is State.Downloading) return
        state = State.Downloading(r, 0f)
        scope.launch {
            val done = apk(app, r.version)
            val part = File(done.path + ".part")
            val ok = runCatching {
                if (done.exists() && done.length() == r.apkSize) return@runCatching
                if (part.length() > r.apkSize) part.delete()
                var have = part.length()
                val c = (URL(r.apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000; readTimeout = 30_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Hiatus/" + installed(app))
                    if (have > 0) setRequestProperty("Range", "bytes=$have-")
                }
                try {
                    when (c.responseCode) {
                        206 -> {}
                        200 -> have = 0 // server ignored the range: start over
                        else -> error("HTTP ${c.responseCode}")
                    }
                    RandomAccessFile(part, "rw").use { out ->
                        out.setLength(have); out.seek(have)
                        c.inputStream.use { input ->
                            val buf = ByteArray(64 * 1024)
                            var last = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                have += n
                                if (have - last > 128 * 1024) {
                                    last = have
                                    state = State.Downloading(r, (have.toFloat() / r.apkSize).coerceIn(0f, 1f))
                                }
                            }
                        }
                    }
                } finally { c.disconnect() }
                if (part.length() != r.apkSize) error("Download incomplete")
                done.delete()
                if (!part.renameTo(done)) error("Couldn't save the update")
            }
            state = ok.fold(
                onSuccess = { verify(app, r, done) },
                onFailure = { State.Failed(r, "Download paused. Try again to resume.") },
            )
        }
    }

    /** Only ever install Hiatus itself, and only a newer version. */
    private fun verify(ctx: Context, r: Release, f: File): State {
        @Suppress("DEPRECATION")
        val info = runCatching { ctx.packageManager.getPackageArchiveInfo(f.path, 0) }.getOrNull()
        if (info == null || info.packageName != ctx.packageName || !newer(info.versionName ?: "", installed(ctx))) {
            f.delete()
            return State.Failed(r, "That download wasn't a valid update")
        }
        return State.Ready(r, f)
    }

    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    /** Android asks once whether Hiatus may install apps. */
    fun allowInstalls(ctx: Context) {
        runCatching {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName)))
        }
    }

    fun install(ctx: Context) {
        val s = state as? State.Ready ?: return
        val app = ctx.applicationContext
        state = State.Installing(s.release)
        scope.launch {
            val err = runCatching {
                val pi = app.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(app.packageName)
                    setSize(s.file.length())
                    if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                val id = pi.createSession(params)
                pi.openSession(id).use { session ->
                    session.openWrite("base.apk", 0, s.file.length()).use { out ->
                        s.file.inputStream().use { it.copyTo(out) }
                        session.fsync(out)
                    }
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    val pending = PendingIntent.getBroadcast(app, id, Intent(app, InstallReceiver::class.java), flags)
                    session.commit(pending.intentSender)
                }
            }.exceptionOrNull()
            if (err != null) state = State.Failed(s.release, "Couldn't start the install")
        }
    }

    internal fun installResult(ctx: Context, ok: Boolean, message: String?) {
        val r = (state as? State.Installing)?.release
        state = if (ok) State.Idle else {
            val f = r?.let { apk(ctx, it.version) }
            if (r != null && f != null && f.exists()) State.Failed(r, message ?: "Install cancelled") else State.Failed(r, message ?: "Install failed")
        }
    }

    /** Back to "ready" after a failed or cancelled install, if the APK is still here. */
    fun retry(ctx: Context) {
        val s = state as? State.Failed ?: return
        val r = s.release ?: return check(ctx, force = true)
        val f = apk(ctx, r.version)
        if (f.exists() && f.length() == r.apkSize) state = State.Ready(r, f) else download(ctx, r)
    }
}

/** PackageInstaller's answer: show Android's confirmation if it needs one, else report the result. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null) runCatching { ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> Updates.installResult(ctx, true, null)
            PackageInstaller.STATUS_FAILURE_ABORTED -> Updates.installResult(ctx, false, "Install cancelled")
            else -> Updates.installResult(ctx, false, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
    }
}
