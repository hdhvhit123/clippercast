package com.clippercast.app.clip

import android.net.Uri

/** Chuẩn hóa link trước khi lưu (spec 2.1.1). */
object UrlNormalizer {
    private val urlRegex = Regex("""https?://[^\s<>"'）)\]]+""", RegexOption.IGNORE_CASE)
    private val trackingParams = setOf(
        "fbclid", "gclid", "dclid", "msclkid", "mc_cid", "mc_eid", "igshid", "ref_src", "ref_url",
        "_hsenc", "_hsmi", "yclid", "spm", "share_id", "si", "s_cid", "cmpid", "ocid",
    )

    fun extractUrl(text: String?): String? =
        text?.let { urlRegex.find(it)?.value?.trimEnd('.', ',', ';', ':', '!', '?') }

    fun normalize(raw: String): String {
        var url = raw.trim()
        // Link AMP của Google: https://www.google.com/amp/s/example.com/bai-viet
        Regex("""^https?://(www\.)?google\.[a-z.]+/amp/(s/)?(.+)$""", RegexOption.IGNORE_CASE).find(url)?.let { m ->
            val secure = m.groupValues[2].isNotEmpty()
            url = (if (secure) "https://" else "http://") + m.groupValues[3]
        }
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return url
        // Link chuyển hướng của Google: google.com/url?q=...
        if (uri.host?.contains("google.") == true && uri.path == "/url") {
            (uri.getQueryParameter("q") ?: uri.getQueryParameter("url"))?.let { return normalize(it) }
        }
        val builder = uri.buildUpon().clearQuery().fragment(null)
        uri.queryParameterNames.sorted().forEach { name ->
            val lower = name.lowercase()
            if (lower.startsWith("utm_") || lower in trackingParams) return@forEach
            uri.getQueryParameters(name).forEach { builder.appendQueryParameter(name, it) }
        }
        var out = builder.build().toString()
        out = out.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
        if (out.endsWith("/") && (uri.path?.length ?: 0) > 1) out = out.dropLast(1)
        return out
    }

    fun host(url: String): String =
        runCatching { Uri.parse(url).host.orEmpty().removePrefix("www.").removePrefix("m.") }.getOrDefault("")

    /** Những trang không phải bài viết: chỉ lưu link (spec 2.1.2). */
    fun isLinkOnlyHost(url: String): Boolean {
        val h = host(url)
        return listOf("youtube.com", "youtu.be", "tiktok.com", "x.com", "twitter.com", "instagram.com",
            "facebook.com", "fb.watch", "threads.net", "vimeo.com", "open.spotify.com", "maps.google.com")
            .any { h == it || h.endsWith(".$it") }
    }

    /** Tên nguồn dễ đọc từ tên miền: "vnexpress.net" -> "VnExpress" nếu không có og:site_name. */
    fun prettySite(url: String): String {
        val h = host(url)
        val base = h.split('.').let { parts -> if (parts.size >= 2) parts[parts.size - 2] else h }
        return base.replaceFirstChar { it.uppercase() }
    }
}
