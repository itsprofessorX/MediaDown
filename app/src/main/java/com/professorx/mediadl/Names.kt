package com.professorx.mediadl

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MediaItem(val url: String, val name: String)

object Names {
    private val bad = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
    private val spaces = Regex("\\s+")

    /** Keeps the real title (any language/emoji), removes only characters Android can't store. */
    fun clean(s: String, max: Int = 90): String {
        var t = s.replace(bad, " ").replace(spaces, " ").trim().trim('.', ' ')
        if (t.length > max) t = t.take(max).trim()
        if (t.isNotEmpty() && t.last().isHighSurrogate()) t = t.dropLast(1)
        return t.ifBlank { "media" }
    }

    fun fromLink(link: String): String {
        val host = try { java.net.URI(link).host ?: "" } catch (_: Exception) { "" }
        val d = SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.US).format(Date())
        return clean("${host.removePrefix("www.")} $d")
    }

    fun date(sec: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(sec * 1000))

    fun decodeHtml(s: String): String = s
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#039;", "'")
        .replace("&lt;", "<").replace("&gt;", ">")
}
