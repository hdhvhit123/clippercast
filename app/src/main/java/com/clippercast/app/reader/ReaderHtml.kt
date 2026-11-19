package com.clippercast.app.reader

import android.content.Context
import com.clippercast.app.data.AppSettings
import com.clippercast.app.data.Article
import com.clippercast.app.data.ReaderTheme
import com.clippercast.app.data.TextUtils
import org.json.JSONObject
import org.jsoup.nodes.Entities

/** Dựng trang HTML cho trình đọc: CSS theo cài đặt, font nhúng sẵn, JS điều khiển. */
object ReaderHtml {
    @Volatile private var fontsCss: String? = null

    private fun fonts(context: Context): String = fontsCss ?: context.assets.open("fonts.css").bufferedReader()
        .use { it.readText() }.replace("url(fonts/", "url(file:///android_asset/fonts/").also { fontsCss = it }

    fun hex(c: Long) = String.format("#%06X", c and 0xFFFFFF)

    fun styleJson(s: AppSettings, theme: ReaderTheme): String = JSONObject().apply {
        put("bg", hex(theme.bg)); put("fg", hex(theme.fg)); put("accent", hex(theme.accent))
        put("dark", theme.dark); put("hl", hex(theme.accent) + "38"); put("sel", hex(theme.accent) + "59")
        put("font", s.font.css); put("size", s.fontSize); put("lh", s.lineHeight.toDouble())
        put("margin", s.margin); put("justify", s.justify); put("indent", s.indent)
    }.toString()

    fun build(context: Context, a: Article, bodyHtml: String, s: AppSettings, theme: ReaderTheme): String {
        val meta = buildList {
            add(Entities.escape(a.siteName))
            if (a.wordCount > 0) add("~${TextUtils.readMinutes(a.wordCount)} phút đọc")
        }.joinToString(" · ")
        val byline = listOfNotNull(a.author?.takeIf { it.isNotBlank() }, a.publishedAt?.take(10)).joinToString(" · ")
        val original = if (a.url.isNotBlank())
            """<footer><a class="orig" href="${Entities.escape(a.url)}">Xem bản gốc ↗</a></footer>""" else ""
        return """<!doctype html>
<html lang="${a.lang ?: "vi"}"><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src file: data:; style-src 'unsafe-inline' file:; font-src file:; script-src 'unsafe-inline'">
<style>
${fonts(context)}
:root { --bg:#fff; --fg:#111; --accent:#c0441e; --font:serif; --size:19px; --lh:1.6; --margin:20px; --hl:#c0441e38; --sel:#c0441e59; }
html,body { margin:0; padding:0; background:var(--bg); color:var(--fg); }
body { font-family:var(--font); font-size:var(--size); line-height:var(--lh); padding:72px var(--margin) 160px;
  -webkit-text-size-adjust:100%; word-wrap:break-word; overflow-wrap:anywhere; }
.wrap { max-width:38em; margin:0 auto; }
header .site { font-family:'Inter',sans-serif; font-size:.72em; letter-spacing:.04em; text-transform:uppercase; opacity:.65; }
header h1 { font-size:1.55em; line-height:1.25; margin:.35em 0 .3em; }
header .by { font-family:'Inter',sans-serif; font-size:.75em; opacity:.65; margin-bottom:1.6em; }
article p { margin:0 0 1em; }
body.justify article p { text-align:justify; hyphens:auto; }
body.indent article p + p { text-indent:1.5em; }
article h2, article h3, article h4 { line-height:1.3; margin:1.4em 0 .5em; }
article img { max-width:100%; height:auto; display:block; margin:1em auto; border-radius:6px; }
article figure { margin:1.2em 0; }
article figcaption { font-size:.8em; opacity:.7; text-align:center; }
article blockquote { margin:1em 0; padding:.2em 1em; border-left:3px solid var(--accent); opacity:.9; }
article pre { overflow-x:auto; padding:.8em; background:rgba(127,127,127,.12); border-radius:6px; font-size:.8em; line-height:1.45; }
article code { font-family:monospace; font-size:.9em; }
article table { display:block; overflow-x:auto; border-collapse:collapse; font-size:.85em; }
article td, article th { border:1px solid rgba(127,127,127,.35); padding:.3em .5em; }
a { color:var(--accent); }
.s.cur { background:var(--hl); border-radius:3px; }
footer { margin-top:2.5em; font-family:'Inter',sans-serif; font-size:.85em; }
::selection { background:var(--sel); }
</style></head>
<body><div class="wrap">
<header><div class="site">$meta</div><h1>${Entities.escape(a.title)}</h1>${if (byline.isNotBlank()) "<div class=\"by\">${Entities.escape(byline)}</div>" else ""}</header>
<article>$bodyHtml</article>
$original
</div>
<script>
(function(){
  var cur = -1, follow = true, lastSent = 0;
  window.applyStyle = function(s){
    var r = document.documentElement.style;
    r.setProperty('--bg', s.bg); r.setProperty('--fg', s.fg); r.setProperty('--accent', s.accent);
    r.setProperty('--font', s.font); r.setProperty('--size', s.size + 'px'); r.setProperty('--lh', s.lh);
    r.setProperty('--margin', s.margin + 'px'); r.setProperty('--hl', s.hl); r.setProperty('--sel', s.sel);
    document.body.classList.toggle('justify', !!s.justify);
    document.body.classList.toggle('indent', !!s.indent);
    document.documentElement.style.colorScheme = s.dark ? 'dark' : 'light';
  };
  window.highlight = function(i, scroll){
    document.querySelectorAll('.s.cur').forEach(function(e){ e.classList.remove('cur'); });
    cur = i;
    if (i < 0) return;
    var els = document.querySelectorAll('.s[data-i="' + i + '"]');
    els.forEach(function(e){ e.classList.add('cur'); });
    if (scroll && follow && els.length) {
      var r = els[0].getBoundingClientRect();
      if (r.top < 80 || r.bottom > window.innerHeight - 140) {
        window.scrollTo({top: window.scrollY + r.top - window.innerHeight * 0.3, behavior: 'smooth'});
      }
    }
  };
  window.setFollow = function(f){ follow = f; };
  window.scrollToProgress = function(p){
    var h = document.documentElement.scrollHeight - window.innerHeight;
    window.scrollTo(0, Math.max(0, h * p));
  };
  window.scrollToSentence = function(i){
    var e = document.querySelector('.s[data-i="' + i + '"]');
    if (e) window.scrollTo(0, window.scrollY + e.getBoundingClientRect().top - window.innerHeight * 0.3);
  };
  window.firstVisibleSentence = function(){
    var all = document.querySelectorAll('.s');
    for (var k = 0; k < all.length; k++) {
      var r = all[k].getBoundingClientRect();
      if (r.bottom > 70) return parseInt(all[k].getAttribute('data-i'));
    }
    return 0;
  };
  var pending = false;
  window.addEventListener('scroll', function(){
    if (pending) return; pending = true;
    setTimeout(function(){
      pending = false;
      var h = document.documentElement.scrollHeight - window.innerHeight;
      Android.onScroll(h > 0 ? window.scrollY / h : 1);
    }, 400);
  }, {passive:true});
  document.addEventListener('click', function(ev){
    if (ev.target.closest('a')) return;
    var sel = window.getSelection();
    if (sel && sel.toString().length > 0) return;
    var s = ev.target.closest('.s');
    if (s && Android.isPlaying()) { Android.onSentenceTap(parseInt(s.getAttribute('data-i'))); return; }
    Android.onTap();
  });
})();
</script>
</body></html>"""
    }
}
