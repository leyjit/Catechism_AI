package com.example.catechismapp.ui.chat.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.catechismapp.domain.model.ChatMessage

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    var isSourcesExpanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // Auto-expand sources for any assistant error/offline message that includes the
    // common SOURCES_SUFFIX phrase. This covers no-key, no-network, rate-limited,
    // server error, content-blocked, and network-interrupted paths — without needing
    // to enumerate each one. Normal LLM responses never contain this exact phrase.
    val isErrorFallback = !message.isUser &&
            message.content.contains("Here are the CCC paragraphs most relevant to your question:")

    // Determine if the Sources section should show (assistant only and has sources)
    val hasSources = !message.isUser && (message.paragraphs.isNotEmpty() || message.verses.isNotEmpty())

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        // Message bubble
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            ),
            color = if (message.isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            tonalElevation = if (message.isUser) 0.dp else 1.dp,
            modifier = Modifier
                .widthIn(max = 290.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        clipboardManager.setText(AnnotatedString(message.content))
                        Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
                    }
                )
        ) {
            if (message.isUser) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            } else {
                MarkdownText(
                    markdown = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }

        // Expandable/Offline sources block
        if (hasSources) {
            // In error/offline fallback mode, sources are visible automatically. No toggle button.
            if (isErrorFallback) {
                Spacer(modifier = Modifier.height(8.dp))
                SourcesList(
                    paragraphs = message.paragraphs,
                    verses = message.verses
                )
            } else {
                // Regular online mode: show sources toggle button
                Spacer(modifier = Modifier.height(2.dp))
                TextButton(
                    onClick = { isSourcesExpanded = !isSourcesExpanded },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text(
                        text = if (isSourcesExpanded) "Hide Sources ▲" else "Show Sources ▼",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                AnimatedVisibility(
                    visible = isSourcesExpanded,
                    enter = expandVertically(animationSpec = tween(durationMillis = 250)),
                    exit = shrinkVertically(animationSpec = tween(durationMillis = 200))
                ) {
                    SourcesList(
                        paragraphs = message.paragraphs,
                        verses = message.verses
                    )
                }
            }
        }
    }
}

@Composable
private fun SourcesList(
    paragraphs: List<com.example.catechismapp.domain.model.CatechismParagraph>,
    verses: List<com.example.catechismapp.domain.model.BibleVerse>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .padding(top = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (verses.isNotEmpty()) {
            Text(
                text = "Scripture",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
            )
            verses.forEach { verse ->
                ScriptureVerseCard(verse = verse)
            }
        }

        if (paragraphs.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "CCC Paragraphs",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            paragraphs.forEach { paragraph ->
                CccParagraphCard(paragraph = paragraph)
            }
        }
    }
}
