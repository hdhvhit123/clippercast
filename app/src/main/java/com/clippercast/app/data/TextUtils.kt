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

    /** Đoán ngôn ngữ đơn giản khi trang không khai báo lang. */
    fun guessLang(text: String): String {
        val sample = text.take(4000)
        if (sample.isEmpty()) return "en"
        val vi = sample.count { it in "ăâđêôơưĂÂĐÊÔƠƯ" || (it.code in 0x1EA0..0x1EF9) || it in "àáảãạèéẻẽẹìíỉĩịòóỏõọùúủũụỳýỷỹỵ" }
        return if (vi * 100 / sample.length >= 1) "vi" else "en"
    }

    fun readMinutes(words: Int, wpm: Int = 220) = maxOf(1, (words + wpm - 1) / wpm)

    /** Thời gian nghe ước tính: giọng đọc ở 1.0x khoảng 160 từ/phút. */
    fun listenMinutes(words: Int, speed: Float) = maxOf(1, (words / (160f * speed)).toInt())
}
