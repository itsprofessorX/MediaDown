package com.professorx.mediadl

import org.json.JSONObject
import java.io.File
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Photo fallback: yt-dlp is video-focused, so photo posts are resolved here. */
object Images {
    private const val UA_BOT = "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uagent.php)"
    private const val UA_WEB = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
    private const val IG_APP_ID = "936619743392459"
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    private val direct = Regex("\\.(jpe?g|png|webp)(\\?.*)?$", RegexOption.IGNORE_CASE)
    private val tweetId = Regex("status(?:es)?/(\\d+)")
    private val og1 = Regex("<meta[^>]+(?:property|name)=[\"'](?:og:image(?::secure_url)?|twitter:image)[\"'][^>]*content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private val og2 = Regex("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]*(?:property|name)=[\"'](?:og:image(?::secure_url)?|twitter:image)[\"']", RegexOption.IGNORE_CASE)
    private val pinSize = Regex("/\\d+x(?:\\d+)?/")
    private val igCode = Regex("instagram\\.com/(?:[^/?#]+/)?(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)")
    private val igDisplay = Regex("display_url\\\\*\"\\s*:\\s*\\\\*\"(https:[^\"]+?)\\\\*\"")
    private val igImg = Regex("<img[^>]+class=\"[^\"]*EmbeddedMediaImage[^\"]*\"[^>]*src=\"([^\"]+)\"", RegexOption.IGNORE_CASE)

    fun find(link: String, cookieFile: File? = null): List<String> {
        val l = link.lowercase()
        if (direct.containsMatchIn(l)) return listOf(link)
        if ("twitter.com" in l || "x.com" in l) {
            val t = tweetPhotos(link)
            if (!t.isNullOrEmpty()) return t
        }
        if ("instagram.com" in l || "instagr.am" in l) {
            val ig = instagram(link, cookieFile)
            if (ig.isNotEmpty()) return ig
        }
        val html = http(link, UA_BOT) ?: return emptyList()
        return (og1.findAll(html) + og2.findAll(html))
            .map { it.groupValues[1].replace("&amp;", "&") }
            .map { if ("pinimg.com" in it) it.replace(pinSize, "/originals/") else it }
            .filter { it.startsWith("http") }
            .distinct().toList()
    }

    // ------------------------------------------------------------ Instagram
    /** Tries several methods and keeps the one that finds the most full-size photos. */
    private fun instagram(link: String, cookieFile: File?): List<String> {
        val code = igCode.find(link)?.groupValues?.get(1) ?: return emptyList()
        val cookie = cookieHeader(cookieFile)
        if (cookie != null) {
            val a = igApi(code, cookie)
            if (a.isNotEmpty()) return a
        }
        val options = listOf(igGraphql(code, cookie), igEmbed(code))
        return options.maxByOrNull { it.size } ?: emptyList()
    }

    /** Logged-in API: every carousel photo at original size and shape. */
    private fun igApi(code: String, cookie: String): List<String> {
        val id = shortcodeToId(code) ?: return emptyList()
        val body = http(
            "https://www.instagram.com/api/v1/media/$id/info/", UA_WEB,
            mapOf("X-IG-App-ID" to IG_APP_ID, "Cookie" to cookie, "X-Requested-With" to "XMLHttpRequest")
        ) ?: return emptyList()
        return try {
            val item = JSONObject(body).optJSONArray("items")?.optJSONObject(0) ?: return emptyList()
            val out = mutableListOf<String>()
            val carousel = item.optJSONArray("carousel_media")
            if (carousel != null) {
                for (i in 0 until carousel.length()) imageOf(carousel.getJSONObject(i))?.let { out.add(it) }
            } else {
                imageOf(item)?.let { out.add(it) }
            }
            out
        } catch (_: Exception) { emptyList() }
    }

    private fun imageOf(m: JSONObject): String? {
        if (m.optInt("media_type") == 2) return null // video, handled by yt-dlp
        return m.optJSONObject("image_versions2")?.optJSONArray("candidates")?.optJSONObject(0)
            ?.optString("url")?.takeIf { it.startsWith("http") }
    }

    /** Public GraphQL query (no login). May be rate-limited or changed by Instagram. */
    private fun igGraphql(code: String, cookie: String?): List<String> {
        val vars = URLEncoder.encode(
            "{\"shortcode\":\"$code\",\"fetch_tagged_user_count\":null,\"hoisted_comment_id\":null,\"hoisted_reply_id\":null}", "UTF-8"
        )
        val headers = mutableMapOf(
            "X-IG-App-ID" to IG_APP_ID, "X-Requested-With" to "XMLHttpRequest", "Referer" to "https://www.instagram.com/"
        )
        if (cookie != null) headers["Cookie"] = cookie
        val body = http("https://www.instagram.com/graphql/query/?doc_id=8845758582119845&variables=$vars", UA_WEB, headers)
            ?: return emptyList()
        return try {
            val m = JSONObject(body).optJSONObject("data")?.optJSONObject("xdt_shortcode_media") ?: return emptyList()
            val out = mutableListOf<String>()
            val edges = m.optJSONObject("edge_sidecar_to_children")?.optJSONArray("edges")
            if (edges != null) {
                for (i in 0 until edges.length()) {
                    val n = edges.getJSONObject(i).optJSONObject("node") ?: continue
                    if (n.optBoolean("is_video")) continue
                    val u = n.optString("display_url")
                    if (u.startsWith("http")) out.add(u)
                }
            } else if (!m.optBoolean("is_video")) {
                val u = m.optString("display_url")
                if (u.startsWith("http")) out.add(u)
            }
            out
        } catch (_: Exception) { emptyList() }
    }

    /** Public embed page: full-size photos, sometimes the whole carousel. */
    private fun igEmbed(code: String): List<String> {
        val html = http("https://www.instagram.com/p/$code/embed/captioned/", UA_WEB) ?: return emptyList()
        val fromJson = igDisplay.findAll(html)
            .map { unescape(it.groupValues[1]) }
            .filter { it.startsWith("http") }
            .distinct().toList()
        if (fromJson.isNotEmpty()) return fromJson
        return igImg.findAll(html)
            .map { it.groupValues[1].replace("&amp;", "&") }
            .distinct().toList()
    }

    private fun unescape(s: String) = s
        .replace("\\\\u0026", "&").replace("\\u0026", "&")
        .replace("\\\\/", "/").replace("\\/", "/")
        .replace("&amp;", "&").replace("\\", "")

    private fun shortcodeToId(code: String): String? {
        var n = BigInteger.ZERO
        val b = BigInteger.valueOf(64)
        for (ch in code.take(11)) {
            val i = ALPHABET.indexOf(ch)
            if (i < 0) return null
            n = n.multiply(b).add(BigInteger.valueOf(i.toLong()))
        }
        return n.toString()
    }

    /** Builds a Cookie header from the imported Netscape cookies.txt (instagram.com only). */
    private fun cookieHeader(f: File?): String? {
        if (f == null || !f.exists()) return null
        val pairs = f.readLines().mapNotNull { raw ->
            val line = raw.removePrefix("#HttpOnly_")
            if (line.isBlank() || line.startsWith("#")) return@mapNotNull null
            val p = line.split('\t')
            if (p.size >= 7 && "instagram.com" in p[0]) "${p[5]}=${p[6]}" else null
        }
        return pairs.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    // ------------------------------------------------------------ X / Twitter
    private fun tweetPhotos(link: String): List<String>? {
        val id = tweetId.find(link)?.groupValues?.get(1) ?: return null
        val body = http("https://api.fxtwitter.com/status/$id", UA_WEB) ?: return null
        return try {
            val arr = JSONObject(body).optJSONObject("tweet")?.optJSONObject("media")?.optJSONArray("photos") ?: return null
            List(arr.length()) { arr.getJSONObject(it).optString("url") }.filter { it.startsWith("http") }
        } catch (_: Exception) { null }
    }

    // ------------------------------------------------------------ http helpers
    private fun http(u: String, ua: String, headers: Map<String, String> = emptyMap()): String? = try {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 20000
        c.setRequestProperty("User-Agent", ua)
        c.setRequestProperty("Accept-Language", "en")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
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
