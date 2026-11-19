package com.clippercast.app.data

import java.text.Normalizer
import java.util.Locale

object TextUtils {
    private val marks = Regex("\\p{Mn}+")

    /** Bỏ dấu tiếng Việt và viết thường: "Kinh tế" -> "kinh te". */
    fun fold(s: String): String =
        marks.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "")
            .replace('đ', 'd').replace('Đ', 'D')
            .lowercase(Locale.ROOT)

    /** Biến chuỗi người dùng gõ thành biểu thức MATCH của FTS4 (mỗi từ là một tiền tố). */
    fun ftsQuery(input: String): String? {
        val tokens = fold(input).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ") { "$it*" }
    }

    fun wordCount(text: String): Int = text.split(Regex("\\s+")).count { it.isNotBlank() }

    /** Mã ngôn ngữ chuẩn hóa theo cách Android dùng (vd. "id" -> "in"), để so khớp với giọng TTS. */
    fun langKey(lang: String?): String =
        Locale(lang?.takeIf { it.isNotBlank() } ?: Locale.getDefault().language).language

    private val latinHints = linkedMapOf(
        "es" to setOf("el", "los", "las", "que", "del", "por", "para", "una", "con", "es", "se", "como", "pero", "más"),
        "pt" to setOf("o", "os", "que", "do", "da", "dos", "das", "não", "uma", "com", "para", "em", "é", "mais"),
        "fr" to setOf("le", "les", "des", "est", "une", "que", "dans", "pour", "pas", "qui", "sur", "avec", "au", "du"),
        "de" to setOf("der", "die", "das", "und", "ist", "nicht", "ein", "eine", "mit", "für", "auf", "den", "zu", "sich"),
        "id" to setOf("yang", "dan", "di", "ini", "itu", "dengan", "untuk", "tidak", "dari", "dalam", "akan", "pada", "juga", "ada"),
        "en" to setOf("the", "and", "of", "to", "is", "that", "in", "it", "for", "with", "was", "on", "are", "this"),
    )

    /** Đoán ngôn ngữ khi trang không khai báo lang: theo hệ chữ, rồi theo từ thông dụng với chữ Latinh. */
    fun guessLang(text: String): String {
        val sample = text.take(4000)
        if (sample.isBlank()) return Locale.getDefault().language
        var han = 0; var kana = 0; var hangul = 0; var thai = 0; var arabic = 0; var deva = 0; var cyr = 0; var vi = 0; var letters = 0
        for (c in sample) {
            if (!c.isLetter()) continue
            letters++
            when (c.code) {
                in 0x4E00..0x9FFF -> han++
                in 0x3040..0x30FF -> kana++
                in 0xAC00..0xD7AF, in 0x1100..0x11FF -> hangul++
                in 0x0E00..0x0E7F -> thai++
                in 0x0600..0x06FF -> arabic++
                in 0x0900..0x097F -> deva++
                in 0x0400..0x04FF -> cyr++
                in 0x1EA0..0x1EF9 -> vi++
                else -> if (c in "ăâđêôơưĂÂĐÊÔƠƯ") vi++
            }
        }
        if (letters == 0) return Locale.getDefault().language
        val pct = { n: Int -> n * 100 / letters }
        return when {
            pct(kana) >= 5 -> "ja"
            pct(hangul) >= 20 -> "ko"
            pct(han) >= 20 -> "zh"
            pct(thai) >= 20 -> "th"
            pct(arabic) >= 20 -> "ar"
            pct(deva) >= 20 -> "hi"
            pct(cyr) >= 20 -> "ru"
            pct(vi) >= 1 -> "vi"
            else -> {
                val words = sample.lowercase(Locale.ROOT).split(Regex("[^\\p{L}]+"))
                val best = latinHints.maxByOrNull { (_, set) -> words.count { it in set } }
                if (best == null || words.count { it in best.value } < 3) "en" else best.key
            }
        }
    }

    fun readMinutes(words: Int, wpm: Int = 220) = maxOf(1, (words + wpm - 1) / wpm)

    /** Thời gian nghe ước tính: giọng đọc ở 1.0x khoảng 160 từ/phút. */
    fun listenMinutes(words: Int, speed: Float) = maxOf(1, (words / (160f * speed)).toInt())
}
