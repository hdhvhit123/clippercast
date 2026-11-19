package com.clippercast.app.reader

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Chia nội dung bài thành câu và bọc mỗi câu bằng <span class="s" data-i="n">.
 * Dùng chung cho trình đọc (highlight câu đang đọc) và trình phát TTS (đọc từng câu),
 * nên chỉ số câu luôn khớp nhau (spec 2.3).
 */
object SentenceMarker {
    data class Marked(val html: String, val sentences: List<String>)

    private const val BLOCKS = "p, h1, h2, h3, h4, h5, h6, li, blockquote, figcaption, td, th, dt, dd"
    private val sentenceEnd = Regex("""(?<=[.!?…।؟])["”’»)\]]*\s+|(?<=[。！？])[”’」』）)]*\s*""")
    private val urlRegex = Regex("""https?://\S+|www\.\S+""")

    fun mark(html: String, readCaptions: Boolean, lang: String?): Marked {
        val doc = Jsoup.parseBodyFragment(html)
        val sentences = mutableListOf<String>()
        val blocks = doc.body().select(BLOCKS).filter { el ->
            el.select(BLOCKS).size == 1 && el.parents().none { it.tagName() == "pre" || it.tagName() == "code" }
        }
        for (block in blocks) {
            if (block.tagName() == "figcaption" && !readCaptions) continue
            val textNodes = mutableListOf<TextNode>()
            collect(block, textNodes)
            if (textNodes.isEmpty()) continue
            val full = StringBuilder()
            val offsets = textNodes.map { tn -> full.length.also { full.append(tn.wholeText) } }
            val ranges = split(full.toString())
            if (ranges.isEmpty()) continue
            val base = sentences.size
            ranges.forEach { (s, e) -> sentences.add(speakable(full.substring(s, e), lang)) }

            textNodes.forEachIndexed { ti, tn ->
                val start = offsets[ti]
                val text = tn.wholeText
                val end = start + text.length
                val pieces = mutableListOf<Pair<String, Int?>>()
                var pos = start
                for ((ri, r) in ranges.withIndex()) {
                    val (rs, re) = r
                    if (re <= pos || rs >= end) continue
                    if (rs > pos) { pieces.add(text.substring(pos - start, rs - start) to null); pos = rs }
                    val pe = minOf(re, end)
                    pieces.add(text.substring(pos - start, pe - start) to (base + ri))
                    pos = pe
                }
                if (pos < end) pieces.add(text.substring(pos - start) to null)
                if (pieces.isEmpty()) return@forEachIndexed
                var anchor: Node = tn
                for ((piece, idx) in pieces) {
                    val node: Node = if (idx == null) TextNode(piece) else
                        Element("span").addClass("s").attr("data-i", idx.toString()).appendChild(TextNode(piece))
                    anchor.after(node)
                    anchor = node
                }
                tn.remove()
            }
        }
        return Marked(doc.body().html(), sentences)
    }

    private fun collect(el: Element, out: MutableList<TextNode>) {
        for (child in el.childNodes()) {
            when (child) {
                is TextNode -> out.add(child)
                is Element -> if (child.tagName() != "pre" && child.tagName() != "code" && child.tagName() != "img") collect(child, out)
            }
        }
    }

    /** Trả về các khoảng [bắt đầu, kết thúc) của từng câu, đã bỏ khoảng trắng hai đầu. */
    private fun split(text: String): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()
        var start = 0
        fun add(s: Int, e: Int) {
            var a = s; var b = e
            while (a < b && text[a].isWhitespace()) a++
            while (b > a && text[b - 1].isWhitespace()) b--
            if (b > a && text.substring(a, b).any { it.isLetterOrDigit() }) {
                // Câu quá dài: chia tiếp ở dấu chấm phẩy/phẩy để highlight và tua mượt hơn
                if (b - a > 400) {
                    var cs = a
                    var i = a + 200
                    while (i < b) {
                        val c = text.indexOfAny(charArrayOf(';', ',', ':'), i)
                        if (c < 0 || c >= b - 50) break
                        result.add(cs to c + 1); cs = c + 1
                        while (cs < b && text[cs].isWhitespace()) cs++
                        i = cs + 200
                    }
                    result.add(cs to b)
                } else result.add(a to b)
            }
        }
        for (m in sentenceEnd.findAll(text)) {
            add(start, m.range.first)
            start = m.range.last + 1
        }
        add(start, text.length)
        return result
    }

    private fun speakable(s: String, lang: String?): String {
        return urlRegex.replace(s, "").replace(Regex("\\s+"), " ").trim()
    }
}
