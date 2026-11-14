package dao3.pickcat.core.ui

import android.text.Html
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

private val TAG_PATTERN = Regex("(?s)<[^>]+>")
private val IMAGE_PATTERN = Regex("""(?is)<img[^>]+src\s*=\s*["']([^"']+)["']""")
private val LINK_PATTERN = Regex("""(?is)<a[^>]+href\s*=\s*["']([^"']+)["'][^>]*>""")
private val STYLE_BLOCK = Regex("(?is)<(script|style)[^>]*>.*?</\\1>")

/** Native rendering: no WebView is involved in forum post bodies. */
fun htmlToAnnotated(html: String?): AnnotatedString {
    if (html.isNullOrBlank()) return AnnotatedString("")
    val source = html.replace(STYLE_BLOCK, "")
    return buildAnnotatedString {
        var index = 0
        val styleStack = ArrayDeque<String>()
        val annotationStack = ArrayDeque<String>()
        fun appendRaw(raw: String) {
            if (raw.isEmpty()) return
            append(decodeEntities(raw))
        }

        while (index < source.length) {
            val open = source.indexOf('<', index)
            if (open == -1) {
                appendRaw(source.substring(index))
                break
            }
            if (open > index) appendRaw(source.substring(index, open))
            val close = source.indexOf('>', open)
            if (close == -1) {
                appendRaw(source.substring(open))
                break
            }
            val rawTag = source.substring(open + 1, close).trim()
            val isClosing = rawTag.startsWith("/")
            val name = rawTag
                .removePrefix("/")
                .trim()
                .substringBefore(' ')
                .substringBefore('\t')
                .lowercase()
            when {
                name == "br" -> append('\n')
                name == "img" -> {
                    val src = Regex("""src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                        .find(rawTag)?.groupValues?.getOrNull(1)
                    if (!src.isNullOrBlank()) append("\n[图片]\n")
                }
                name == "p" || name == "div" || name == "h1" || name == "h2" ||
                    name == "h3" || name == "h4" || name == "blockquote" || name == "pre" -> {
                    if (!isClosing) {
                        if (name == "blockquote") append("▎ ")
                        if (name == "h1" || name == "h2" || name == "h3") {
                            pushStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = when (name) {
                                        "h1" -> 22.sp
                                        "h2" -> 19.sp
                                        else -> 17.sp
                                    },
                                ),
                            )
                            styleStack.addLast("heading")
                        }
                    } else {
                        if (styleStack.lastOrNull() == "heading") {
                            pop()
                            styleStack.removeLast()
                        }
                        append('\n')
                        if (name != "blockquote") append('\n')
                    }
                }
                name == "li" -> {
                    if (!isClosing) append("• ") else append('\n')
                }
                name == "strong" || name == "b" -> {
                    if (isClosing) {
                        if (styleStack.lastOrNull() == "bold") { pop(); styleStack.removeLast() }
                    } else {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        styleStack.addLast("bold")
                    }
                }
                name == "em" || name == "i" -> {
                    if (isClosing) {
                        if (styleStack.lastOrNull() == "italic") { pop(); styleStack.removeLast() }
                    } else {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        styleStack.addLast("italic")
                    }
                }
                name == "s" || name == "del" || name == "strike" -> {
                    if (isClosing) {
                        if (styleStack.lastOrNull() == "strike") { pop(); styleStack.removeLast() }
                    } else {
                        pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                        styleStack.addLast("strike")
                    }
                }
                name == "code" -> {
                    if (isClosing) {
                        if (styleStack.lastOrNull() == "code") { pop(); styleStack.removeLast() }
                    } else {
                        pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))
                        styleStack.addLast("code")
                    }
                }
                name == "a" -> {
                    if (isClosing) {
                        if (styleStack.lastOrNull() == "link") { pop(); styleStack.removeLast() }
                        if (annotationStack.isNotEmpty()) {
                            annotationStack.removeLast()
                            pop()
                        }
                    } else {
                        val href = Regex("""href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                            .find(rawTag)?.groupValues?.getOrNull(1)
                        if (!href.isNullOrBlank()) {
                            pushStringAnnotation("URL", href)
                            annotationStack.addLast(href)
                        }
                        pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                        styleStack.addLast("link")
                    }
                }
            }
            index = close + 1
        }
        // Close any styles left open by malformed markup.
        while (styleStack.isNotEmpty()) {
            pop()
            styleStack.removeLast()
            if (annotationStack.isNotEmpty()) { annotationStack.removeLast(); pop() }
        }
    }.let { annotated ->
        // Collapse runs of blank lines produced by block tags.
        val text = annotated.text.replace(Regex("[ \\t]+\\n"), "\n").replace(Regex("\\n{3,}"), "\n\n").trim()
        AnnotatedString(text, annotated.spanStyles, annotated.paragraphStyles)
    }
}

fun extractImageUrls(html: String?): List<String> {
    if (html.isNullOrBlank()) return emptyList()
    return IMAGE_PATTERN.findAll(html)
        .mapNotNull { it.groupValues.getOrNull(1) }
        .mapNotNull { absoluteUrl(it) }
        .distinct()
        .toList()
}

fun plainTextFromHtml(html: String?): String {
    if (html.isNullOrBlank()) return ""
    val withoutImages = html.replace(IMAGE_PATTERN, " ")
    return cleanDisplayText(
        decodeEntities(withoutImages.replace(TAG_PATTERN, " "))
            .replace(Regex("\\s+"), " ")
            .trim(),
    )
}

private fun decodeEntities(raw: String): String {
    if (raw.isEmpty()) return raw
    return runCatching {
        Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString()
    }.getOrDefault(raw)
}

fun markdownToBasicHtml(markdown: String): String {
    if (markdown.isBlank()) return ""
    val lines = markdown.replace("\r\n", "\n").split('\n')
    val out = StringBuilder()
    var inCode = false
    for (raw in lines) {
        val line = raw
        if (line.trimStart().startsWith("```")) {
            inCode = !inCode
            out.append(if (inCode) "<pre><code>" else "</code></pre>")
            continue
        }
        if (inCode) {
            out.append(escapeHtml(line)).append('\n')
            continue
        }
        when {
            line.startsWith("### ") -> out.append("<h3>").append(inlineMarkdown(line.removePrefix("### "))).append("</h3>")
            line.startsWith("## ") -> out.append("<h2>").append(inlineMarkdown(line.removePrefix("## "))).append("</h2>")
            line.startsWith("# ") -> out.append("<h1>").append(inlineMarkdown(line.removePrefix("# "))).append("</h1>")
            line.startsWith("> ") -> out.append("<blockquote>").append(inlineMarkdown(line.removePrefix("> "))).append("</blockquote>")
            line.startsWith("- ") || line.startsWith("* ") -> out.append("<li>").append(inlineMarkdown(line.drop(2))).append("</li>")
            line.matches(Regex("""\d+\.\s+.*""")) -> out.append("<li>").append(inlineMarkdown(line.substringAfter(" "))).append("</li>")
            line.trim() == "---" -> out.append("<p>────────</p>")
            line.isBlank() -> out.append("<p></p>")
            else -> out.append("<p>").append(inlineMarkdown(line)).append("</p>")
        }
        out.append('\n')
    }
    if (inCode) out.append("</code></pre>")
    return out.toString()
}

private fun inlineMarkdown(input: String): String {
    var result = escapeHtml(input)
    result = result.replace(Regex("!\\[([^]]*)]\\(([^)]+)\\)"), "[图片：$1]")
    result = result.replace(Regex("\\[([^]]+)]\\(([^)]+)\\)"), "<a href=\"$2\">$1</a>")
    result = result.replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
    result = result.replace(Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)"), "<em>$1</em>")
    result = result.replace(Regex("`([^`]+)`"), "<code>$1</code>")
    result = result.replace(Regex("~~(.+?)~~"), "<s>$1</s>")
    return result
}

private fun escapeHtml(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
