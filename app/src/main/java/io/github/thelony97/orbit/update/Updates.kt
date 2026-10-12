package io.github.thelony97.orbit.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import io.github.thelony97.orbit.BuildConfig
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.now
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val size: Long,
    val notes: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data object Installing : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** Checks GitHub Releases for a newer APK and installs it with the system package installer. */
object Updates {

    private const val TAG = "Updates"
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkIfDue() {
        val s = OrbitRepository.snapshot().settings
        if (!s.autoUpdateCheck) return
        if (now() - s.lastUpdateCheck < TimeUnit.HOURS.toMillis(12) && _state.value !is UpdateState.Idle) return
        check(quiet = true)
    }

    suspend fun check(quiet: Boolean = false) {
        if (_state.value is UpdateState.Downloading || _state.value is UpdateState.Installing) return
        if (!quiet) _state.value = UpdateState.Checking
        val result = withContext(Dispatchers.IO) { runCatching { fetchLatest() } }
        OrbitRepository.update { d -> d.copy(settings = d.settings.copy(lastUpdateCheck = now())) }
        result.onSuccess { info ->
            _state.value = if (info != null && info.versionCode > BuildConfig.VERSION_CODE) {
                UpdateState.Available(info)
            } else {
                UpdateState.UpToDate
            }
        }.onFailure {
            Log.w(TAG, "update check failed", it)
            _state.value = if (quiet) UpdateState.Idle else UpdateState.Failed("Keine Verbindung zu GitHub")
        }
    }

    private fun fetchLatest(): UpdateInfo? {
        val url = URL("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Orbit-App")
            connectTimeout = 10_000
            readTimeout = 15_000
        }
        try {
            if (conn.responseCode == 404) return null
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val obj = json.parseToJsonElement(body) as JsonObject
            val tag = obj["tag_name"]?.jsonPrimitive?.content ?: return null
            val code = tag.substringAfterLast('.').toIntOrNull() ?: return null
            val assets = obj["assets"] as? JsonArray ?: return null
            val apk = assets.mapNotNull { it as? JsonObject }
                .firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true } ?: return null
            return UpdateInfo(
                versionCode = code,
                versionName = tag.removePrefix("v"),
                apkUrl = apk["browser_download_url"]?.jsonPrimitive?.content ?: return null,
                size = apk["size"]?.jsonPrimitive?.longOrNull ?: 0L,
                notes = obj["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        } finally {
            conn.disconnect()
        }
    }

    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))

    suspend fun downloadAndInstall(ctx: Context, info: UpdateInfo) {
        try {
            _state.value = UpdateState.Downloading(info, 0f)
            val file = withContext(Dispatchers.IO) { download(ctx, info) }
            _state.value = UpdateState.Installing
            withContext(Dispatchers.IO) { install(ctx, file) }
        } catch (e: Exception) {
            Log.w(TAG, "update failed", e)
            _state.value = UpdateState.Failed(e.message ?: "Download fehlgeschlagen")
        }
    }

    private fun open(start: URL): HttpURLConnection {
        var url = start
        repeat(6) {
            val conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Orbit-App")
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location") ?: error("Weiterleitung ohne Ziel")
                conn.disconnect()
                url = URL(url, location)
            } else {
                if (code !in 200..299) {
                    conn.disconnect()
                    error("Download: HTTP $code")
                }
                return conn
            }
        }
        error("Zu viele Weiterleitungen")
    }

    private fun download(ctx: Context, info: UpdateInfo): File {
        val target = File(ctx.cacheDir, "update.apk")
        val conn = open(URL(info.apkUrl))
        try {
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.size
            conn.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        if (total > 0) _state.value = UpdateState.Downloading(info, (done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        return target
    }

    private fun install(ctx: Context, apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("orbit.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(ctx, InstallResultReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                ctx, sessionId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pending.intentSender)
        }
    }

    internal fun onInstallResult(ctx: Context, status: Int, message: String?) {
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> _state.value = UpdateState.Idle
            PackageInstaller.STATUS_PENDING_USER_ACTION -> Unit
            else -> {
                _state.value = UpdateState.Failed(message ?: "Installation abgebrochen")
                Toast.makeText(ctx, "Update nicht installiert: ${message ?: status}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun reset() {
        _state.value = UpdateState.Idle
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(confirm)
                } catch (e: Exception) {
                    Updates.onInstallResult(context, PackageInstaller.STATUS_FAILURE, e.message)
                }
            }
        }
        Updates.onInstallResult(context, status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}
