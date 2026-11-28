package dao3.pickcat.core.ui

import android.text.Html
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
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
        // Trim without rebuilding spans: rebuilding from raw ranges after trim
        // can leave spans beyond the new text length.
        val raw = annotated.text
        val start = raw.indexOfFirst { !it.isWhitespace() }
        if (start < 0) {
            AnnotatedString("")
        } else {
            val end = raw.indexOfLast { !it.isWhitespace() } + 1
            annotated.subSequence(start, end)
        }
    }
}

/**
 * Turns the `URL` string annotations produced by [htmlToAnnotated] into real
 * Compose links, so tapping a link opens it instead of only being selectable.
 */
fun linkifyAnnotated(
    source: AnnotatedString,
    linkColor: Color,
    onClick: (String) -> Unit,
): AnnotatedString {
    if (source.isEmpty()) return source
    val urls = source.getStringAnnotations("URL", 0, source.length).toList()
    if (urls.isEmpty()) return source
    return buildAnnotatedString {
        append(source)
        urls.forEach { annotation ->
            val url = annotation.item
            val start = annotation.start.coerceIn(0, length)
            val end = annotation.end.coerceIn(start, length)
            if (start >= end) return@forEach
            addStyle(
                SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                start,
                end,
            )
            addLink(
                LinkAnnotation.Clickable(
                    tag = url,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ),
                    linkInteractionListener = { onClick(url) },
                ),
                start,
                end,
            )
        }
    }
}

/** PickCat mentions are `@<user uuid>` or `@{<user uuid>}`; plain `@昵称` is also highlighted. */
private val MENTION_PATTERN = Regex(
    """(?<![0-9A-Za-z_@])@(\{[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\}|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}|[\w\u4e00-\u9fa5-]{1,32})""",
)

internal fun mentionTargetOrNull(raw: String): String? {
    val value = raw.trim().removeSurrounding("{", "}")
    if (value.isBlank()) return null
    return value
}

internal fun isUserIdMention(value: String): Boolean =
    Regex("""^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$""")
        .matches(value.trim().removeSurrounding("{", "}"))

/**
 * Highlights `@用户ID` mentions (and legacy `@昵称`) and makes them tappable.
 * Mentions already inside a URL range are skipped so links are not touched.
 */
fun mentionifyAnnotated(
    source: AnnotatedString,
    mentionColor: Color,
    fontWeight: FontWeight,
    onClick: ((String) -> Unit)?,
): AnnotatedString {
    if (source.isEmpty()) return source
    val urlRanges = source.getStringAnnotations("URL", 0, source.length)
        .map { it.start..it.end }
    val matches = MENTION_PATTERN.findAll(source.text).toList()
    if (matches.isEmpty()) return source
    return buildAnnotatedString {
        append(source)
        matches.forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            if (urlRanges.any { overlap -> start < overlap.last && end > overlap.first }) {
                return@forEach
            }
            addStyle(
                SpanStyle(color = mentionColor, fontWeight = fontWeight),
                start,
                end,
            )
            val target = mentionTargetOrNull(match.groupValues[1]) ?: return@forEach
            if (onClick != null) {
                addLink(
                    LinkAnnotation.Clickable(
                        tag = "mention:$target",
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = mentionColor,
                                fontWeight = fontWeight,
                                textDecoration = TextDecoration.None,
                            ),
                        ),
                        linkInteractionListener = { onClick(target) },
                    ),
                    start,
                    end,
                )
            }
        }
    }
}

fun extractImageUrls(html: String?): List<String> {    if (html.isNullOrBlank()) return emptyList()
    return IMAGE_PATTERN.findAll(html)
        .mapNotNull { it.groupValues.getOrNull(1) }
        .mapNotNull { absoluteUrl(it) }
        .distinct()
        .toList()
}

sealed interface RichPart {
    data class Text(val annotated: AnnotatedString) : RichPart
    data class Image(val url: String, val emoji: Boolean) : RichPart
}

private val IMG_TAG = Regex("""(?is)<img\b[^>]*>""")
private val IMG_SRC = Regex("""src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
private val IMG_ALT = Regex("""alt\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
private val IMG_CLASS = Regex("""class\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)

/**
 * Splits cooked HTML into ordered text and image parts. Regular post images are
 * kept where they appear in the document; emoji images are marked separately so
 * the renderer can keep them small.
 */
fun parseRichParts(html: String?): List<RichPart> {
    if (html.isNullOrBlank()) return emptyList()
    val parts = mutableListOf<RichPart>()
    var cursor = 0

    fun addText(chunk: String) {
        val annotated = htmlToAnnotated(chunk)
        if (annotated.text.isNotBlank()) parts += RichPart.Text(annotated)
    }

    for (match in IMG_TAG.findAll(html)) {
        if (match.range.first > cursor) {
            addText(html.substring(cursor, match.range.first))
        }
        val tag = match.value
        val src = IMG_SRC.find(tag)?.groupValues?.getOrNull(1)
            ?: continue
        if (src.isBlank()) continue
        val alt = IMG_ALT.find(tag)?.groupValues?.getOrNull(1).orEmpty()
        val className = IMG_CLASS.find(tag)?.groupValues?.getOrNull(1).orEmpty()
        val emoji = src.contains("/emojis/") ||
            alt.startsWith("[emoji:") ||
            className.contains("markdown-emoji")
        val url = absoluteUrl(src) ?: continue
        parts += RichPart.Image(url, emoji)
        cursor = match.range.last + 1
    }
    if (cursor < html.length) addText(html.substring(cursor))
    return parts
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
    result = result.replace(
        Regex("!\\[([0-9a-fA-F-]{36})]"),
    ) { match ->
        val id = match.groupValues[1]
        "<img src=\"https://pickcat.dao3.fun/api/v1/files/$id\" />"
    }
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
