package com.professorx.mediadl

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Photo fallback: yt-dlp is video-focused, so photo posts are resolved here. */
object Images {
    private const val UA_BOT = "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uagent.php)"
    private const val UA_WEB = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

    private val direct = Regex("\\.(jpe?g|png|webp)(\\?.*)?$", RegexOption.IGNORE_CASE)
    private val tweetId = Regex("status(?:es)?/(\\d+)")
    private val og1 = Regex("<meta[^>]+(?:property|name)=[\"'](?:og:image(?::secure_url)?|twitter:image)[\"'][^>]*content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private val og2 = Regex("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]*(?:property|name)=[\"'](?:og:image(?::secure_url)?|twitter:image)[\"']", RegexOption.IGNORE_CASE)
    private val pinSize = Regex("/\\d+x(?:\\d+)?/")

    fun find(link: String): List<String> {
        val l = link.lowercase()
        if (direct.containsMatchIn(l)) return listOf(link)
        if ("twitter.com" in l || "x.com" in l) {
            val t = tweetPhotos(link)
            if (!t.isNullOrEmpty()) return t
        }
        val html = http(link, UA_BOT) ?: return emptyList()
        return (og1.findAll(html) + og2.findAll(html))
            .map { it.groupValues[1].replace("&amp;", "&") }
            .map { if ("pinimg.com" in it) it.replace(pinSize, "/originals/") else it }
            .filter { it.startsWith("http") }
            .distinct().toList()
    }

    private fun tweetPhotos(link: String): List<String>? {
        val id = tweetId.find(link)?.groupValues?.get(1) ?: return null
        val body = http("https://api.fxtwitter.com/status/$id", UA_WEB) ?: return null
        return try {
            val arr = JSONObject(body).optJSONObject("tweet")?.optJSONObject("media")?.optJSONArray("photos") ?: return null
            List(arr.length()) { arr.getJSONObject(it).optString("url") }.filter { it.startsWith("http") }
        } catch (_: Exception) { null }
    }

    private fun http(u: String, ua: String): String? = try {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 20000
        c.setRequestProperty("User-Agent", ua)
        c.setRequestProperty("Accept-Language", "en")
        if (c.responseCode !in 200..299) null else c.inputStream.bufferedReader().use { it.readText() }
    } catch (_: Exception) { null }

    fun download(src: String, dir: File, i: Int, onProgress: (Float) -> Unit): File? {
        var f = fetch(src, dir, i, onProgress)
        if (f == null && "/originals/" in src) f = fetch(src.replace("/originals/", "/736x/"), dir, i, onProgress)
        return f
    }

    private fun fetch(src: String, dir: File, i: Int, onProgress: (Float) -> Unit): File? {
        return try {
            val c = URL(src).openConnection() as HttpURLConnection
            c.connectTimeout = 15000; c.readTimeout = 25000
            c.setRequestProperty("User-Agent", UA_WEB)
            if (c.responseCode !in 200..299) return null
            val type = c.contentType.orEmpty().lowercase()
            if (!type.startsWith("image")) return null
            val ext = when {
                "png" in type -> "png"
                "webp" in type -> "webp"
                "gif" in type -> "gif"
                else -> "jpg"
            }
            val out = File(dir, "img_${i + 1}.$ext")
            val total = c.contentLengthLong
            var read = 0L
            c.inputStream.use { ins ->
                out.outputStream().use { o ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n); read += n
                        if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (out.length() > 0) out else null
        } catch (_: Exception) { null }
    }
}
