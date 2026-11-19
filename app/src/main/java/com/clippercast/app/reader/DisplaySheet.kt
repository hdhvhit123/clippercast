package com.clippercast.app.reader

import com.clippercast.app.tr
import com.clippercast.app.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.clippercast.app.ClipperApp
import com.clippercast.app.data.AppSettings
import com.clippercast.app.data.ReaderFont
import com.clippercast.app.data.ReaderTheme
import kotlinx.coroutines.launch

/** Bảng chỉnh hiển thị: xem trước trực tiếp trên bài đang đọc (spec 4). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplaySheet(s: AppSettings, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as ClipperApp
    val scope = rememberCoroutineScope()
    fun set(f: (AppSettings) -> AppSettings) = scope.launch { app.settings.update(f) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr(R.string.color_theme), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ReaderTheme.entries.forEach { t ->
                    val sel = s.theme == t
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(Color(t.bg))
                            .border(BorderStroke(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline), CircleShape)
                            .clickable { set { it.copy(theme = t) } }
                            .semantics { contentDescription = tr(t.labelRes); selected = sel },
                        contentAlignment = Alignment.Center,
                    ) { Text("Aa", color = Color(t.fg)) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr(R.string.follow_system), Modifier.weight(1f))
                Switch(s.followSystem, { v -> set { it.copy(followSystem = v) } })
            }
            Text(tr(R.string.font), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderFont.entries.forEach { f ->
                    FilterChip(s.font == f, { set { it.copy(font = f) } }, label = { Text(f.labelRes?.let { tr(it) } ?: f.label) })
                }
            }
            if (s.font == ReaderFont.DYSLEXIC) {
                Text(tr(R.string.dyslexic_note),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Labeled(tr(R.string.font_size, s.fontSize)) {
                Slider(s.fontSize.toFloat(), { v -> set { it.copy(fontSize = v.toInt()) } }, valueRange = 14f..32f, steps = 17)
            }
            Labeled(tr(R.string.line_height, "%.1f".format(s.lineHeight))) {
                Slider(s.lineHeight, { v -> set { it.copy(lineHeight = (v * 10).toInt() / 10f) } }, valueRange = 1.2f..2.2f, steps = 9)
            }
            Labeled(tr(R.string.margins, s.margin)) {
                Slider(s.margin.toFloat(), { v -> set { it.copy(margin = v.toInt()) } }, valueRange = 8f..48f, steps = 9)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr(R.string.justify), Modifier.weight(1f)); Switch(s.justify, { v -> set { it.copy(justify = v) } })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr(R.string.indent), Modifier.weight(1f)); Switch(s.indent, { v -> set { it.copy(indent = v) } })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr(R.string.keep_screen_on), Modifier.weight(1f)); Switch(s.keepScreenOn, { v -> set { it.copy(keepScreenOn = v) } })
            }
        }
    }
}

@Composable
private fun Labeled(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        content()
    }
}
