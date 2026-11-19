package com.clippercast.app.ui

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
import androidx.compose.material.icons.outlined.Login
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
                SheetItem(Icons.AutoMirrored.Outlined.MenuBook, "Đọc") { go { nav.openArticle(a.id) } }
                SheetItem(Icons.Outlined.Headphones, "Nghe ngay") { go { nav.listen(a.id) } }
                SheetItem(Icons.Outlined.QueuePlayNext, "Phát tiếp theo") { go { actions.playNext(a) } }
                SheetItem(Icons.AutoMirrored.Outlined.PlaylistAdd, "Thêm vào cuối hàng chờ") { go { actions.enqueue(a) } }
            }
            if (a.status == ClipStatus.FAILED || a.status == ClipStatus.PARTIAL || a.status == ClipStatus.PENDING) {
                SheetItem(Icons.Outlined.Refresh, "Thử tải lại") { go { actions.retry(a) } }
            }
            if (!a.isNote && a.url.isNotBlank() && a.status != ClipStatus.READY) {
                SheetItem(Icons.Outlined.Login, "Mở trong app để đăng nhập rồi lưu") { go { nav.browser(a.id) } }
            }
            SheetItem(Icons.Outlined.FavoriteBorder, if (a.favorite) "Bỏ yêu thích" else "Yêu thích") { go { actions.favorite(a) } }
            if (a.archived) SheetItem(Icons.Outlined.Unarchive, "Đưa về Hộp thư") { go { actions.unarchive(a) } }
            else SheetItem(Icons.Outlined.Archive, "Lưu trữ (đã đọc)") { go { actions.archive(a) } }
            if (a.url.isNotBlank()) SheetItem(Icons.AutoMirrored.Outlined.OpenInNew, "Xem bản gốc") { go { actions.openOriginal(a) } }
            SheetItem(Icons.Outlined.Delete, "Xóa") { go { actions.delete(a) } }
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
        title = { Text("Thêm vào ClipperCast") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(mode == 0, { mode = 0 }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Dán link") }
                    SegmentedButton(mode == 1, { mode = 1 }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Dán văn bản") }
                }
                if (mode == 0) {
                    OutlinedTextField(link, { link = it }, label = { Text("Đường link (mỗi dòng một link)") }, minLines = 2, maxLines = 6,
                        modifier = Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(title, { title = it }, label = { Text("Tiêu đề (không bắt buộc)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(body, { body = it }, label = { Text("Nội dung") }, minLines = 5, maxLines = 10,
                        modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    if (mode == 0) {
                        val urls = link.lines().mapNotNull { UrlNormalizer.extractUrl(it) }
                        if (urls.isEmpty()) { onSaved("Không tìm thấy link hợp lệ"); return@launch }
                        var dup = 0
                        urls.forEach { if (app.repo.saveUrl(it).duplicate) dup++ }
                        onSaved(if (urls.size == 1) (if (dup == 1) "Bài này đã có trong thư viện" else "Đã lưu, đang tải nội dung…")
                            else "Đã lưu ${urls.size - dup} link mới")
                    } else {
                        if (body.isBlank()) { onSaved("Nội dung đang trống"); return@launch }
                        app.repo.addNote(title, body)
                        onSaved("Đã lưu văn bản")
                    }
                    onDismiss()
                }
            }) { Text("Lưu") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
    )
}

/** Bài mẫu để người dùng mới thử ngay mà không cần mạng (spec 4.1, onboarding). */
object SampleArticle {
    suspend fun insert(app: ClipperApp): Long {
        val text = """
Chào mừng bạn đến với ClipperCast. Đây là một bài viết mẫu để bạn thử trình đọc và giọng đọc ngay, kể cả khi không có mạng.

Cách lưu bài rất đơn giản. Khi đang đọc một trang trong Chrome, bạn bấm nút ba chấm, chọn Chia sẻ, rồi chọn ClipperCast. Ứng dụng sẽ lưu link ngay lập tức, sau đó tải và làm sạch nội dung ở chế độ nền.

Bài viết đã lưu nằm trong máy của bạn. Bạn có thể đọc với năm bảng màu, nhiều kiểu chữ và cỡ chữ khác nhau. Chạm vào giữa màn hình để hiện thanh công cụ.

Khi bận tay, hãy bấm nút tai nghe để nghe bài viết như một tập podcast. Câu đang đọc sẽ được tô sáng. Bạn có thể đổi tốc độ, hẹn giờ tắt, và xếp nhiều bài vào hàng chờ để nghe liên tục.

ClipperCast không cần tài khoản và không gửi dữ liệu của bạn đi đâu. Chúc bạn đọc vui!
""".trim()
        return app.repo.addNote("Chào mừng đến với ClipperCast", text)
    }
}
