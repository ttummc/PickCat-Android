package dao3.pickcat.feature.settings

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
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

data class AvailableUpdate(
    val tag: String,
    val versionName: String,
    val title: String,
    val notes: String,
    val releaseUrl: String,
    val apkUrl: String,
    val apkSize: Long,
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

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = UpdateRepository()
    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    fun check() {
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
            runCatching { repository.latestRelease() }
                .onSuccess { release ->
                    val current = installedVersionName()
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

    fun download() {
        val update = _state.value.update ?: return
        if (_state.value.downloading) return
        _state.update { it.copy(downloading = true, progress = 0f, installError = null) }
        viewModelScope.launch {
            val target = File(
                getApplication<Application>().cacheDir,
                "updates/pickcat-${update.versionName}.apk",
            )
            runCatching {
                repository.download(update.apkUrl, target) { progress ->
                    _state.update { it.copy(progress = progress) }
                }
                val validationError = validateApk(target)
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

    private fun installedVersionName(): String = runCatching {
        val packageInfo = getApplication<Application>().packageManager
            .getPackageInfo(getApplication<Application>().packageName, 0)
        packageInfo.versionName.orEmpty()
    }.getOrDefault("")

    private fun validateApk(file: File): String? {
        if (!file.exists() || file.length() == 0L) return "更新包为空"
        val app = getApplication<Application>()
        val packageManager = app.packageManager
        @Suppress("DEPRECATION")
        val archive = packageManager.getPackageArchiveInfo(
            file.absolutePath,
            PackageManager.GET_SIGNATURES,
        ) ?: return "无法读取更新包"
        if (archive.packageName != app.packageName) return "更新包不属于 PickCat"
        @Suppress("DEPRECATION")
        val current = packageManager.getPackageInfo(app.packageName, PackageManager.GET_SIGNATURES)
        val archiveSignatures = archive.signatures
        val currentSignatures = current.signatures
        if (archiveSignatures != null && currentSignatures != null &&
            !archiveSignatures.contentEquals(currentSignatures)
        ) {
            return "更新包签名与当前应用不一致，无法直接覆盖安装。请先在 GitHub Releases 手动安装一次正式签名版本。"
        }
        if (archive.versionCode <= current.versionCode) return "更新包版本不高于当前版本"
        return null
    }
}

private class UpdateRepository(
    private val client: OkHttpClient = OkHttpClient(),
) {
    suspend fun latestRelease(): AvailableUpdate = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/ttummc/PickCat-Android/releases/latest")
            .header("Accept", "application/vnd.github+json")
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

    suspend fun download(
        url: String,
        target: File,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()
        val request = Request.Builder()
            .url(url)
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
