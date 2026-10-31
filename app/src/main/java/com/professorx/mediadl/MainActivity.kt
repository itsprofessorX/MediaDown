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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

private val Accent1 = Color(0xFFFF3B4A)
private val Accent2 = Color(0xFFB0102A)
private val CardBg = Color(0xFF16161C)
private val Muted = Color(0xFF9A9AA6)
private val AccentBrush = Brush.horizontalGradient(listOf(Accent1, Accent2))

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
                    primary = Accent1, background = Color(0xFF0B0B0F),
                    surface = CardBg, onBackground = Color.White, onSurface = Color.White
                )
            ) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Screen(vm) } }
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
    val pct = (vm.progress * 100).toInt()

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    "MediaDown",
                    style = TextStyle(brush = AccentBrush, fontSize = 38.sp, fontWeight = FontWeight.Black)
                )
                Text("Save videos, photos & audio from your favorite apps", color = Muted, fontSize = 14.sp)
            }
        }

        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(CardBg).padding(16.dp)) {
                OutlinedTextField(
                    value = vm.url, onValueChange = { vm.url = it },
                    label = { Text("Paste up to 3 links") },
                    modifier = Modifier.fillMaxWidth(), maxLines = 4,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent1, unfocusedBorderColor = Color(0xFF2E2E38),
                        focusedLabelColor = Accent1, cursorColor = Accent1
                    )
                )
                AnimatedVisibility(site != null) {
                    Text("● $site link detected", color = Accent1, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                }
                Row {
                    TextButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        clipboard.getText()?.text?.let { vm.url = it }
                    }) { Text("Paste", color = Accent1) }
                    TextButton(onClick = { vm.url = "" }) { Text("Clear", color = Muted) }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CardBg).padding(4.dp)) {
                ModeTab("🎬  Video / Photo", !audio, Modifier.weight(1f)) { audio = false }
                ModeTab("🎵  MP3 Audio", audio, Modifier.weight(1f)) { audio = true }
            }
        }

        item {
            val label = when {
                !vm.ready -> "Starting engine…"
                vm.busy -> "Downloading  $pct%"
                audio -> "Download MP3"
                else -> "Download"
            }
            BigButton(label, canGo) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.download(audio)
            }
        }

        item {
            Column {
                AnimatedVisibility(vm.busy) {
                    Column(Modifier.padding(bottom = 10.dp)) {
                        WavyProgress(vm.progress, vm.busy)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("$pct%", color = Accent1, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { vm.cancel() }) { Text("Cancel", color = Muted) }
                        }
                    }
                }
                Crossfade(vm.status, label = "status") { s ->
                    Text(
                        s, fontSize = 14.sp,
                        color = if (s.startsWith("✅")) Color(0xFF4ADE80) else Muted,
                        textAlign = TextAlign.Start
                    )
                }
            }
        }

        item {
            Column {
                TextButton(onClick = { tools = !tools }) { Text(if (tools) "Hide tools ▴" else "Tools ▾", color = Muted) }
                AnimatedVisibility(tools) {
                    Column {
                        TextButton(onClick = { vm.updateEngine() }, enabled = vm.ready && !vm.busy) { Text("⟳  Update engine (fixes broken sites)", color = Accent1) }
                        TextButton(onClick = { picker.launch(arrayOf("text/*", "application/octet-stream")) }) { Text("🍪  Import cookies.txt (private posts)", color = Accent1) }
                        if (vm.hasCookies) TextButton(onClick = { vm.removeCookies() }) { Text("Remove cookies", color = Muted) }
                    }
                }
            }
        }

        item { Text("Downloads", fontWeight = FontWeight.Bold, fontSize = 18.sp) }

        if (vm.history.isEmpty()) {
            item { Text("Nothing yet. Paste a link or share one to MediaDown.", color = Muted, fontSize = 14.sp) }
        }
        items(vm.history, key = { it.uri.toString() }) { s ->
            val icon = when {
                s.mime.startsWith("video") -> "🎬"
                s.mime.startsWith("image") -> "🖼️"
                s.mime.startsWith("audio") -> "🎵"
                else -> "📄"
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardBg)
                    .clickable {
                        try {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW).setDataAndType(s.uri, s.mime)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            )
                        } catch (_: Exception) {}
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(icon, fontSize = 22.sp)
                Spacer(Modifier.padding(6.dp))
                Text(s.name, maxLines = 1, modifier = Modifier.weight(1f))
                Text("Open", color = Accent1, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun ModeTab(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Accent1 else Color.Transparent, tween(250), label = "tab")
    Box(
        modifier.clip(RoundedCornerShape(20.dp)).background(bg)
            .clickable(onClick = onClick).padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else Muted, fontSize = 14.sp)
    }
}

@Composable
fun BigButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && enabled) 0.94f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "press"
    )
    Box(
        Modifier.fillMaxWidth().height(64.dp).scale(scale).clip(RoundedCornerShape(32.dp))
            .background(if (enabled) AccentBrush else SolidColor(Color(0xFF26262E)))
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) Color.White else Muted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun WavyProgress(progress: Float, busy: Boolean, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "wave")
    val phase by inf.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "phase"
    )
    val pulse by inf.animateFloat(
        0.06f, 0.2f,
        infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse"
    )
    val target by animateFloatAsState(progress, tween(400), label = "progress")
    val shown = if (busy && target < 0.01f) pulse else target

    Canvas(modifier.fillMaxWidth().height(30.dp)) {
        val w = size.width
        val cy = size.height / 2
        val amp = 6.dp.toPx()
        val len = 30.dp.toPx()
        val stroke = 5.dp.toPx()
        val endX = (w * shown).coerceIn(0f, w)

        drawLine(Color(0x33FFFFFF), Offset(endX, cy), Offset(w, cy), strokeWidth = stroke, cap = StrokeCap.Round)

        val path = Path()
        var x = 0f
        path.moveTo(0f, cy + amp * sin(phase))
        while (x <= endX) {
            path.lineTo(x, cy + amp * sin((x / len * 2f * PI.toFloat()) + phase))
            x += 3f
        }
        drawPath(
            path, Brush.horizontalGradient(listOf(Accent1, Accent2), 0f, w),
            style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
