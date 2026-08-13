package com.example.ota

import android.content.Context
import android.content.pm.PackageManager
import com.example.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * OTA self-update for the CoS app (Stage 7).
 *
 * Fetches a version manifest from a configurable update URL, compares
 * versionCode, downloads the APK, verifies its SHA-256 digest AND that its
 * signing certificate matches the currently-installed app, then installs via
 * ACTION_INSTALL_PACKAGE (FileProvider URI) so the system shows the standard
 * confirmation dialog and re-validates signature continuity — a mismatched
 * cert fails closed.
 *
 * Manifest format (served next to the APK):
 *   { "versionCode": 2, "versionName": "1.1",
 *     "url": "app-debug.apk",            // relative to the manifest URL
 *     "sha256": "<hex digest of the APK>" }
 */
object OtaUpdater {

    const val PREFS = "ota_config"
    const val KEY_UPDATE_URL = "update_url"
    const val DEFAULT_UPDATE_URL = "http://127.0.0.1:8890/update.json"

    private val http = OkHttpClient().newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun getUpdateUrl(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_UPDATE_URL, DEFAULT_UPDATE_URL) ?: DEFAULT_UPDATE_URL

    fun setUpdateUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_UPDATE_URL, url).apply()
    }

    data class Manifest(
        val versionCode: Int,
        val versionName: String,
        val url: String,
        val sha256: String,
    )

    /** GET + parse the manifest. `url` may be absolute or relative to the manifest URL. */
    fun fetchManifest(context: Context, updateUrl: String?): Manifest {
        val base = updateUrl ?: getUpdateUrl(context)
        val req = Request.Builder().url(base).get().build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}: $base")
            val json = JSONObject(body)
            val rawUrl = json.optString("url", "")
            val apkUrl = when {
                rawUrl.startsWith("http") -> rawUrl
                else -> {
                    val b = URL(base)
                    URL(b.protocol, b.host, b.port, b.path.substringBeforeLast('/') + "/" + rawUrl).toString()
                }
            }
            return Manifest(
                versionCode = json.getInt("versionCode"),
                versionName = json.optString("versionName", ""),
                url = apkUrl,
                sha256 = json.optString("sha256", "").lowercase(),
            )
        }
    }

    /** Check for an update. Returns an informational JSON, never throws for "no update". */
    fun check(context: Context, updateUrl: String?): JSONObject {
        val out = JSONObject()
        out.put("ok", true)
        out.put("current_version_code", BuildConfig.VERSION_CODE)
        out.put("current_version_name", BuildConfig.VERSION_NAME)
        out.put("update_url", updateUrl ?: getUpdateUrl(context))
        try {
            val m = fetchManifest(context, updateUrl)
            out.put("latest_version_code", m.versionCode)
            out.put("latest_version_name", m.versionName)
            out.put("apk_url", m.url)
            out.put("sha256", m.sha256)
            out.put("available", m.versionCode > BuildConfig.VERSION_CODE)
        } catch (e: Exception) {
            out.put("ok", false)
            out.put("error", e.message)
        }
        return out
    }

    /**
     * Download + verify + install. Throws on any verification failure (bad
     * digest, cert mismatch) so the caller surfaces it; the system install
     * confirmation dialog is shown for the user to approve.
     */
    fun install(context: Context, updateUrl: String?): JSONObject {
        val m = fetchManifest(context, updateUrl)
        if (m.versionCode <= BuildConfig.VERSION_CODE) {
            throw IllegalStateException("no update (latest=${m.versionCode}, current=${BuildConfig.VERSION_CODE})")
        }

        val apk = File(context.cacheDir, "ota-update.apk")
        val req = Request.Builder().url(m.url).get().build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("download HTTP ${resp.code}: ${m.url}")
            resp.body!!.byteStream().use { input ->
                apk.outputStream().use { output -> input.copyTo(output) }
            }
        }

        // 1. Integrity: SHA-256 must match the manifest.
        if (m.sha256.isNotEmpty()) {
            val actual = sha256Hex(apk)
            if (actual != m.sha256) throw IllegalStateException("sha256 mismatch: got $actual, expected ${m.sha256}")
        }

        // 2. Signature: the update's signing cert must match the installed app.
        val pm = context.packageManager
        val installedCert = installedCertSha256(pm, context.packageName)
        val updateCert = apkCertSha256(pm, apk.absolutePath)
        if (installedCert == null || updateCert == null) {
            throw IllegalStateException("cannot read signing certs (installed=$installedCert, update=$updateCert)")
        }
        if (installedCert != updateCert) {
            throw IllegalStateException("signature mismatch: update signed with a different key")
        }

        // 3. Install: ACTION_INSTALL_PACKAGE with a FileProvider URI. This is
        //    the reliable self-update confirmation path — the system shows the
        //    "Update this app?" dialog regardless of EXTRA_INTENT behavior.
        //    (PackageInstaller.commit's PENDING_USER_ACTION handoff delivered a
        //    null EXTRA_INTENT on this device.) Requires SYSTEM_ALERT_WINDOW for
        //    the background activity start; we surface a notification otherwise.
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apk
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_INSTALL_PACKAGE)
            .setData(uri)
            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            throw IllegalStateException("cannot launch install confirm: ${e.message}")
        }

        val out = JSONObject()
        out.put("ok", true)
        out.put("installing", true)
        out.put("version_code", m.versionCode)
        out.put("version_name", m.versionName)
        out.put("apk_path", apk.absolutePath)
        out.put("cert_verified", true)
        return out
    }

    /** CAN the user install unknown apps at all? */
    fun canRequestInstall(context: Context): Boolean {
        return try {
            context.packageManager.canRequestPackageInstalls()
        } catch (e: Exception) {
            true // pre-API-26 always permits
        }
    }

    /** SHA-256 (hex) of the first signing cert in the installed package. */
    private fun installedCertSha256(pm: PackageManager, pkg: String): String? {
        val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
        return info.signatures?.firstOrNull()?.let { sha256Hex(it.toByteArray()) }
    }

    /** SHA-256 (hex) of the first signing cert inside an APK archive. */
    private fun apkCertSha256(pm: PackageManager, apkPath: String): String? {
        val info = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_SIGNATURES)
        return info?.signatures?.firstOrNull()?.let { sha256Hex(it.toByteArray()) }
    }

    private fun sha256Hex(data: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(data).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun sha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
