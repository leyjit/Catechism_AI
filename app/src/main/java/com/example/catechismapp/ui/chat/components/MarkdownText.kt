package com.example.catechismapp.ui.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures

@Composable
fun MarkdownText(
    markdown: String,
    style: TextStyle,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    citationKeys: Set<String> = emptySet(),
    onOpenCitation: (String) -> Unit = {},
    onCopyRequested: (() -> Unit)? = null
) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val lines = markdown.lines()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        lines.forEach { line ->
            val trimmed = line.trim()
            val numberedListMatch = Regex("""^(\d+)\.\s+(.+)$""").find(trimmed)
            when {
                trimmed.isBlank() -> Spacer(modifier = Modifier.height(2.dp))
                trimmed.startsWith("#") -> {
                    val headingText = trimmed.dropWhile { it == '#' }.trim()
                    if (headingText.isNotBlank()) {
                        MarkdownInlineText(
                            text = headingText,
                            style = style.copy(fontWeight = FontWeight.Bold),
                            color = color,
                            linkColor = linkColor,
                            citationKeys = citationKeys,
                            onOpenUrl = uriHandler::openUri,
                            onOpenCitation = onOpenCitation,
                            onCopyRequested = onCopyRequested
                        )
                    }
                }
                numberedListMatch != null -> {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${numberedListMatch.groupValues[1]}.",
                            style = style,
                            color = color,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        MarkdownInlineText(
                            text = numberedListMatch.groupValues[2].trim(),
                            style = style,
                            color = color,
                            linkColor = linkColor,
                            citationKeys = citationKeys,
                            onOpenUrl = uriHandler::openUri,
                            onOpenCitation = onOpenCitation,
                            onCopyRequested = onCopyRequested,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "\u2022",
                            style = style,
                            color = color,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        MarkdownInlineText(
                            text = trimmed.drop(2).trim(),
                            style = style,
                            color = color,
                            linkColor = linkColor,
                            citationKeys = citationKeys,
                            onOpenUrl = uriHandler::openUri,
                            onOpenCitation = onOpenCitation,
                            onCopyRequested = onCopyRequested,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                else -> {
                    MarkdownInlineText(
                        text = line,
                        style = style,
                        color = color,
                        linkColor = linkColor,
                        citationKeys = citationKeys,
                        onOpenUrl = uriHandler::openUri,
                        onOpenCitation = onOpenCitation,
                        onCopyRequested = onCopyRequested
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownInlineText(
    text: String,
    style: TextStyle,
    color: androidx.compose.ui.graphics.Color,
    linkColor: androidx.compose.ui.graphics.Color,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    citationKeys: Set<String> = emptySet(),
    onOpenCitation: (String) -> Unit = {},
    onCopyRequested: (() -> Unit)? = null
) {
    val annotatedText = markdownInlineAnnotatedString(text, linkColor, citationKeys)
    var textLayoutResult by remember(text, annotatedText) { mutableStateOf<TextLayoutResult?>(null) }

    BasicText(
        text = annotatedText,
        style = style.copy(color = color),
        modifier = modifier.pointerInput(annotatedText, onOpenUrl, onOpenCitation, onCopyRequested) {
            detectTapGestures(
                onTap = { position: Offset ->
                    val layoutResult = textLayoutResult ?: return@detectTapGestures
                    val offset = layoutResult.getOffsetForPosition(position)
                    val urlAnnotation = annotatedText
                        .getStringAnnotations(tag = UrlAnnotationTag, start = offset, end = offset)
                        .firstOrNull()
                    if (urlAnnotation != null) {
                        onOpenUrl(urlAnnotation.item)
                    } else {
                        annotatedText
                            .getStringAnnotations(tag = CitationAnnotationTag, start = offset, end = offset)
                            .firstOrNull()
                            ?.let { onOpenCitation(it.item) }
                    }
                },
                onLongPress = {
                    onCopyRequested?.invoke()
                }
            )
        },
        onTextLayout = { textLayoutResult = it }
    )
}

private fun markdownInlineAnnotatedString(
    text: String,
    linkColor: androidx.compose.ui.graphics.Color,
    citationKeys: Set<String> = emptySet()
): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        val linkStart = text.indexOf('[', index).takeIf { it >= 0 } ?: Int.MAX_VALUE
        val boldStart = text.indexOf("**", index).takeIf { it >= 0 } ?: Int.MAX_VALUE
        val citationMatch = findEarliestCitation(text, index, citationKeys)
        val citationStart = citationMatch?.start ?: Int.MAX_VALUE
        val citationEnd = citationMatch?.endExclusive ?: Int.MAX_VALUE
        val nextStart = minOf(linkStart, boldStart, citationStart)

        if (nextStart == Int.MAX_VALUE) {
            append(text.substring(index))
            break
        }

        if (nextStart > index) {
            append(text.substring(index, nextStart))
        }

        when (nextStart) {
            citationStart -> {
                val start = length
                append(text.substring(citationStart, citationEnd))
                addStyle(
                    style = SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline
                    ),
                    start = start,
                    end = length
                )
                addStringAnnotation(
                    tag = CitationAnnotationTag,
                    annotation = text.substring(citationStart, citationEnd),
                    start = start,
                    end = length
                )
                index = citationEnd
            }
            linkStart -> {
                val labelEnd = text.indexOf(']', linkStart + 1)
                val urlStart = if (labelEnd >= 0) text.indexOf("(", labelEnd + 1) else -1
                val urlEnd = if (urlStart == labelEnd + 1) text.indexOf(')', urlStart + 1) else -1
                if (labelEnd > linkStart && urlEnd > urlStart) {
                    val label = text.substring(linkStart + 1, labelEnd)
                    val url = text.substring(urlStart + 1, urlEnd)
                    val start = length
                    append(label)
                    addStyle(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline
                        ),
                        start = start,
                        end = length
                    )
                    addStringAnnotation(
                        tag = UrlAnnotationTag,
                        annotation = url,
                        start = start,
                        end = length
                    )
                    index = urlEnd + 1
                } else {
                    append(text[linkStart])
                    index = linkStart + 1
                }
            }
            else -> {
                val boldEnd = text.indexOf("**", boldStart + 2)
                if (boldEnd > boldStart) {
                    val start = length
                    append(text.substring(boldStart + 2, boldEnd))
                    addStyle(
                        style = SpanStyle(fontWeight = FontWeight.Bold),
                        start = start,
                        end = length
                    )
                    index = boldEnd + 2
                } else {
                    append(text.substring(boldStart, boldStart + 2))
                    index = boldStart + 2
                }
            }
        }
    }
}

private fun findEarliestCitation(
    text: String,
    startIndex: Int,
    keys: Set<String>
): CitationMatch? {
    var earliest = Int.MAX_VALUE
    var match: CitationMatch? = null
    for (key in keys) {
        val i = text.indexOf(key, startIndex, ignoreCase = true)
        if (i in 0 until earliest && isBoundarySafe(text, i, key)) {
            val endExclusive = if (key.startsWith("ccc", ignoreCase = true)) {
                i + key.length
            } else {
                extendBibleRange(text, i + key.length)
            }
            earliest = i
            match = CitationMatch(start = i, key = key, endExclusive = endExclusive)
        }
    }
    return match
}

private fun isBoundarySafe(text: String, start: Int, key: String): Boolean {
    val before = start == 0 || !text[start - 1].isLetterOrDigit()
    val after = start + key.length >= text.length || !text[start + key.length].isLetterOrDigit()
    return before && after
}

private fun extendBibleRange(text: String, startAfterBaseKey: Int): Int {
    var end = startAfterBaseKey
    var cursor = end

    while (cursor < text.length && text[cursor].isWhitespace()) {
        cursor++
    }

    if (cursor < text.length && (text[cursor] == '-' || text[cursor] == '–' || text[cursor] == '—')) {
        cursor++
        while (cursor < text.length && text[cursor].isWhitespace()) {
            cursor++
        }
        val digitsStart = cursor
        while (cursor < text.length && text[cursor].isDigit()) {
            cursor++
        }
        if (cursor > digitsStart) {
            end = cursor
        }
    }

    return end
}

private data class CitationMatch(
    val start: Int,
    val key: String,
    val endExclusive: Int
)

private const val UrlAnnotationTag = "URL"
private const val CitationAnnotationTag = "CITATION"
