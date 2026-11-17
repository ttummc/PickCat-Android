package dao3.pickcat.feature.settings

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

data class AvailableUpdate(
    val tag: String,
    val versionName: String,
    val title: String,
    val notes: String,
    val releaseUrl: String,
    val apkUrl: String,
    val apkSize: Long,
    val sha256: String? = null,
)

data class UpdateUiState(
    val checking: Boolean = false,
    val update: AvailableUpdate? = null,
    val upToDate: Boolean = false,
    val error: String? = null,
    val downloading: Boolean = false,
    val progress: Float? = null,
    val installFile: File? = null,
    val installError: String? = null,
)

class UpdateViewModel : ViewModel() {
    private val repository = UpdateRepository()
    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    fun check(context: Context, source: String = "mirror") {
        if (_state.value.checking || _state.value.downloading) return
        _state.update {
            it.copy(
                checking = true,
                update = null,
                upToDate = false,
                error = null,
                installError = null,
            )
        }
        viewModelScope.launch {
            runCatching { repository.latestRelease(source) }
                .onSuccess { release ->
                    val current = installedVersionName(context)
                    val newer = compareVersions(release.versionName, current) > 0
                    _state.update {
                        it.copy(
                            checking = false,
                            update = release.takeIf { newer },
                            upToDate = !newer,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            checking = false,
                            error = error.message ?: "检查更新失败",
                        )
                    }
                }
        }
    }

    fun download(context: Context) {
        val update = _state.value.update ?: return
        if (_state.value.downloading) return
        _state.update { it.copy(downloading = true, progress = 0f, installError = null) }
        viewModelScope.launch {
            val target = File(
                context.cacheDir,
                "updates/pickcat-${update.versionName}.apk",
            )
            runCatching {
                repository.download(update.apkUrl, target) { progress ->
                    _state.update { it.copy(progress = progress) }
                }
                if (update.apkSize > 0 && target.length() != update.apkSize) {
                    throw IOException("更新包大小校验失败")
                }
                if (!update.sha256.isNullOrBlank() &&
                    !sha256(target).equals(update.sha256, ignoreCase = true)
                ) {
                    throw IOException("更新包 SHA-256 校验失败")
                }
                val validationError = validateApk(context, target)
                if (validationError != null) throw IOException(validationError)
                target
            }.onSuccess { file ->
                _state.update {
                    it.copy(
                        downloading = false,
                        progress = 1f,
                        installFile = file,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        downloading = false,
                        progress = null,
                        installError = error.message ?: "下载更新失败",
                    )
                }
            }
        }
    }

    fun consumeInstallFile() {
        _state.update { it.copy(installFile = null) }
    }

    fun reportInstallError(message: String) {
        _state.update { it.copy(installError = message) }
    }

    fun dismiss() {
        _state.update {
            it.copy(
                update = null,
                upToDate = false,
                error = null,
                installError = null,
            )
        }
    }

    private fun installedVersionName(context: Context): String = runCatching {
        val packageInfo = context.packageManager
            .getPackageInfo(context.packageName, 0)
        packageInfo.versionName.orEmpty()
    }.getOrDefault("")

    private fun validateApk(context: Context, file: File): String? {        if (!file.exists() || file.length() == 0L) return "更新包为空"
        val packageManager = context.packageManager
        @Suppress("DEPRECATION")
        val archive = packageManager.getPackageArchiveInfo(
            file.absolutePath,
            PackageManager.GET_SIGNATURES,
        ) ?: return "无法读取更新包"
        if (archive.packageName != context.packageName) return "更新包不属于 PickCat"
        @Suppress("DEPRECATION")
        val current = packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNATURES,
        )
        val archiveSignatures = archive.signatures
        val currentSignatures = current.signatures
        if (archiveSignatures != null && currentSignatures != null &&
            !archiveSignatures.contentEquals(currentSignatures)
        ) {
            return "更新包签名与当前应用不一致，无法直接覆盖安装。请先手动安装一次官方发行版。"
        }
        if (archive.versionCode <= current.versionCode) return "更新包版本不高于当前版本"
        return null
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

private const val MIRROR_BASE = "https://pickcat-android.kitter.cn"
private const val MIRROR_LATEST_JSON = "$MIRROR_BASE/latest.json"
private const val MIRROR_VERSION = "$MIRROR_BASE/version"
private const val MIRROR_APK = "$MIRROR_BASE/latest.apk"

private class UpdateRepository(
    private val client: OkHttpClient = OkHttpClient(),
) {
    suspend fun latestRelease(source: String): AvailableUpdate = withContext(Dispatchers.IO) {
        coroutineScope {
            val githubDeferred = async { runCatching { fetchGitHub() } }
            val mirrorDeferred = async { runCatching { fetchMirror() } }
            val githubResult = githubDeferred.await()
            val mirrorResult = mirrorDeferred.await()
            val github = githubResult.getOrNull()
            val mirror = mirrorResult.getOrNull()

            val preferred = if (source == "mirror") mirror else github
            val fallback = if (source == "mirror") github else mirror
            when {
                preferred == null && fallback == null -> {
                    throw IOException(
                        "GitHub：${githubResult.exceptionOrNull()?.message.orEmpty()}\n" +
                            "Kitter镜像源：${mirrorResult.exceptionOrNull()?.message.orEmpty()}",
                    )
                }
                preferred == null -> fallback!!
                fallback == null -> preferred
                compareVersions(preferred.versionName, fallback.versionName) >= 0 -> preferred
                else -> fallback
            }
        }
    }

    private suspend fun fetchGitHub(): AvailableUpdate = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/ttummc/PickCat-Android/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("Cache-Control", "no-cache")
            .header("User-Agent", "PickCat-Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("GitHub 返回 HTTP ${response.code}")
            }
            val root = JSONObject(response.body?.string().orEmpty())
            val assets = root.optJSONArray("assets")
            var apkUrl = ""
            var apkSize = 0L
            if (assets != null) {
                for (index in 0 until assets.length()) {
                    val asset = assets.optJSONObject(index) ?: continue
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        apkSize = asset.optLong("size")
                        break
                    }
                }
            }
            if (apkUrl.isBlank()) throw IOException("最新发行版没有 APK 附件")
            val tag = root.optString("tag_name")
            AvailableUpdate(
                tag = tag,
                versionName = tag.removePrefix("v"),
                title = root.optString("name").ifBlank { tag },
                notes = root.optString("body").ifBlank { "没有提供更新说明。" },
                releaseUrl = root.optString("html_url"),
                apkUrl = apkUrl,
                apkSize = apkSize,
            )
        }
    }

    private suspend fun fetchMirror(): AvailableUpdate = withContext(Dispatchers.IO) {
        val detailed = runCatching { fetchMirrorJson() }.getOrNull()
        if (detailed != null) return@withContext detailed

        val version = fetchMirrorVersion()
        if (version.isBlank()) throw IOException("镜像没有返回版本号")
        AvailableUpdate(
            tag = version,
            versionName = version.removePrefix("v"),
            title = "PickCat Android $version",
            notes = "来自 Kitter 镜像。",
            releaseUrl = MIRROR_BASE,
            apkUrl = MIRROR_APK,
            apkSize = 0L,
        )
    }

    private fun fetchMirrorJson(): AvailableUpdate {
        val request = Request.Builder()
            .url(MIRROR_LATEST_JSON)
            .header("Cache-Control", "no-cache")
            .header("User-Agent", "PickCat-Android")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("镜像返回 HTTP ${response.code}")
            val root = JSONObject(response.body?.string().orEmpty())
            val tag = root.optString("tag")
            if (tag.isBlank()) throw IOException("镜像 JSON 没有版本号")
            val syncedAt = root.optString("synced_at")
            AvailableUpdate(
                tag = tag,
                versionName = tag.removePrefix("v"),
                title = "PickCat Android $tag",
                notes = buildString {
                    append("来自 Kitter 镜像。")
                    if (syncedAt.isNotBlank()) {
                        append("\n同步时间：")
                        append(syncedAt)
                    }
                },
                releaseUrl = MIRROR_BASE,
                apkUrl = root.optString("url").ifBlank { MIRROR_APK },
                apkSize = root.optLong("size"),
                sha256 = root.optString("sha256").takeIf { it.isNotBlank() },
            )
        }
    }

    private fun fetchMirrorVersion(): String {
        val request = Request.Builder()
            .url(MIRROR_VERSION)
            .header("Cache-Control", "no-cache")
            .header("User-Agent", "PickCat-Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("镜像返回 HTTP ${response.code}")
            return response.body?.string()?.trim().orEmpty()
        }
    }

    suspend fun download(
        url: String,
        target: File,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()
        val request = Request.Builder()
            .url(url)
            .header("Cache-Control", "no-cache")
            .header("User-Agent", "PickCat-Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("下载失败：HTTP ${response.code}")
            val body = response.body ?: throw IOException("下载内容为空")
            val total = body.contentLength()
            var written = 0L
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (target.length() == 0L) throw IOException("下载内容为空")
        }
        target
    }
}

private fun compareVersions(left: String, right: String): Int {
    fun parts(value: String): List<Int> = value
        .removePrefix("v")
        .substringBefore('-')
        .split('.')
        .map { it.toIntOrNull() ?: 0 }
    val a = parts(left)
    val b = parts(right)
    for (index in 0 until maxOf(a.size, b.size)) {
        val delta = (a.getOrElse(index) { 0 }) - (b.getOrElse(index) { 0 })
        if (delta != 0) return delta
    }
    return 0
}
