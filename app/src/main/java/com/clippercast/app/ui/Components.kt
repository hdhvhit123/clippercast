package com.clippercast.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.clippercast.app.data.Article
import com.clippercast.app.data.ClipStatus
import com.clippercast.app.data.ReadState
import com.clippercast.app.data.TextUtils
import com.clippercast.app.player.PlayState
import java.io.File

data class StatusInfo(val icon: ImageVector, val label: String)

fun statusInfo(a: Article): StatusInfo? = when (a.status) {
    ClipStatus.PENDING -> StatusInfo(Icons.Outlined.HourglassEmpty, "Chờ tải")
    ClipStatus.LOADING -> StatusInfo(Icons.Outlined.CloudDownload, "Đang tải")
    ClipStatus.PARTIAL -> StatusInfo(Icons.Outlined.WarningAmber, "Có thể chưa đầy đủ")
    ClipStatus.FAILED -> StatusInfo(Icons.Outlined.ErrorOutline, "Lỗi")
    ClipStatus.LINK_ONLY -> StatusInfo(Icons.Outlined.Link, "Chỉ có link")
    else -> null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArticleCard(
    a: Article,
    compact: Boolean,
    speed: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    a11yActions: List<CustomAccessibilityAction> = emptyList(),
    trailing: (@Composable () -> Unit)? = null,
) {
    val status = statusInfo(a)
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Tùy chọn")
                .semantics { customActions = a11yActions }
                .padding(horizontal = 16.dp, vertical = if (compact) 10.dp else 14.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            a.siteName.uppercase(), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (a.favorite) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Filled.Favorite, "Yêu thích", Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        a.title,
                        style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                        fontWeight = if (a.readState == ReadState.READ) FontWeight.Normal else FontWeight.SemiBold,
                        maxLines = if (compact) 2 else 3, overflow = TextOverflow.Ellipsis,
                        color = if (a.readState == ReadState.READ) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (status != null) {
                            Icon(status.icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            Text(status.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (a.wordCount > 0) {
                            Text(
                                "${TextUtils.readMinutes(a.wordCount)} phút đọc · ${TextUtils.listenMinutes(a.wordCount, speed)} phút nghe",
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (!compact && a.thumb != null && File(a.thumb).exists()) {
                    Spacer(Modifier.width(12.dp))
                    AsyncImage(
                        model = File(a.thumb), contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)),
                    )
                }
                trailing?.invoke()
            }
            val progress = maxOf(a.readProgress, 0f)
            if (a.readState == ReadState.READING && progress > 0.02f && progress < 0.98f) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

@Composable
fun EmptyState(emoji: String, title: String, body: String, actions: @Composable () -> Unit = {}) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(emoji, style = MaterialTheme.typography.displayMedium)
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            actions()
        }
    }
}

/** Trình phát mini ở đáy màn hình (spec 2.3). */
@Composable
fun MiniPlayer(st: PlayState, onOpen: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit) {
    val a = st.article ?: return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp) {
        Column {
            val p = if (st.sentenceCount > 0) (st.sentenceIndex + 1f) / st.sentenceCount else 0f
            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().height(2.dp))
            Row(
                Modifier.fillMaxWidth().clickable(onClickLabel = "Mở trình phát", onClick = onOpen)
                    .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (a.thumb != null && File(a.thumb).exists()) {
                    AsyncImage(File(a.thumb), null, Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                } else {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primaryContainer))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        st.error ?: if (st.isLoading) "Đang chuẩn bị giọng đọc…" else a.siteName,
                        style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (st.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onToggle) {
                    Icon(if (st.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (st.isPlaying) "Tạm dừng" else "Phát")
                }
                IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, "Bài tiếp theo") }
            }
        }
    }
}
