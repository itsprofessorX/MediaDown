package com.professorx.mediadl

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Black = Color(0xFF000000)
private val Line = Color(0xFF1F1F1F)
private val Muted = Color(0xFF8A8A8A)
private val Accent = Color(0xFFFF3B4A)
private val Good = Color(0xFF4ADE80)

class MainActivity : ComponentActivity() {
    private val vm: DlViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        handleShare(intent)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent, background = Black, surface = Black,
                    onBackground = Color.White, onSurface = Color.White
                )
            ) { Surface(Modifier.fillMaxSize(), color = Black) { Screen(vm) } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) i.getStringExtra(Intent.EXTRA_TEXT)?.let { vm.url = it }
    }
}

fun platformOf(t: String): String? {
    val s = t.lowercase()
    return when {
        "instagram.com" in s || "instagr.am" in s -> "Instagram"
        "tiktok.com" in s -> "TikTok"
        "twitter.com" in s || "//x.com" in s || ".x.com" in s -> "X"
        "facebook.com" in s || "fb.watch" in s -> "Facebook"
        "reddit.com" in s -> "Reddit"
        "pinterest." in s || "pin.it" in s -> "Pinterest"
        "youtube.com" in s || "youtu.be" in s -> "YouTube"
        else -> null
    }
}

@Composable
fun Screen(vm: DlViewModel) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    var audio by remember { mutableStateOf(false) }
    var tools by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> u?.let { vm.importCookies(it) } }
    val site = platformOf(vm.url)
    val canGo = vm.ready && !vm.busy && vm.url.isNotBlank()

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column {
                Text("MediaDown", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                if (vm.engine.isNotEmpty()) Text(vm.engine, color = Muted, fontSize = 12.sp)
            }
        }

        item {
            Column {
                OutlinedTextField(
                    value = vm.url, onValueChange = { vm.url = it },
                    label = { Text("Paste link") },
                    modifier = Modifier.fillMaxWidth(), maxLines = 4,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF3A3A3A), unfocusedBorderColor = Line,
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedLabelColor = Muted, unfocusedLabelColor = Muted, cursorColor = Accent
                    )
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        clipboard.getText()?.text?.let { vm.url = it }
                    }) { Text("Paste", color = Color.White) }
                    TextButton(onClick = { vm.url = "" }) { Text("Clear", color = Muted) }
                    Spacer(Modifier.weight(1f))
                    if (site != null) Text(site, color = Accent, fontSize = 13.sp)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                ModeTab("Video / Photo", !audio) { audio = false }
                ModeTab("Audio MP3", audio) { audio = true }
            }
        }

        item {
            BigButton(if (vm.busy) "Downloading…" else if (!vm.ready) "Starting…" else "Download", canGo) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.download(audio)
            }
        }

        item {
            Column {
                ProgressBlock(vm)
                Crossfade(vm.status, label = "status") { s ->
                    Text(s, fontSize = 14.sp, color = if (s.startsWith("✅")) Good else Muted)
                }
            }
        }

        item {
            Column {
                TextButton(onClick = { tools = !tools }) { Text(if (tools) "Tools  ▴" else "Tools  ▾", color = Muted) }
                AnimatedVisibility(tools) {
                    Column {
                        TextButton(onClick = { vm.forceUpdate() }, enabled = vm.ready && !vm.busy) { Text("Update engine now", color = Color.White) }
                        TextButton(onClick = { picker.launch(arrayOf("text/*", "application/octet-stream")) }) { Text("Import cookies.txt", color = Color.White) }
                        if (vm.hasCookies) TextButton(onClick = { vm.removeCookies() }) { Text("Remove cookies", color = Muted) }
                    }
                }
            }
        }

        item { Text("Downloads", color = Muted, fontSize = 13.sp) }

        if (vm.history.isEmpty()) {
            item { Text("Nothing yet.", color = Muted, fontSize = 14.sp) }
        }
        items(vm.history, key = { it.uri.toString() }) { s ->
            Column {
                Row(
                    Modifier.fillMaxWidth().clickable {
                        try {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW).setDataAndType(s.uri, s.mime)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            )
                        } catch (_: Exception) {}
                    }.padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(s.name, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f), fontSize = 14.sp)
                    Text("Open", color = Accent, fontSize = 13.sp)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            }
        }
    }
}

/** Reads progress itself so only this small block recomposes while downloading. */
@Composable
fun ProgressBlock(vm: DlViewModel) {
    AnimatedVisibility(vm.busy) {
        Column(Modifier.padding(bottom = 8.dp)) {
            SmoothBar(vm.progress, vm.indeterminate)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val label = when {
                    vm.indeterminate -> vm.stage.ifBlank { "Preparing…" }
                    vm.stage.isNotBlank() -> "${(vm.progress * 100).toInt()}%  ·  ${vm.stage}"
                    else -> "${(vm.progress * 100).toInt()}%"
                }
                Text(label, color = Color.White, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { vm.cancel() }) { Text("Cancel", color = Muted) }
            }
        }
    }
}

@Composable
fun SmoothBar(progress: Float, indeterminate: Boolean) {
    val p = animateFloatAsState(progress, tween(300, easing = LinearEasing), label = "bar")
    val t = rememberInfiniteTransition(label = "ind").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "t"
    )
    Canvas(Modifier.fillMaxWidth().height(4.dp)) {
        val h = size.height
        val r = CornerRadius(h / 2, h / 2)
        drawRoundRect(Line, cornerRadius = r)
        if (indeterminate) {
            val seg = size.width * 0.3f
            val x = (size.width + seg) * t.value - seg
            val left = x.coerceAtLeast(0f)
            val right = (x + seg).coerceAtMost(size.width)
            if (right > left) drawRoundRect(Accent, Offset(left, 0f), Size(right - left, h), r)
        } else {
            val w = size.width * p.value.coerceIn(0f, 1f)
            if (w > 0f) drawRoundRect(Accent, Offset(0f, 0f), Size(w, h), r)
        }
    }
}

@Composable
fun ModeTab(text: String, selected: Boolean, onClick: () -> Unit) {
    val c by animateColorAsState(if (selected) Color.White else Muted, tween(200), label = "mode")
    Column(
        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Text(text, color = c, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.width(20.dp).height(2.dp).background(if (selected) Accent else Color.Transparent))
    }
}

@Composable
fun BigButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && enabled) 0.96f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "press"
    )
    Box(
        Modifier.fillMaxWidth().height(58.dp).scale(scale).clip(RoundedCornerShape(29.dp))
            .background(if (enabled) Color.White else Color(0xFF141414))
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) Color.Black else Muted, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}
