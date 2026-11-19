package com.clippercast.app.ui

import com.clippercast.app.tr
import com.clippercast.app.R

import android.content.ClipboardManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.QueuePlayNext
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clippercast.app.ClipperApp
import com.clippercast.app.clip.UrlNormalizer
import com.clippercast.app.data.Article
import com.clippercast.app.data.ClipStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleActionSheet(a: Article, actions: ArticleActions, nav: HomeNav, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(a.title, Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            statusInfo(a)?.let { s ->
                Text(
                    a.error ?: s.label, Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (a.status == ClipStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            fun go(f: () -> Unit) { onDismiss(); f() }
            val readable = ClipStatus.isReadable(a.status)
            if (readable) {
                SheetItem(Icons.AutoMirrored.Outlined.MenuBook, tr(R.string.read)) { go { nav.openArticle(a.id) } }
                SheetItem(Icons.Outlined.Headphones, tr(R.string.listen_now)) { go { nav.listen(a.id) } }
                SheetItem(Icons.Outlined.QueuePlayNext, tr(R.string.play_next)) { go { actions.playNext(a) } }
                SheetItem(Icons.AutoMirrored.Outlined.PlaylistAdd, tr(R.string.add_to_queue_end)) { go { actions.enqueue(a) } }
            }
            if (a.status == ClipStatus.FAILED || a.status == ClipStatus.PARTIAL || a.status == ClipStatus.PENDING) {
                SheetItem(Icons.Outlined.Refresh, tr(R.string.try_download_again)) { go { actions.retry(a) } }
            }
            if (!a.isNote && a.url.isNotBlank() && a.status != ClipStatus.READY) {
                SheetItem(Icons.AutoMirrored.Outlined.Login, tr(R.string.open_to_sign_in)) { go { nav.browser(a.id) } }
            }
            SheetItem(Icons.Outlined.FavoriteBorder, if (a.favorite) tr(R.string.unfavorite) else tr(R.string.favorite)) { go { actions.favorite(a) } }
            if (a.archived) SheetItem(Icons.Outlined.Unarchive, tr(R.string.move_to_inbox)) { go { actions.unarchive(a) } }
            else SheetItem(Icons.Outlined.Archive, tr(R.string.archive_read)) { go { actions.archive(a) } }
            if (a.url.isNotBlank()) SheetItem(Icons.AutoMirrored.Outlined.OpenInNew, tr(R.string.view_original)) { go { actions.openOriginal(a) } }
            SheetItem(Icons.Outlined.Delete, tr(R.string.delete)) { go { actions.delete(a) } }
        }
    }
}

@Composable
fun SheetItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Nút "+": dán link hoặc dán văn bản thủ công (spec 2.1.2, tầng 3). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDialog(onDismiss: () -> Unit, onSaved: (String) -> Unit, initialMode: Int = 0) {
    val context = LocalContext.current
    val app = context.applicationContext as ClipperApp
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(initialMode) }
    var link by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val cm = context.getSystemService(ClipboardManager::class.java)
        val clip = runCatching { cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull()
        UrlNormalizer.extractUrl(clip)?.let { link = it }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(R.string.add_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(mode == 0, { mode = 0 }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(tr(R.string.paste_link)) }
                    SegmentedButton(mode == 1, { mode = 1 }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(tr(R.string.paste_text)) }
                }
                if (mode == 0) {
                    OutlinedTextField(link, { link = it }, label = { Text(tr(R.string.links_hint)) }, minLines = 2, maxLines = 6,
                        modifier = Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(title, { title = it }, label = { Text(tr(R.string.title_optional)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(body, { body = it }, label = { Text(tr(R.string.content)) }, minLines = 5, maxLines = 10,
                        modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    if (mode == 0) {
                        val urls = link.lines().mapNotNull { UrlNormalizer.extractUrl(it) }
                        if (urls.isEmpty()) { onSaved(tr(R.string.no_valid_link)); return@launch }
                        var dup = 0
                        urls.forEach { if (app.repo.saveUrl(it).duplicate) dup++ }
                        onSaved(if (urls.size == 1) (if (dup == 1) tr(R.string.already_saved) else tr(R.string.saved_downloading))
                            else tr(R.string.saved_n_links, urls.size - dup))
                    } else {
                        if (body.isBlank()) { onSaved(tr(R.string.content_empty)); return@launch }
                        app.repo.addNote(title, body)
                        onSaved(tr(R.string.text_saved))
                    }
                    onDismiss()
                }
            }) { Text(tr(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(R.string.cancel)) } },
    )
}

/** Bài mẫu để người dùng mới thử ngay mà không cần mạng (spec 4.1, onboarding). */
object SampleArticle {
    suspend fun insert(app: ClipperApp): Long {
        val text = tr(R.string.sample_body)
        return app.repo.addNote(tr(R.string.sample_title), text)
    }
}
