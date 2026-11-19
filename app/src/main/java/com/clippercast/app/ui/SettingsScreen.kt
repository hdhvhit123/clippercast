package com.clippercast.app.ui

import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.clippercast.app.BuildConfigInfo
import com.clippercast.app.ClipperApp
import com.clippercast.app.backup.BackupManager
import com.clippercast.app.data.AppSettings
import com.clippercast.app.player.VoiceDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Màn hình Cài đặt (spec 4.2). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onTrash: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as ClipperApp
    val scope = rememberCoroutineScope()
    val s by app.settings.flow.collectAsState(initial = AppSettings())
    val totalSize by app.repo.articles.totalSize().collectAsState(initial = 0L)
    fun set(f: (AppSettings) -> AppSettings) = scope.launch { app.settings.update(f) }
    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_LONG).show()
    var pick by remember { mutableStateOf<String?>(null) }
    var voiceLang by remember { mutableStateOf<String?>(null) }
    var includeImages by remember { mutableStateOf(true) }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            runCatching { BackupManager.export(context, uri, includeImages) }
                .onSuccess { toast("Đã sao lưu ${it.articles} bài") }
                .onFailure { toast("Sao lưu thất bại: ${it.message}") }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { BackupManager.import(context, uri) }
                .onSuccess { toast("Đã khôi phục ${it.articles} bài" + if (it.skipped > 0) ", bỏ qua ${it.skipped} bài đã có" else "") }
                .onFailure { toast(it.message ?: "File sao lưu bị hỏng hoặc không đúng định dạng.") }
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Cài đặt") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } })
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {
            Section("Nghe")
            Toggle("Đọc câu giới thiệu đầu bài", "\"Tiêu đề, từ Nguồn\"", s.readIntro) { v -> set { it.copy(readIntro = v) } }
            Toggle("Đọc chú thích ảnh", null, s.readCaptions) { v -> set { it.copy(readCaptions = v) } }
            Toggle("Tự phát bài tiếp theo trong hàng chờ", null, s.autoNext) { v -> set { it.copy(autoNext = v) } }
            Toggle("Tự lưu trữ bài sau khi nghe xong", null, s.archiveAfterListen) { v -> set { it.copy(archiveAfterListen = v) } }
            Toggle("Riêng tư tối đa", "Chỉ dùng giọng đọc offline. Giọng cần mạng sẽ gửi văn bản lên máy chủ của nhà cung cấp giọng đọc.", s.privacyMax) { v -> set { it.copy(privacyMax = v) } }
            Toggle("Tốc độ thử nghiệm đến 3.0x", "Chất lượng ở tốc độ cao phụ thuộc giọng đọc của máy.", s.experimentalSpeed) { v -> set { it.copy(experimentalSpeed = v) } }
            Item("Giọng tiếng Việt", s.voices["vi"] ?: "Tự chọn") { voiceLang = "vi" }
            Item("Giọng tiếng Anh", s.voices["en"] ?: "Tự chọn") { voiceLang = "en" }
            Item("Cài đặt Chuyển văn bản thành giọng nói của hệ thống", "Tải thêm giọng đọc offline") {
                runCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
            }

            Section("Lưu bài")
            Toggle("Chỉ tải bài khi dùng Wi-Fi", null, s.wifiOnly) { v -> set { it.copy(wifiOnly = v) } }
            Item("Tải ảnh", listOf("Luôn tải", "Chỉ khi dùng Wi-Fi", "Không tải ảnh")[s.images]) { pick = "images" }
            Toggle("Thông báo khi lưu xong", null, s.notifyOnSave) { v -> set { it.copy(notifyOnSave = v) } }
            Toggle("Gợi ý lưu link vừa sao chép", "Android sẽ hiện thông báo mỗi khi app đọc bộ nhớ tạm", s.clipboardSuggest) { v -> set { it.copy(clipboardSuggest = v) } }

            Section("Thư viện")
            Toggle("Danh sách dạng gọn", null, s.compactList) { v -> set { it.copy(compactList = v) } }
            Item("Thùng rác", "Bài đã xóa được giữ 30 ngày", onTrash)

            Section("Dung lượng")
            Item("Dung lượng đang dùng", formatSize(totalSize)) {}
            Item("Xóa ảnh của bài đã lưu trữ", "Giữ lại phần chữ") {
                scope.launch { val freed = app.repo.clearArchivedImages(); toast("Đã giải phóng ${formatSize(freed)}") }
            }
            Item("Tự xóa bài trong Lưu trữ", if (s.autoDeleteArchivedDays == 0) "Không bao giờ" else "Sau ${s.autoDeleteArchivedDays} ngày (trừ bài yêu thích)") { pick = "autodelete" }

            Section("Sao lưu & khôi phục")
            Toggle("Kèm ảnh trong file sao lưu", "Tắt để file nhỏ hơn", includeImages) { includeImages = it }
            Item("Sao lưu ngay", "Lưu toàn bộ thư viện thành một file ZIP, ví dụ vào thư mục Google Drive") {
                val d = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                backupLauncher.launch("ClipperCast-$d.zip")
            }
            Item("Khôi phục từ file sao lưu", "Bài đã có sẽ được bỏ qua") { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }

            Section("Quyền riêng tư")
            Item("Xóa dữ liệu đăng nhập các trang web", "Cookie và dữ liệu của trình duyệt trong app") {
                CookieManager.getInstance().removeAllCookies(null)
                WebStorage.getInstance().deleteAllData()
                toast("Đã xóa dữ liệu đăng nhập")
            }
            Text(
                "ClipperCast không có tài khoản, không có máy chủ, không dùng quảng cáo hay công cụ theo dõi. " +
                    "Dữ liệu chỉ rời máy khi app tải trang web bạn lưu, khi bạn dùng giọng đọc cần mạng, hoặc khi bạn tự sao lưu/gửi báo lỗi.",
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Section("Giới thiệu")
            Item("Phiên bản", BuildConfigInfo.versionName(context)) {}
            Item("Giấy phép mã nguồn mở và font", "Readability (Apache 2.0), JSoup (MIT), OkHttp (Apache 2.0), Lora, Merriweather, Inter, OpenDyslexic (SIL OFL)") {}
            Item("Đánh giá trên Google Play", null) {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))) }
            }
        }
    }

    when (pick) {
        "images" -> Choice("Tải ảnh", listOf("Luôn tải", "Chỉ khi dùng Wi-Fi", "Không tải ảnh"), s.images,
            onPick = { i -> set { it.copy(images = i) }; pick = null }, onDismiss = { pick = null })
        "autodelete" -> {
            val opts = listOf(0, 30, 90, 180)
            Choice("Tự xóa bài trong Lưu trữ", opts.map { if (it == 0) "Không bao giờ" else "Sau $it ngày" }, opts.indexOf(s.autoDeleteArchivedDays).coerceAtLeast(0),
                onPick = { i -> set { it.copy(autoDeleteArchivedDays = opts[i]) }; pick = null }, onDismiss = { pick = null })
        }
    }
    voiceLang?.let { VoiceDialog(it, s, onDismiss = { voiceLang = null }) }
}

fun formatSize(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${b / 1024} KB"
    else -> String.format(Locale.ROOT, "%.1f MB", b / 1024.0 / 1024.0)
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(title, Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Toggle(title: String, subtitle: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onChange(!value) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(value, onChange)
    }
}

@Composable
private fun Item(title: String, subtitle: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun Choice(title: String, options: List<String>, selected: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column {
            options.forEachIndexed { i, o ->
                Row(Modifier.fillMaxWidth().clickable { onPick(i) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(i == selected, { onPick(i) }); Text(o)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } })
}
