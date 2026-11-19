package com.clippercast.app.backup

import com.clippercast.app.data.Article
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Xuất bài sang Markdown có YAML front matter, tương thích Obsidian (spec 2.4). */
object MarkdownExporter {
    fun fileName(a: Article): String {
        val safe = a.title.replace(Regex("[\\\\/:*?\"<>|\\n\\r]+"), " ").trim().take(80).ifBlank { "bai-viet" }
        return "$safe.md"
    }

    fun toMarkdown(a: Article, html: String): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(a.savedAt))
        fun y(s: String?) = "\"" + (s ?: "").replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        val tags = a.tags.split('|').filter { it.isNotBlank() }
        val sb = StringBuilder()
        sb.append("---\n")
        sb.append("title: ${y(a.title)}\n")
        if (!a.author.isNullOrBlank()) sb.append("author: ${y(a.author)}\n")
        sb.append("source: ${y(a.siteName)}\n")
        if (a.url.isNotBlank()) sb.append("url: ${y(a.url)}\n")
        if (!a.publishedAt.isNullOrBlank()) sb.append("published: ${y(a.publishedAt)}\n")
        sb.append("saved: $date\n")
        if (tags.isNotEmpty()) sb.append("tags: [${tags.joinToString(", ") { y(it) }}]\n")
        sb.append("---\n\n# ${a.title}\n\n")
        val body = Jsoup.parseBodyFragment(html).body()
        body.select("span.s").unwrap()
        sb.append(blocks(body).trim()).append("\n")
        return sb.toString().replace(Regex("\n{3,}"), "\n\n")
    }

    private fun blocks(el: Element): String {
        val sb = StringBuilder()
        for (n in el.childNodes()) sb.append(block(n))
        return sb.toString()
    }

    private fun block(n: Node): String {
        if (n is TextNode) return n.text().takeIf { it.isNotBlank() }?.let { "$it\n\n" } ?: ""
        if (n !is Element) return ""
        return when (n.tagName()) {
            "h1", "h2", "h3", "h4", "h5", "h6" -> "#".repeat(n.tagName()[1].digitToInt().coerceAtLeast(2)) + " " + inline(n) + "\n\n"
            "p" -> inline(n) + "\n\n"
            "blockquote" -> blocks(n).trim().lines().joinToString("\n") { "> $it" } + "\n\n"
            "pre" -> "```\n" + n.wholeText().trimEnd() + "\n```\n\n"
            "ul" -> n.children().filter { it.tagName() == "li" }.joinToString("") { "- " + inline(it) + "\n" } + "\n"
            "ol" -> n.children().filter { it.tagName() == "li" }.mapIndexed { i, li -> "${i + 1}. " + inline(li) + "\n" }.joinToString("") + "\n"
            "img" -> "![${n.attr("alt")}](${n.attr("src")})\n\n"
            "figure" -> blocks(n)
            "figcaption" -> "*" + inline(n) + "*\n\n"
            "hr" -> "---\n\n"
            "table" -> table(n)
            "br" -> "\n"
            else -> if (n.children().any { it.isBlock }) blocks(n) else inline(n).takeIf { it.isNotBlank() }?.let { "$it\n\n" } ?: ""
        }
    }

    private fun table(t: Element): String {
        val rows = t.select("tr").map { r -> r.select("th, td").map { inline(it).replace("|", "\\|") } }
        if (rows.isEmpty()) return ""
        val w = rows.maxOf { it.size }
        val sb = StringBuilder()
        rows.forEachIndexed { i, r ->
            sb.append("| ").append((r + List(w - r.size) { "" }).joinToString(" | ")).append(" |\n")
            if (i == 0) sb.append("|").append(" --- |".repeat(w)).append("\n")
        }
        return sb.append("\n").toString()
    }

    private fun inline(el: Element): String {
        val sb = StringBuilder()
        for (n in el.childNodes()) {
            when (n) {
                is TextNode -> sb.append(n.text())
                is Element -> when (n.tagName()) {
                    "strong", "b" -> sb.append("**").append(inline(n).trim()).append("**")
                    "em", "i" -> sb.append("*").append(inline(n).trim()).append("*")
                    "code" -> sb.append("`").append(n.text()).append("`")
                    "a" -> sb.append("[").append(inline(n).trim()).append("](").append(n.attr("href")).append(")")
                    "img" -> sb.append("![").append(n.attr("alt")).append("](").append(n.attr("src")).append(")")
                    "br" -> sb.append("  \n")
                    else -> sb.append(inline(n))
                }
            }
        }
        return sb.toString().trim()
    }
}
