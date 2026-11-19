package com.clippercast.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Bảng màu đọc (spec 2.2). */
enum class ReaderTheme(val label: String, val bg: Long, val fg: Long, val accent: Long, val dark: Boolean) {
    LIGHT("Trắng sáng", 0xFFFFFFFF, 0xFF1F1F1F, 0xFFC0441E, false),
    SEPIA("Giấy vàng", 0xFFF6EEDC, 0xFF3B2F22, 0xFF9A4A12, false),
    GRAY("Xám dịu", 0xFFDADCDF, 0xFF222428, 0xFF8A3A16, false),
    NAVY("Tím than", 0xFF1E2235, 0xFFD6D8E4, 0xFFF0A07A, true),
    OLED("Đen OLED", 0xFF000000, 0xFFC9C9C9, 0xFFF0A07A, true),
}

enum class ReaderFont(val label: String, val css: String) {
    LORA("Lora", "'Lora', serif"),
    MERRIWEATHER("Merriweather", "'Merriweather', serif"),
    INTER("Inter", "'Inter', sans-serif"),
    SYSTEM("Roboto (hệ thống)", "sans-serif"),
    DYSLEXIC("OpenDyslexic", "'OpenDyslexic', sans-serif"),
}

data class AppSettings(
    val theme: ReaderTheme = ReaderTheme.LIGHT,
    val followSystem: Boolean = true,
    val font: ReaderFont = ReaderFont.LORA,
    val fontSize: Int = 19,
    val lineHeight: Float = 1.6f,
    val margin: Int = 20,
    val justify: Boolean = false,
    val indent: Boolean = false,
    val keepScreenOn: Boolean = false,
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val readIntro: Boolean = true,
    val readCaptions: Boolean = false,
    val autoNext: Boolean = true,
    val archiveAfterListen: Boolean = false,
    val privacyMax: Boolean = true,
    val wifiOnly: Boolean = false,
    /** 0 = luôn tải ảnh, 1 = chỉ khi Wi-Fi, 2 = không tải ảnh */
    val images: Int = 0,
    val notifyOnSave: Boolean = true,
    val compactList: Boolean = false,
    val sort: Int = 0,
    val onboardingDone: Boolean = false,
    /** 0 = không bao giờ tự xóa bài lưu trữ */
    val autoDeleteArchivedDays: Int = 0,
    val clipboardSuggest: Boolean = false,
    val experimentalSpeed: Boolean = false,
    val voices: Map<String, String> = emptyMap(),
)

class SettingsRepo(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val followSystem = booleanPreferencesKey("follow_system")
        val font = stringPreferencesKey("font")
        val fontSize = intPreferencesKey("font_size")
        val lineHeight = floatPreferencesKey("line_height")
        val margin = intPreferencesKey("margin")
        val justify = booleanPreferencesKey("justify")
        val indent = booleanPreferencesKey("indent")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val speed = floatPreferencesKey("speed")
        val pitch = floatPreferencesKey("pitch")
        val readIntro = booleanPreferencesKey("read_intro")
        val readCaptions = booleanPreferencesKey("read_captions")
        val autoNext = booleanPreferencesKey("auto_next")
        val archiveAfterListen = booleanPreferencesKey("archive_after_listen")
        val privacyMax = booleanPreferencesKey("privacy_max")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val images = intPreferencesKey("images")
        val notifyOnSave = booleanPreferencesKey("notify_on_save")
        val compactList = booleanPreferencesKey("compact_list")
        val sort = intPreferencesKey("sort")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val autoDelete = intPreferencesKey("auto_delete_days")
        val clipboard = booleanPreferencesKey("clipboard_suggest")
        val experimentalSpeed = booleanPreferencesKey("experimental_speed")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        val d = AppSettings()
        AppSettings(
            theme = p[K.theme]?.let { runCatching { ReaderTheme.valueOf(it) }.getOrNull() } ?: d.theme,
            followSystem = p[K.followSystem] ?: d.followSystem,
            font = p[K.font]?.let { runCatching { ReaderFont.valueOf(it) }.getOrNull() } ?: d.font,
            fontSize = p[K.fontSize] ?: d.fontSize,
            lineHeight = p[K.lineHeight] ?: d.lineHeight,
            margin = p[K.margin] ?: d.margin,
            justify = p[K.justify] ?: d.justify,
            indent = p[K.indent] ?: d.indent,
            keepScreenOn = p[K.keepScreenOn] ?: d.keepScreenOn,
            speed = p[K.speed] ?: d.speed,
            pitch = p[K.pitch] ?: d.pitch,
            readIntro = p[K.readIntro] ?: d.readIntro,
            readCaptions = p[K.readCaptions] ?: d.readCaptions,
            autoNext = p[K.autoNext] ?: d.autoNext,
            archiveAfterListen = p[K.archiveAfterListen] ?: d.archiveAfterListen,
            privacyMax = p[K.privacyMax] ?: d.privacyMax,
            wifiOnly = p[K.wifiOnly] ?: d.wifiOnly,
            images = p[K.images] ?: d.images,
            notifyOnSave = p[K.notifyOnSave] ?: d.notifyOnSave,
            compactList = p[K.compactList] ?: d.compactList,
            sort = p[K.sort] ?: d.sort,
            onboardingDone = p[K.onboardingDone] ?: d.onboardingDone,
            autoDeleteArchivedDays = p[K.autoDelete] ?: d.autoDeleteArchivedDays,
            clipboardSuggest = p[K.clipboard] ?: d.clipboardSuggest,
            experimentalSpeed = p[K.experimentalSpeed] ?: d.experimentalSpeed,
            voices = p.asMap().entries
                .filter { it.key.name.startsWith("voice_") }
                .associate { it.key.name.removePrefix("voice_") to it.value.toString() },
        )
    }

    suspend fun current(): AppSettings = flow.first()

    suspend fun update(block: (AppSettings) -> AppSettings) {
        val old = current()
        val n = block(old)
        context.dataStore.edit { p ->
            p[K.theme] = n.theme.name
            p[K.followSystem] = n.followSystem
            p[K.font] = n.font.name
            p[K.fontSize] = n.fontSize
            p[K.lineHeight] = n.lineHeight
            p[K.margin] = n.margin
            p[K.justify] = n.justify
            p[K.indent] = n.indent
            p[K.keepScreenOn] = n.keepScreenOn
            p[K.speed] = n.speed
            p[K.pitch] = n.pitch
            p[K.readIntro] = n.readIntro
            p[K.readCaptions] = n.readCaptions
            p[K.autoNext] = n.autoNext
            p[K.archiveAfterListen] = n.archiveAfterListen
            p[K.privacyMax] = n.privacyMax
            p[K.wifiOnly] = n.wifiOnly
            p[K.images] = n.images
            p[K.notifyOnSave] = n.notifyOnSave
            p[K.compactList] = n.compactList
            p[K.sort] = n.sort
            p[K.onboardingDone] = n.onboardingDone
            p[K.autoDelete] = n.autoDeleteArchivedDays
            p[K.clipboard] = n.clipboardSuggest
            p[K.experimentalSpeed] = n.experimentalSpeed
            n.voices.forEach { (lang, voice) -> p[stringPreferencesKey("voice_$lang")] = voice }
        }
    }
}
