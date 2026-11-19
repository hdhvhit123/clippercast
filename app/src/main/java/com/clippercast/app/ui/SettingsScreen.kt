package com.clippercast.app.ui

import com.clippercast.app.tr
import com.clippercast.app.R

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
                .onSuccess { toast(tr(R.string.backup_done, it.articles)) }
                .onFailure { toast(tr(R.string.backup_failed, it.message ?: "")) }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { BackupManager.import(context, uri) }
                .onSuccess { toast(tr(R.string.restore_done, it.articles) + if (it.skipped > 0) tr(R.string.restore_skipped, it.skipped) else "") }
                .onFailure { toast(it.message ?: tr(R.string.backup_corrupt)) }
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(tr(R.string.settings)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr(R.string.back)) } })
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {
            Section(tr(R.string.sec_listen))
            Toggle(tr(R.string.set_intro), tr(R.string.set_intro_sub), s.readIntro) { v -> set { it.copy(readIntro = v) } }
            Toggle(tr(R.string.set_captions), null, s.readCaptions) { v -> set { it.copy(readCaptions = v) } }
            Toggle(tr(R.string.set_autonext), null, s.autoNext) { v -> set { it.copy(autoNext = v) } }
            Toggle(tr(R.string.set_archive_after), null, s.archiveAfterListen) { v -> set { it.copy(archiveAfterListen = v) } }
            Toggle(tr(R.string.set_privacy), tr(R.string.set_privacy_sub), s.privacyMax) { v -> set { it.copy(privacyMax = v) } }
            Toggle(tr(R.string.set_exp_speed), tr(R.string.set_exp_speed_sub), s.experimentalSpeed) { v -> set { it.copy(experimentalSpeed = v) } }
            voiceLanguages().forEach { lang ->
                Item(tr(R.string.set_voice_for, langDisplayName(lang)), s.voices[lang] ?: tr(R.string.auto_select)) { voiceLang = lang }
            }
            Item(tr(R.string.set_system_tts), tr(R.string.set_system_tts_sub)) {
                runCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
            }

            Section(tr(R.string.sec_saving))
            Toggle(tr(R.string.set_wifi_only), null, s.wifiOnly) { v -> set { it.copy(wifiOnly = v) } }
            Item(tr(R.string.set_images), imageOptions()[s.images]) { pick = "images" }
            Toggle(tr(R.string.set_notify), null, s.notifyOnSave) { v -> set { it.copy(notifyOnSave = v) } }
            Toggle(tr(R.string.set_clipboard), tr(R.string.set_clipboard_sub), s.clipboardSuggest) { v -> set { it.copy(clipboardSuggest = v) } }

            Section(tr(R.string.sec_library))
            Toggle(tr(R.string.set_compact), null, s.compactList) { v -> set { it.copy(compactList = v) } }
            Item(tr(R.string.trash), tr(R.string.trash_kept), onTrash)

            Section(tr(R.string.sec_storage))
            Item(tr(R.string.storage_used), formatSize(totalSize)) {}
            Item(tr(R.string.clear_archived_images), tr(R.string.keep_text)) {
                scope.launch { val freed = app.repo.clearArchivedImages(); toast(tr(R.string.freed, formatSize(freed))) }
            }
            Item(tr(R.string.auto_delete), if (s.autoDeleteArchivedDays == 0) tr(R.string.never) else tr(R.string.after_days_except_fav, s.autoDeleteArchivedDays)) { pick = "autodelete" }

            Section(tr(R.string.sec_backup))
            Toggle(tr(R.string.include_images), tr(R.string.include_images_sub), includeImages) { includeImages = it }
            Item(tr(R.string.backup_now), tr(R.string.backup_now_sub)) {
                val d = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                backupLauncher.launch("ClipperCast-$d.zip")
            }
            Item(tr(R.string.restore), tr(R.string.restore_sub)) { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }

            Section(tr(R.string.sec_privacy))
            Item(tr(R.string.clear_logins), tr(R.string.clear_logins_sub)) {
                CookieManager.getInstance().removeAllCookies(null)
                WebStorage.getInstance().deleteAllData()
                toast(tr(R.string.logins_cleared))
            }
            Text(
                tr(R.string.privacy_note),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Section(tr(R.string.sec_about))
            Item(tr(R.string.version), BuildConfigInfo.versionName(context)) {}
            Item(tr(R.string.licenses), "Readability (Apache 2.0), JSoup (MIT), OkHttp (Apache 2.0), Lora, Merriweather, Inter, OpenDyslexic (SIL OFL)") {}
            Item(tr(R.string.rate_app), null) {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))) }
            }
        }
    }

    when (pick) {
        "images" -> Choice(tr(R.string.set_images), imageOptions(), s.images,
            onPick = { i -> set { it.copy(images = i) }; pick = null }, onDismiss = { pick = null })
        "autodelete" -> {
            val opts = listOf(0, 30, 90, 180)
            Choice(tr(R.string.auto_delete), opts.map { if (it == 0) tr(R.string.never) else tr(R.string.after_days, it) }, opts.indexOf(s.autoDeleteArchivedDays).coerceAtLeast(0),
                onPick = { i -> set { it.copy(autoDeleteArchivedDays = opts[i]) }; pick = null }, onDismiss = { pick = null })
        }
    }
    voiceLang?.let { VoiceDialog(it, s, onDismiss = { voiceLang = null }) }
}

private fun imageOptions() = listOf(tr(R.string.images_always), tr(R.string.images_wifi), tr(R.string.images_never))

/** Ngôn ngữ cần chọn giọng: ngôn ngữ máy, tiếng Anh, tiếng Việt. */
private fun voiceLanguages() = listOf(Locale.getDefault().language, "en").distinct()

fun langDisplayName(lang: String): String =
    Locale(lang).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }

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
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(tr(R.string.close)) } })
}
