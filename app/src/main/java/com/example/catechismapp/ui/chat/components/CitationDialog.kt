package com.example.catechismapp.ui.chat.components

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.catechismapp.domain.model.BibleVerse
import com.example.catechismapp.domain.model.CatechismParagraph

sealed interface Citation {
    data class Bible(val verse: BibleVerse) : Citation
    data class CCC(val paragraph: CatechismParagraph) : Citation
}

@Composable
fun CitationDialog(
    citation: Citation,
    onDismiss: () -> Unit
) {
    val (title, content) = when (citation) {
        is Citation.Bible -> citation.verse.reference to citation.verse.text
        is Citation.CCC -> "CCC \u00A7${citation.paragraph.id}" to citation.paragraph.text
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
