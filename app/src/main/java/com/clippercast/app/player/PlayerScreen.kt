package com.clippercast.app.player

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.clippercast.app.ClipperApp
import com.clippercast.app.data.AppSettings
import kotlinx.coroutines.delay
import java.io.File

/** Trình phát đầy đủ (spec 4.1). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(onBack: () -> Unit, openArticle: (Long) -> Unit, openQueue: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as ClipperApp
    val st by Playback.state.collectAsState()
    val settings by app.settings.flow.collectAsState(initial = AppSettings())
    var showSleep by remember { mutableStateOf(false) }
    var showVoice by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(st.sleepEndsAt) { while (st.sleepEndsAt != null) { now = System.currentTimeMillis(); delay(1000) } }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Đang nghe") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.KeyboardArrowDown, "Đóng trình phát") } },
            actions = {
                IconButton(onClick = openQueue) { Icon(Icons.AutoMirrored.Outlined.QueueMusic, "Hàng chờ nghe") }
                st.article?.let { a -> IconButton(onClick = { openArticle(a.id) }) { Icon(Icons.AutoMirrored.Outlined.MenuBook, "Mở bài để đọc") } }
            },
        )
    }) { pad ->
        val a = st.article
        if (a == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Chưa có bài nào đang phát", style = MaterialTheme.typography.titleMedium)
                    if (st.queue.isNotEmpty()) Button(onClick = { Playback.resume() }) { Text("Phát hàng chờ (${st.queue.size} bài)") }
                }
            }
            return@Scaffold
        }
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            if (a.thumb != null && File(a.thumb).exists()) {
                AsyncImage(File(a.thumb), null, Modifier.fillMaxWidth(0.7f).aspectRatio(1f).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxWidth(0.7f).aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center) {
                    Text(a.siteName.take(2).uppercase(), style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(a.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(a.siteName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            // Câu đang đọc dạng phụ đề lớn
            Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
                contentAlignment = Alignment.Center) {
                Text(
                    st.error ?: st.currentText.ifBlank { if (st.isLoading) "Đang chuẩn bị giọng đọc…" else "" },
                    style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis,
                    color = if (st.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (st.needsVoiceData) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button(onClick = {
                        try { context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        catch (_: ActivityNotFoundException) {
                            context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }) { Text("Tải giọng đọc") }
                    TextButton(onClick = { Playback.clearError(); Playback.resume() }) { Text("Thử lại") }
                }
            }
            Spacer(Modifier.height(16.dp))
            val progress = if (st.sentenceCount > 0) (st.sentenceIndex + 1f) / st.sentenceCount else 0f
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)))
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text("Câu ${st.sentenceIndex + 1}/${st.sentenceCount}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                val remainChars = (st.totalChars - st.charsBefore).coerceAtLeast(0)
                val remainMin = (remainChars / (Playback.CHARS_PER_SEC * st.speed) / 60).toInt()
                Text("còn ~${remainMin} phút", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(12.dp))
            // Điều khiển: chạm = tua 1 câu, nhấn giữ = tua 1 đoạn
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { Playback.previousArticle() }) { Icon(Icons.Outlined.SkipPrevious, "Bài trước", Modifier.size(30.dp)) }
                Box(Modifier.size(52.dp).clip(CircleShape).combinedClickable(
                    onClickLabel = "Lùi một câu", onLongClickLabel = "Lùi một đoạn",
                    onClick = { Playback.seekSentence(-1) }, onLongClick = { Playback.seekParagraph(false) }), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.FastRewind, "Lùi một câu", Modifier.size(30.dp))
                }
                FilledIconButton(onClick = { Playback.toggle() }, modifier = Modifier.size(76.dp),
                    colors = IconButtonDefaults.filledIconButtonColors()) {
                    Icon(if (st.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (st.isPlaying) "Tạm dừng" else "Phát", Modifier.size(40.dp))
                }
                Box(Modifier.size(52.dp).clip(CircleShape).combinedClickable(
                    onClickLabel = "Tới một câu", onLongClickLabel = "Tới một đoạn",
                    onClick = { Playback.seekSentence(1) }, onLongClick = { Playback.seekParagraph(true) }), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.FastForward, "Tới một câu", Modifier.size(30.dp))
                }
                IconButton(onClick = { Playback.nextArticle() }) { Icon(Icons.Outlined.SkipNext, "Bài tiếp theo", Modifier.size(30.dp)) }
            }
            Spacer(Modifier.height(16.dp))
            // Tốc độ (spec 2.3: 0.5x–2.5x, 2.6x–3.0x là thử nghiệm)
            val maxSpeed = if (settings.experimentalSpeed) 3.0f else 2.5f
            Text("Tốc độ: ${"%.1f".format(st.speed)}x", style = MaterialTheme.typography.labelLarge)
            Slider(st.speed, { v -> Playback.setSpeed((v * 10).toInt() / 10f) }, valueRange = 0.5f..maxSpeed,
                steps = ((maxSpeed - 0.5f) * 10).toInt() - 1)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.8f, 1.0f, 1.2f, 1.5f, 2.0f).forEach { v ->
                    FilterChip(kotlin.math.abs(st.speed - v) < 0.05f, { Playback.setSpeed(v) }, label = { Text("${v}x") })
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val sleepLabel = when {
                    st.sleepEndOfArticle -> "Tắt khi hết bài"
                    st.sleepEndsAt != null -> "Tắt sau ${((st.sleepEndsAt!! - now) / 60000) + 1} phút"
                    else -> "Hẹn giờ tắt"
                }
                TextButton(onClick = { showSleep = true }) { Icon(Icons.Outlined.Bedtime, null); Spacer(Modifier.width(6.dp)); Text(sleepLabel) }
                TextButton(onClick = { showVoice = true }) { Icon(Icons.Outlined.RecordVoiceOver, null); Spacer(Modifier.width(6.dp)); Text("Giọng đọc") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showSleep) {
        AlertDialog(onDismissRequest = { showSleep = false }, title = { Text("Hẹn giờ tắt") }, text = {
            Column {
                listOf(5, 15, 30, 45, 60).forEach { m ->
                    TextButton(onClick = { Playback.setSleepTimer(m); showSleep = false }) { Text("$m phút") }
                }
                TextButton(onClick = { Playback.setSleepEndOfArticle(); showSleep = false }) { Text("Khi hết bài này") }
                TextButton(onClick = { Playback.setSleepTimer(null); showSleep = false }) { Text("Tắt hẹn giờ") }
            }
        }, confirmButton = {})
    }
    if (showVoice) VoiceDialog(st.article?.lang ?: "vi", settings, onDismiss = { showVoice = false })
}

@Composable
fun VoiceDialog(lang: String, settings: AppSettings, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var voices by remember { mutableStateOf<List<Voice>?>(null) }
    LaunchedEffect(lang) { Playback.voicesFor(lang) { voices = it } }
    val current = settings.voices[lang]
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Giọng đọc") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            when {
                voices == null -> Text("Đang tải danh sách giọng…")
                voices!!.isEmpty() -> Text("Máy chưa có giọng đọc cho ngôn ngữ này. Hãy tải giọng trong cài đặt Chuyển văn bản thành giọng nói của hệ thống.")
                else -> voices!!.forEachIndexed { i, v ->
                    val net = v.isNetworkConnectionRequired
                    val disabled = net && settings.privacyMax
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = v.name == current, enabled = !disabled, onClick = { Playback.setVoice(lang, v.name); onDismiss() })
                        Column {
                            Text("Giọng ${i + 1}" + if (net) " ☁️ cần mạng" else " · offline")
                            Text(v.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (settings.privacyMax) Text("Chế độ Riêng tư tối đa đang bật: chỉ dùng giọng offline.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
    }, confirmButton = {
        TextButton(onClick = {
            runCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }) { Text("Cài đặt TTS hệ thống") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Đóng") } })
}
