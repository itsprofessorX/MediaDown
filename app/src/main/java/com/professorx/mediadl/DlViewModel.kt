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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

data class Saved(val name: String, val uri: Uri, val mime: String)

class DlViewModel(private val app: Application) : AndroidViewModel(app) {
    var url by mutableStateOf("")
    var status by mutableStateOf("Starting…")
    var engine by mutableStateOf("")
    var progress by mutableStateOf(0f)
    var indeterminate by mutableStateOf(false)
    var stage by mutableStateOf("")
    var busy by mutableStateOf(false)
    var ready by mutableStateOf(false)
    var hasCookies by mutableStateOf(false)
    val history = mutableStateListOf<Saved>()
    private val prefs = app.getSharedPreferences("mediadown", 0)
    var autoShare by mutableStateOf(prefs.getBoolean("auto", true))
    private var pendingShare = false

    private val cookies get() = File(app.filesDir, "cookies.txt")
    private val processId = "dl-main"
    private val engineLock = Mutex()
    @Volatile private var lastUpdate = 0L
    @Volatile private var cancelled = false
    private var lastError = ""

    init {
        loadHistory()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                hasCookies = cookies.exists()
                ready = true
                status = "Ready"
                if (pendingShare) { pendingShare = false; download(false) }
                updateEngine() // auto-refresh yt-dlp every time the app opens
            } catch (e: Throwable) {
                status = "Engine failed to start: ${e.message}"
            }
        }
    }

    private suspend fun updateEngine(): Boolean = engineLock.withLock {
        engine = "Updating engine…"
        try {
            YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE)
            lastUpdate = System.currentTimeMillis()
            engine = "Engine up to date"
            true
        } catch (e: Throwable) {
            engine = "Engine update skipped"
            false
        }
    }

    /** Link arrived from another app's Share menu. */
    fun onShared(text: String) {
        url = text
        if (autoShare) { if (ready) download(false) else pendingShare = true }
    }

    fun toggleAuto() {
        autoShare = !autoShare
        prefs.edit().putBoolean("auto", autoShare).apply()
    }

    private fun loadHistory() {
        try {
            val arr = JSONArray(prefs.getString("history", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                history.add(Saved(o.getString("n"), Uri.parse(o.getString("u")), o.getString("m")))
            }
        } catch (_: Exception) {}
    }

    private fun saveHistory() {
        val arr = JSONArray()
        history.take(60).forEach { arr.put(JSONObject().put("n", it.name).put("u", it.uri.toString()).put("m", it.mime)) }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    fun forceUpdate() { viewModelScope.launch(Dispatchers.IO) { if (ready && !busy) updateEngine() } }

    fun importCookies(src: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                app.contentResolver.openInputStream(src)?.use { i -> cookies.outputStream().use { o -> i.copyTo(o) } }
                hasCookies = true
                status = "Cookies imported."
            } catch (e: Throwable) { status = "Cookie import failed: ${e.message}" }
        }
    }

    fun removeCookies() { cookies.delete(); hasCookies = false; status = "Cookies removed." }

    fun cancel() {
        cancelled = true
        YoutubeDL.getInstance().destroyProcessById(processId)
    }

    fun download(audio: Boolean) {
        if (!ready || busy) return
        val links = Regex("https?://[^\\s<>]+").findAll(url).map { it.value }.distinct().take(3).toList()
        if (links.isEmpty()) { status = "Please paste a valid link."; return }
        busy = true; cancelled = false; progress = 0f; indeterminate = true; stage = ""
        viewModelScope.launch(Dispatchers.IO) {
            var ok = 0
            for ((idx, link) in links.withIndex()) {
                if (cancelled) break
                status = if (links.size > 1) "Downloading ${idx + 1} of ${links.size}…" else "Downloading…"
                progress = 0f; indeterminate = true; stage = ""
                ok += fetchOne(link, audio)
            }
            status = when {
                cancelled -> "Cancelled."
                ok > 0 -> "✅ Saved $ok file(s) to your phone."
                else -> lastError.ifBlank { "❌ Download failed." }
            }
            progress = 0f; indeterminate = false; stage = ""; busy = false
        }
    }

    private suspend fun fetchOne(link: String, audio: Boolean): Int {
        for (attempt in 0..1) {
            val dir = File(app.cacheDir, "dl_${System.nanoTime()}").apply { mkdirs() }
            try {
                val n = runYtDlp(link, audio, dir)
                if (n > 0) { dir.deleteRecursively(); return n }
                lastError = "🤔 No media found at that link."
            } catch (e: Throwable) {
                if (cancelled) { dir.deleteRecursively(); return 0 }
                val partial = saveAll(mediaFiles(dir))
                if (partial > 0) { dir.deleteRecursively(); return partial }
                lastError = friendly(e)
            }
            // yt-dlp can't do photo posts, so try the photo fallback
            if (!audio && !cancelled) {
                try {
                    val n = runImages(link, dir)
                    if (n > 0) { dir.deleteRecursively(); return n }
                } catch (_: Throwable) {}
            }
            dir.deleteRecursively()
            if (attempt == 0 && !cancelled && System.currentTimeMillis() - lastUpdate > 10 * 60_000L) {
                updateEngine() // stale engine is the usual cause, refresh and retry once
            } else break
        }
        return 0
    }

    private suspend fun runYtDlp(link: String, audio: Boolean, dir: File): Int = engineLock.withLock {
        val req = YoutubeDLRequest(link).apply {
            addOption("-o", "${dir.absolutePath}/%(id)s_%(autonumber)s.%(ext)s")
            addOption("--restrict-filenames")
            addOption("--no-mtime")
            addOption("--newline")
            addOption("-i")
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
        var lastRaw = 0f
        var fileNo = 1
        YoutubeDL.getInstance().execute(req, processId) { p, _, line ->
            val l = line.orEmpty()
            if ("[ExtractAudio]" in l || "[Merger]" in l || "[VideoConvertor]" in l || "[FixupM3u8]" in l) {
                indeterminate = true
                if (stage != "Converting…") stage = "Converting…"
            } else if (p >= 0f) {
                if (p + 30f < lastRaw) fileNo++
                lastRaw = p
                val v = (p / 100f).coerceIn(0f, 1f)
                if (indeterminate) indeterminate = false
                val s = if (fileNo > 1) "File $fileNo" else ""
                if (stage != s) stage = s
                if (v >= 0.999f || abs(v - progress) >= 0.01f) progress = v
            } else {
                indeterminate = true
            }
        }
        saveAll(mediaFiles(dir))
    }

    private fun runImages(link: String, dir: File): Int {
        val urls = Images.find(link, if (cookies.exists()) cookies else null)
        if (urls.isEmpty()) return 0
        indeterminate = false; progress = 0f; stage = if (urls.size > 1) "${urls.size} photos" else ""
        var n = 0
        urls.forEachIndexed { i, u ->
            val f = Images.download(u, dir, i) { frac ->
                val v = (i + frac) / urls.size
                if (abs(v - progress) >= 0.01f) progress = v
            }
            if (f != null) { saveToGallery(f); n++ }
        }
        progress = 1f
        return n
    }

    private fun mediaFiles(dir: File): List<File> =
        dir.listFiles()?.filter {
            it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") && !it.name.endsWith(".json")
        }.orEmpty().sortedBy { it.name }

    private fun saveAll(files: List<File>): Int {
        var n = 0
        files.forEach { try { saveToGallery(it); n++ } catch (_: Throwable) {} }
        return n
    }

    private fun friendly(e: Throwable): String {
        val t = (e.message ?: "").lowercase()
        return when {
            listOf("login", "cookie", "rate-limit", "private", "not available", "sign in").any { it in t } ->
                "🔒 Private post or login needed. Import cookies in Tools."
            "unsupported url" in t -> "🤔 No downloadable media at that link."
            else -> "❌ Couldn't download that. Check the link and try again."
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
        saveHistory()
    }
}
