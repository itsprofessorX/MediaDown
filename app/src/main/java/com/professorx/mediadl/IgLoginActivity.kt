package com.professorx.mediadl

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import java.io.File

/** You log in on instagram.com inside this screen; the app never sees your password. */
class IgLoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cm = CookieManager.getInstance()
        val wv = WebView(this)
        setContentView(wv)
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(wv, true)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.userAgentString = wv.settings.userAgentString.replace("; wv", "")
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val c = cm.getCookie("https://www.instagram.com") ?: return
                if ("sessionid=" in c && "ds_user_id=" in c) {
                    save(c)
                    Toast.makeText(this@IgLoginActivity, "Instagram connected", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
            }
        }
        wv.loadUrl("https://www.instagram.com/accounts/login/")
    }

    private fun save(cookieHeader: String) {
        val sb = StringBuilder("# Netscape HTTP Cookie File\n")
        cookieHeader.split("; ").forEach { kv ->
            val i = kv.indexOf('=')
            if (i > 0) sb.append(".instagram.com\tTRUE\t/\tTRUE\t2147483647\t${kv.substring(0, i)}\t${kv.substring(i + 1)}\n")
        }
        File(filesDir, "cookies.txt").writeText(sb.toString())
    }
}
