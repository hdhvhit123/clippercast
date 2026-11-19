package com.clippercast.app.player

import com.clippercast.app.tr
import com.clippercast.app.R

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.core.content.ContextCompat
import com.clippercast.app.data.TextUtils
import com.clippercast.app.data.AppSettings
import com.clippercast.app.data.Article
import com.clippercast.app.data.ClipStatus
import com.clippercast.app.data.ReadState
import com.clippercast.app.data.Repository
import com.clippercast.app.data.SettingsRepo
import com.clippercast.app.reader.SentenceMarker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class PlayState(
    val article: Article? = null,
    val sentenceIndex: Int = 0,
    val sentenceCount: Int = 0,
    val currentText: String = "",
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val error: String? = null,
    /** true khi lỗi do máy thiếu dữ liệu giọng đọc: giao diện hiện nút "Tải giọng". */
    val needsVoiceData: Boolean = false,
    val sleepEndsAt: Long? = null,
    val sleepEndOfArticle: Boolean = false,
    val voiceName: String? = null,
    val queue: List<Article> = emptyList(),
    /** Ký tự trước mỗi câu, để ước tính vị trí theo thời gian cho màn hình khóa. */
    val charsBefore: Int = 0,
    val totalChars: Int = 0,
)

/**
 * Bộ điều khiển phát TTS dùng chung cho giao diện, thông báo và màn hình khóa (spec 2.3).
 * Mọi hàm public phải được gọi trên luồng chính.
 */
object Playback {
    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val repo by lazy { Repository.get(app) }
    private val settingsRepo by lazy { SettingsRepo(app) }

    private val _state = MutableStateFlow(PlayState())
    val state: StateFlow<PlayState> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val onReady = mutableListOf<() -> Unit>()
    private var sentences: List<String> = emptyList()
    private var prefix: IntArray = IntArray(0)
    private var intro: String? = null
    private var session = 0 // tăng mỗi lần bắt đầu đọc lại, bỏ qua callback cũ
    private var sleepJob: Job? = null
    private var resumeOnFocusGain = false
    private var settings = AppSettings()

    var player: TtsPlayer? = null
        private set

    const val CHARS_PER_SEC = 14.0

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        player = TtsPlayer(Looper.getMainLooper())
        scope.launch {
            settingsRepo.flow.collect { s ->
                settings = s
                _state.update { it.copy(speed = s.speed, pitch = s.pitch) }
            }
        }
        scope.launch {
            repo.queue().collect { q ->
                _state.update { st -> st.copy(queue = q, article = st.article?.let { a -> q.firstOrNull { it.id == a.id } ?: a }) }
                notifyPlayer()
            }
        }
    }

    // ---------- TTS ----------
    private fun withTts(block: () -> Unit) {
        if (ttsReady && tts != null) { block(); return }
        onReady.add(block)
        if (tts != null) return
        tts = TextToSpeech(app) { status ->
            main.post {
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    tts?.setAudioAttributes(
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                    )
                    tts?.setOnUtteranceProgressListener(listener)
                    val pending = onReady.toList(); onReady.clear(); pending.forEach { it() }
                } else {
                    onReady.clear()
                    tts = null
                    _state.update { it.copy(isLoading = false, isPlaying = false, needsVoiceData = true,
                        error = tr(R.string.no_tts_engine)) }
                    notifyPlayer()
                }
            }
        }
    }

    /** Danh sách giọng cho một ngôn ngữ (lấy trực tiếp từ engine, spec 2.3). */
    fun voicesFor(lang: String, callback: (List<Voice>) -> Unit) = withTts {
        val code = TextUtils.langKey(lang)
        val list = runCatching { tts?.voices?.toList() }.getOrNull().orEmpty()
            .filter { it.locale.language == code }
            .filter { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
            .sortedWith(compareBy<Voice>({ it.isNetworkConnectionRequired }, { -it.quality }, { it.name }))
        callback(list)
    }

    private fun pickVoice(rawLang: String): Pair<Boolean, String?> {
        val lang = TextUtils.langKey(rawLang)
        val t = tts ?: return false to null
        val all = runCatching { t.voices?.toList() }.getOrNull().orEmpty()
            .filter { it.locale.language == lang && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
        val allowed = if (settings.privacyMax) all.filter { !it.isNetworkConnectionRequired } else all
        val preferred = settings.voices[lang]?.let { name -> allowed.firstOrNull { it.name == name } }
        val chosen = preferred ?: allowed.sortedWith(
            compareBy<Voice>({ it.isNetworkConnectionRequired }, { -it.quality })
        ).firstOrNull()
        if (chosen != null) {
            t.setVoice(chosen)
            return true to chosen.name
        }
        if (settings.privacyMax && all.isNotEmpty()) return false to null
        val r = t.setLanguage(Locale.forLanguageTag(lang))
        return (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED) to t.voice?.name
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            val (sid, idx) = parse(utteranceId) ?: return
            main.post {
                if (sid != session || idx < 0) return@post
                setSentence(idx)
            }
        }
        override fun onDone(utteranceId: String) {
            val (sid, idx) = parse(utteranceId) ?: return
            main.post {
                if (sid != session) return@post
                if (idx == sentences.size - 1) onArticleFinished()
            }
        }
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) { onDone(utteranceId) }
        override fun onError(utteranceId: String, errorCode: Int) {
            if (errorCode == TextToSpeech.ERROR_NETWORK || errorCode == TextToSpeech.ERROR_NETWORK_TIMEOUT) {
                main.post {
                    pause()
                    _state.update { it.copy(error = tr(R.string.voice_needs_network)) }
                }
            } else onDone(utteranceId)
        }
    }

    private fun parse(id: String): Pair<Int, Int>? {
        val p = id.split(':'); if (p.size != 2) return null
        return (p[0].toIntOrNull() ?: return null) to (p[1].toIntOrNull() ?: return null)
    }

    private fun setSentence(i: Int) {
        val count = sentences.size
        _state.update {
            it.copy(sentenceIndex = i, currentText = sentences.getOrElse(i) { "" },
                charsBefore = if (i in prefix.indices) prefix[i] else 0, sentenceCount = count)
        }
        if (i % 8 == 0) savePosition()
        notifyPlayer()
    }

    // ---------- Điều khiển ----------
    /** Phát một bài, mặc định tiếp tục từ câu đã nghe dở. */
    fun play(articleId: Long, fromSentence: Int? = null) {
        scope.launch {
            val a = repo.get(articleId) ?: return@launch
            if (!ClipStatus.isReadable(a.status)) {
                _state.update { it.copy(error = tr(R.string.not_downloaded_yet_dot)) }
                return@launch
            }
            val cur = _state.value.article?.id
            if (!repo.inQueue(a.id)) repo.playNext(a.id, cur)
            val html = repo.content(a.id) ?: return@launch
            val marked = withContext(Dispatchers.Default) { SentenceMarker.mark(html, settings.readCaptions, a.lang) }
            sentences = marked.sentences
            prefix = IntArray(sentences.size).also { arr ->
                var acc = 0; sentences.forEachIndexed { k, s -> arr[k] = acc; acc += s.length + 1 }
            }
            intro = if (settings.readIntro) {
                "${a.title}. ${a.siteName}."
            } else null
            val start = (fromSentence ?: a.listenSentence).coerceIn(0, maxOf(0, sentences.size - 1))
            _state.update {
                it.copy(article = a, sentenceIndex = start, sentenceCount = sentences.size,
                    currentText = sentences.getOrElse(start) { "" }, error = null, needsVoiceData = false,
                    isLoading = true, charsBefore = prefix.getOrElse(start) { 0 }, totalChars = sentences.sumOf { it.length + 1 })
            }
            if (a.readState == ReadState.UNREAD) repo.update(a.copy(readState = ReadState.READING))
            speakFrom(start, withIntro = fromSentence == null && a.listenSentence == 0)
        }
    }

    private fun speakFrom(index: Int, withIntro: Boolean = false) {
        val a = _state.value.article ?: return
        withTts {
            val t = tts ?: return@withTts
            val (ok, voice) = pickVoice(TextUtils.langKey(a.lang))
            if (!ok) {
                _state.update { it.copy(isLoading = false, isPlaying = false, needsVoiceData = true,
                    error = tr(R.string.no_offline_voice, langName(a.lang))) }
                notifyPlayer(); return@withTts
            }
            if (!requestFocus()) return@withTts
            session++
            t.stop()
            t.setSpeechRate(settings.speed)
            t.setPitch(settings.pitch)
            val sid = session
            if (sentences.isEmpty()) { onArticleFinished(); return@withTts }
            val params = Bundle()
            if (withIntro && intro != null) t.speak(intro, TextToSpeech.QUEUE_ADD, params, "$sid:-1")
            for (k in index until sentences.size) {
                t.speak(sentences[k], TextToSpeech.QUEUE_ADD, params, "$sid:$k")
            }
            registerNoisy()
            _state.update { it.copy(isPlaying = true, isLoading = false, voiceName = voice, sentenceIndex = index,
                currentText = sentences.getOrElse(index) { "" }, charsBefore = prefix.getOrElse(index) { 0 }) }
            notifyPlayer()
        }
    }

    fun pause() {
        if (!_state.value.isPlaying) return
        session++
        tts?.stop()
        resumeOnFocusGain = false
        _state.update { it.copy(isPlaying = false) }
        unregisterNoisy()
        abandonFocus()
        savePosition()
        notifyPlayer()
    }

    fun resume() {
        val st = _state.value
        val a = st.article ?: run {
            // Chưa có bài nào: phát bài đầu tiên trong hàng chờ
            st.queue.firstOrNull()?.let { play(it.id) }
            return
        }
        if (st.isPlaying) return
        if (sentences.isEmpty()) { play(a.id); return }
        speakFrom(st.sentenceIndex)
    }

    fun toggle() = if (_state.value.isPlaying) pause() else resume()

    fun seekSentence(delta: Int) = seekTo(_state.value.sentenceIndex + delta)

    fun seekTo(index: Int) {
        if (sentences.isEmpty()) return
        val i = index.coerceIn(0, sentences.size - 1)
        if (_state.value.isPlaying) speakFrom(i) else { setSentence(i); savePosition() }
    }

    /** Tua theo đoạn: nhảy tới câu đầu của đoạn kế tiếp/trước (ước lượng bằng câu kết thúc đoạn dài). */
    fun seekParagraph(forward: Boolean) = seekSentence(if (forward) 5 else -5)

    fun nextArticle() {
        val st = _state.value
        val q = st.queue
        val idx = q.indexOfFirst { it.id == st.article?.id }
        val next = q.getOrNull(idx + 1) ?: return
        play(next.id)
    }

    fun previousArticle() {
        val st = _state.value
        if (st.sentenceIndex > 2) { seekTo(0); return }
        val q = st.queue
        val idx = q.indexOfFirst { it.id == st.article?.id }
        val prev = q.getOrNull(idx - 1) ?: run { seekTo(0); return }
        play(prev.id)
    }

    fun playQueueIndex(index: Int, positionMs: Long?) {
        val q = _state.value.queue
        val target = q.getOrNull(index) ?: return
        if (target.id == _state.value.article?.id) {
            positionMs?.let { seekTo(sentenceForPosition(it)) }
        } else play(target.id)
    }

    fun sentenceForPosition(ms: Long): Int {
        val chars = (ms / 1000.0 * CHARS_PER_SEC * settings.speed).toInt()
        val i = prefix.indexOfLast { it <= chars }
        return if (i < 0) 0 else i
    }

    fun setSpeed(speed: Float) {
        scope.launch { settingsRepo.update { it.copy(speed = speed) } }
        settings = settings.copy(speed = speed)
        _state.update { it.copy(speed = speed) }
        if (_state.value.isPlaying) speakFrom(_state.value.sentenceIndex)
    }

    fun setPitch(pitch: Float) {
        scope.launch { settingsRepo.update { it.copy(pitch = pitch) } }
        settings = settings.copy(pitch = pitch)
        if (_state.value.isPlaying) speakFrom(_state.value.sentenceIndex)
    }

    fun setVoice(rawLang: String, name: String) {
        val lang = TextUtils.langKey(rawLang)
        scope.launch { settingsRepo.update { it.copy(voices = it.voices + (lang to name)) } }
        settings = settings.copy(voices = settings.voices + (lang to name))
        if (_state.value.isPlaying) speakFrom(_state.value.sentenceIndex)
    }

    fun clearError() = _state.update { it.copy(error = null, needsVoiceData = false) }

    fun stop() {
        pause()
        sleepJob?.cancel()
        _state.update { it.copy(article = null, sleepEndsAt = null, sleepEndOfArticle = false) }
        sentences = emptyList()
        notifyPlayer()
    }

    // ---------- Hẹn giờ tắt (spec 2.3) ----------
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _state.update { it.copy(sleepEndsAt = null, sleepEndOfArticle = false) }; return
        }
        val end = System.currentTimeMillis() + minutes * 60_000L
        _state.update { it.copy(sleepEndsAt = end, sleepEndOfArticle = false) }
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            pause()
            _state.update { it.copy(sleepEndsAt = null) }
        }
    }

    fun setSleepEndOfArticle() {
        sleepJob?.cancel()
        _state.update { it.copy(sleepEndsAt = null, sleepEndOfArticle = true) }
    }

    // ---------- Hết bài ----------
    private fun onArticleFinished() {
        val st = _state.value
        val a = st.article ?: return
        val q = st.queue
        val idx = q.indexOfFirst { it.id == a.id }
        val next = q.getOrNull(idx + 1)
        scope.launch {
            repo.get(a.id)?.let { fresh ->
                repo.update(fresh.copy(listenSentence = 0, readState = ReadState.READ,
                    archived = fresh.archived || settings.archiveAfterListen))
            }
            repo.dequeue(a.id)
        }
        if (st.sleepEndOfArticle) {
            _state.update { it.copy(isPlaying = false, sleepEndOfArticle = false) }
            unregisterNoisy(); abandonFocus(); notifyPlayer(); return
        }
        if (settings.autoNext && next != null) {
            // Âm báo ngắn giữa hai bài: một khoảng lặng nhỏ
            tts?.playSilentUtterance(600, TextToSpeech.QUEUE_FLUSH, "gap")
            play(next.id)
        } else {
            _state.update { it.copy(isPlaying = false, sentenceIndex = 0) }
            unregisterNoisy(); abandonFocus(); notifyPlayer()
        }
    }

    private fun savePosition() {
        val st = _state.value
        val a = st.article ?: return
        val i = st.sentenceIndex
        scope.launch { repo.get(a.id)?.let { if (it.listenSentence != i) repo.update(it.copy(listenSentence = i)) } }
    }

    private fun notifyPlayer() { player?.refresh() }

    private fun langName(l: String?) = com.clippercast.app.ui.langDisplayName(TextUtils.langKey(l))

    // ---------- Audio focus & tai nghe ----------
    private val audioManager by lazy { app.getSystemService(AudioManager::class.java) }
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        main.post {
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS -> pause()
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    val was = _state.value.isPlaying
                    pause()
                    resumeOnFocusGain = was
                }
                AudioManager.AUDIOFOCUS_GAIN -> if (resumeOnFocusGain) { resumeOnFocusGain = false; resume() }
            }
        }
    }
    private val focusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener(focusListener, main)
            .setWillPauseWhenDucked(true)
            .build()
    }
    private fun requestFocus(): Boolean =
        audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    private fun abandonFocus() { audioManager.abandonAudioFocusRequest(focusRequest) }

    private var noisyRegistered = false
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause()
        }
    }
    private fun registerNoisy() {
        if (noisyRegistered) return
        ContextCompat.registerReceiver(app, noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED)
        noisyRegistered = true
    }
    private fun unregisterNoisy() {
        if (!noisyRegistered) return
        runCatching { app.unregisterReceiver(noisyReceiver) }
        noisyRegistered = false
    }

    @Suppress("unused") private val sdk = Build.VERSION.SDK_INT
}
