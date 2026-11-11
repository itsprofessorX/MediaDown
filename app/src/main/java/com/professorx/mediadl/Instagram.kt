package com.professorx.mediadl

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Logged-in Instagram resolver: posts, reels, carousels (photos + videos), stories, highlights.
 * Endpoint flow adapted from HOAIAN2/Instagram-Downloader (MIT License).
 */
object Instagram {
    private const val APP_ID = "936619743392459"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private val postRe = Regex("instagram\\.com/(?:[^/?#]+/)?(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)")
    private val storyRe = Regex("instagram\\.com/stories/([^/?#]+)(?:/(\\d+))?")

    fun isInstagram(link: String): Boolean {
        val l = link.lowercase()
        return "instagram.com" in l || "instagr.am" in l
    }

    /** Returns direct media URLs (photos and videos) or an empty list. */
    fun resolve(link: String, cookieFile: File): List<String> {
        val cookie = Images.cookieHeader(cookieFile) ?: return emptyList()
        val csrf = Regex("csrftoken=([^;]+)").find(cookie)?.groupValues?.get(1)
        val h = mutableMapOf(
            "x-ig-app-id" to APP_ID,
            "x-requested-with" to "XMLHttpRequest",
            "x-ig-www-claim" to "0",
            "Cookie" to cookie,
            "Referer" to "https://www.instagram.com/"
        )
        if (csrf != null) h["x-csrftoken"] = csrf

        val story = storyRe.find(link)
        if (story != null) {
            val who = story.groupValues[1]
            val sid = story.groupValues[2]
            return if (who == "highlights") reel("highlight:$sid", sid, h) else stories(who, sid, h)
        }
        val code = postRe.find(link)?.groupValues?.get(1) ?: return emptyList()
        return post(code, h)
    }

    // ------------------------------------------------------------ posts / reels
    private fun post(code: String, h: Map<String, String>): List<String> {
        var item = mediaInfo(shortcodeToId(code), h)
        if (item == null) item = mediaInfo(graphqlId(code, h), h) // private-profile fallback, same as the extension
        val it = item ?: return emptyList()
        val carousel = it.optJSONArray("carousel_media")
        val out = mutableListOf<String>()
        if (carousel != null) {
            for (i in 0 until carousel.length()) mediaUrl(carousel.getJSONObject(i))?.let { u -> out.add(u) }
        } else {
            mediaUrl(it)?.let { u -> out.add(u) }
        }
        return out
    }

    private fun mediaInfo(id: String?, h: Map<String, String>): JSONObject? {
        if (id == null) return null
        val body = get("https://www.instagram.com/api/v1/media/$id/info/", h) ?: return null
        return try { JSONObject(body).optJSONArray("items")?.optJSONObject(0) } catch (_: Exception) { null }
    }

    private fun graphqlId(code: String, h: Map<String, String>): String? {
        val form = "fb_api_caller_class=RelayModern" +
            "&fb_api_req_friendly_name=PolarisPostActionLoadPostQueryQuery" +
            "&doc_id=8845758582119845" +
            "&variables=" + URLEncoder.encode("{\"shortcode\":\"$code\"}", "UTF-8")
        val hh = h.toMutableMap()
        hh["content-type"] = "application/x-www-form-urlencoded"
        hh["x-fb-friendly-name"] = "PolarisPostActionLoadPostQueryQuery"
        val body = request("https://www.instagram.com/graphql/query/", hh, form) ?: return null
        return try { JSONObject(body).optJSONObject("data")?.optJSONObject("xdt_shortcode_media")?.optString("id")?.ifBlank { null } }
        catch (_: Exception) { null }
    }

    // ------------------------------------------------------------ stories / highlights
    private fun stories(username: String, storyId: String, h: Map<String, String>): List<String> {
        val body = get("https://www.instagram.com/api/v1/users/web_profile_info/?username=" + URLEncoder.encode(username, "UTF-8"), h)
            ?: return emptyList()
        val uid = try { JSONObject(body).optJSONObject("data")?.optJSONObject("user")?.optString("id") } catch (_: Exception) { null }
        if (uid.isNullOrBlank()) return emptyList()
        return reel(uid, storyId, h)
    }

    private fun reel(reelId: String, onlyId: String, h: Map<String, String>): List<String> {
        val body = get("https://www.instagram.com/api/v1/feed/reels_media/?reel_ids=" + URLEncoder.encode(reelId, "UTF-8"), h)
            ?: return emptyList()
        return try {
            val items = JSONObject(body).optJSONObject("reels")?.optJSONObject(reelId)?.optJSONArray("items")
                ?: return emptyList()
            val all = (0 until items.length()).map { items.getJSONObject(it) }
            // a story link with an id means "just this story"; otherwise take the whole reel
            val chosen = if (onlyId.isNotBlank() && !reelId.startsWith("highlight:")) {
                all.filter { it.optString("pk") == onlyId || it.optString("id").startsWith("${onlyId}_") }.ifEmpty { all }
            } else all
            chosen.mapNotNull { mediaUrl(it) }
        } catch (_: Exception) { emptyList() }
    }

    // ------------------------------------------------------------ helpers
    /** media_type 1 = photo, otherwise video (same rule as the extension). */
    private fun mediaUrl(m: JSONObject): String? {
        val u = if (m.optInt("media_type") == 1) {
            m.optJSONObject("image_versions2")?.optJSONArray("candidates")?.optJSONObject(0)?.optString("url")
        } else {
            m.optJSONArray("video_versions")?.optJSONObject(0)?.optString("url")
        }
        return u?.takeIf { it.startsWith("http") }
    }

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

    private fun get(u: String, h: Map<String, String>): String? = request(u, h, null)

    private fun request(u: String, h: Map<String, String>, form: String?): String? = try {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 25000
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        h.forEach { (k, v) -> c.setRequestProperty(k, v) }
        if (form != null) {
            c.requestMethod = "POST"; c.doOutput = true
            c.outputStream.use { it.write(form.toByteArray()) }
        }
        if (c.responseCode !in 200..299) null else c.inputStream.bufferedReader().use { it.readText() }
    } catch (_: Exception) { null }
}
