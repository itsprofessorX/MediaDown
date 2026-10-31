package com.professorx.mediadl

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

data class Saved(val name: String, val uri: Uri, val mime: String)

class DlViewModel(private val app: Application) : AndroidViewModel(app) {
    var url by mutableStateOf("")
    var status by mutableStateOf("Starting engine…")
    var progress by mutableStateOf(0f)
    var busy by mutableStateOf(false)
    var ready by mutableStateOf(false)
    val history = mutableStateListOf<Saved>()

    private val cookies get() = File(app.filesDir, "cookies.txt")
    val hasCookies get() = cookies.exists()
    private val processId = "dl-main"

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                ready = true
                status = "Ready. Paste a link or share one to this app."
            } catch (e: Throwable) {
                status = "Engine failed to start: ${e.message}"
            }
        }
    }

    fun importCookies(src: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            app.contentResolver.openInputStream(src)?.use { i -> cookies.outputStream().use { o -> i.copyTo(o) } }
            status = "Cookies imported (private/age-gated posts may work now)."
        } catch (e: Throwable) { status = "Cookie import failed: ${e.message}" }
    }

    fun removeCookies() { cookies.delete(); status = "Cookies removed." }

    fun updateEngine() = viewModelScope.launch(Dispatchers.IO) {
        if (!ready || busy) return@launch
        busy = true; status = "Updating yt-dlp…"
        try {
            val r = YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE)
            status = "Update finished: $r"
        } catch (e: Throwable) { status = "Update failed: ${e.message}" }
        busy = false
    }

    fun cancel() {
        YoutubeDL.getInstance().destroyProcessById(processId)
        status = "Cancelled."
    }

    fun download(audio: Boolean) {
        if (!ready || busy) return
        val links = Regex("https?://[^\\s<>]+").findAll(url).map { it.value }.distinct().take(3).toList()
        if (links.isEmpty()) { status = "Please paste a valid link."; return }
        busy = true; progress = 0f
        viewModelScope.launch(Dispatchers.IO) {
            var ok = 0
            for ((idx, link) in links.withIndex()) {
                val dir = File(app.cacheDir, "dl_${System.nanoTime()}").apply { mkdirs() }
                try {
                    status = "Downloading ${idx + 1}/${links.size}…"
                    val req = YoutubeDLRequest(link).apply {
                        addOption("-o", "${dir.absolutePath}/%(id)s_%(autonumber)s.%(ext)s")
                        addOption("--restrict-filenames")
                        addOption("--no-mtime")
                        addOption("--socket-timeout", "20")
                        addOption("--retries", "3")
                        if (audio) {
                            addOption("-f", "bestaudio/best")
                            addOption("-x")
                            addOption("--audio-format", "mp3")
                            addOption("--audio-quality", "0")
                        } else {
                            addOption("-f", "best[ext=mp4]/best")
                        }
                        if (cookies.exists()) addOption("--cookies", cookies.absolutePath)
                    }
                    YoutubeDL.getInstance().execute(req, processId) { p, _, _ -> progress = p / 100f }
                    val files = dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }.orEmpty().sortedBy { it.name }
                    if (files.isEmpty()) status = "No media found at that link."
                    files.forEach { saveToGallery(it); ok++ }
                } catch (e: Throwable) {
                    status = friendly(e)
                } finally {
                    dir.deleteRecursively()
                }
            }
            if (ok > 0) status = "✅ Saved $ok file(s) to your phone."
            progress = 0f; busy = false
        }
    }

    private fun friendly(e: Throwable): String {
        val t = (e.message ?: "").lowercase()
        return when {
            listOf("login", "cookie", "rate-limit", "private", "not available").any { it in t } ->
                "🔒 Private post or login needed. Import a cookies.txt file."
            "unsupported url" in t -> "🤔 No downloadable media at that link."
            "cancel" in t || "destroy" in t -> "Cancelled."
            else -> "❌ Download failed. Tap 'Update engine' and try again."
        }
    }

    private fun saveToGallery(f: File) {
        val ext = f.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        val (collection, folder) = when {
            mime.startsWith("video") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MOVIES
            mime.startsWith("image") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_PICTURES
            mime.startsWith("audio") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MUSIC
            else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_DOWNLOADS
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, f.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/MediaDownloader")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = app.contentResolver
        val uri = resolver.insert(collection, values) ?: return
        resolver.openOutputStream(uri)?.use { o -> f.inputStream().use { it.copyTo(o) } }
        values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        history.add(0, Saved(f.name, uri, mime))
    }
}
