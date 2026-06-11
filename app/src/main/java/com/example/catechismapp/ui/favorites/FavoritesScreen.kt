package com.example.catechismapp.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.catechismapp.domain.model.QaPair
import com.example.catechismapp.ui.chat.components.Citation
import com.example.catechismapp.ui.chat.components.CitationDialog
import com.example.catechismapp.ui.chat.components.QaPairCard
import com.example.catechismapp.ui.chat.components.toPlainCopyText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel = hiltViewModel()
) {
    val favorites by viewModel.favorites.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    var selectedPairIds by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var selectedCitation by remember { mutableStateOf<Citation?>(null) }

    val selectedPairs = favorites.filter { it.id in selectedPairIds }
    val isSelectionMode = selectedPairIds.isNotEmpty()
    val listState = rememberLazyListState()
    val isAtTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
    val showScrollFab by remember {
        derivedStateOf {
            listState.canScrollBackward || listState.canScrollForward
        }
    }

    fun clearSelection() {
        selectedPairIds = emptyList()
    }

    fun togglePairSelection(pairId: Int) {
        selectedPairIds = if (pairId in selectedPairIds) {
            selectedPairIds - pairId
        } else {
            selectedPairIds + pairId
        }
    }

    fun selectAllFavorites() {
        selectedPairIds = favorites.map { it.id }
    }

    fun copySelectedPairs() {
        if (selectedPairs.isEmpty()) return
        clipboardManager.setText(AnnotatedString(selectedPairs.toCopyText()))
        snackbarScope.launch {
            snackbarHostState.showSnackbar("${selectedPairs.size} Q&A pairs copied")
        }
        clearSelection()
    }

    fun deleteSelectedPairs() {
        if (selectedPairs.isEmpty()) return
        val count = selectedPairs.size
        snackbarScope.launch {
            viewModel.deleteFavorites(selectedPairs)
            clearSelection()
            snackbarHostState.showSnackbar("$count favorite Q&A pairs deleted")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isSelectionMode) "${selectedPairIds.size} selected" else "Faves",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = { selectAllFavorites() }) {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = "Select all favorite Q&A pairs",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { copySelectedPairs() }) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy selected favorite Q&A pairs",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { deleteSelectedPairs() }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete selected favorite Q&A pairs",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { clearSelection() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel favorite selection",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            if (favorites.isEmpty()) {
                EmptyFavoritesPlaceholder(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(favorites, key = { it.id }) { pair ->
                        val isSelected = pair.id in selectedPairIds
                        QaPairCard(
                            pair = pair,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            onToggleSelection = { togglePairSelection(pair.id) },
                            onLongPressSelection = {
                                if (!isSelected) {
                                    togglePairSelection(pair.id)
                                }
                            },
                            onCitationClick = { selectedCitation = it }
                        )
                    }
                }

                if (showScrollFab) {
                    FloatingActionButton(
                        onClick = {
                            snackbarScope.launch {
                                if (isAtTop) {
                                    listState.animateScrollToItem(favorites.lastIndex)
                                } else {
                                    listState.animateScrollToItem(0)
                                }
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        Icon(
                            imageVector = if (isAtTop) {
                                Icons.Default.KeyboardArrowDown
                            } else {
                                Icons.Default.KeyboardArrowUp
                            },
                            contentDescription = if (isAtTop) {
                                "Jump to bottom of favorites"
                            } else {
                                "Jump to top of favorites"
                            }
                        )
                    }
                }
            }
        }
    }

    selectedCitation?.let { citation ->
        CitationDialog(
            citation = citation,
            onDismiss = { selectedCitation = null }
        )
    }
}

private fun List<QaPair>.toCopyText(): String =
    joinToString("\n\n") { pair ->
        buildString {
            appendLine("Question:")
            appendLine(toPlainCopyText(pair.question.content))
            pair.answer?.let { answer ->
                appendLine()
                appendLine("Answer:")
                append(toPlainCopyText(answer.content))
            }
        }
    }

@Composable
private fun EmptyFavoritesPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "No saved Q&A pairs yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Move selected Q&A pairs from Chat to keep them here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
