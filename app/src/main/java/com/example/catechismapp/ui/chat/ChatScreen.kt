package com.example.catechismapp.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.catechismapp.domain.model.ChatMessage
import com.example.catechismapp.ui.chat.components.Citation
import com.example.catechismapp.ui.chat.components.CitationDialog
import com.example.catechismapp.ui.chat.components.MessageBubble
import com.example.catechismapp.ui.chat.components.MessageInputBar
import com.example.catechismapp.util.findActivity
import com.example.catechismapp.voice.VoiceInputState
import com.example.catechismapp.voice.rememberVoiceInputController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isApiKeyMissing by viewModel.isApiKeyMissing.collectAsState()

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    var inputText by rememberSaveable { mutableStateOf("") }
    var isFieldFocused by remember { mutableStateOf(false) }
    var hasRequestedMicPermission by rememberSaveable { mutableStateOf(false) }
    var showPermissionRationaleDialog by remember { mutableStateOf(false) }
    var showOpenMicSettingsDialog by remember { mutableStateOf(false) }

    var isBannerDismissed by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var selectedCitation by remember { mutableStateOf<Citation?>(null) }

    val focusRequester = remember { FocusRequester() }
    val isInputEnabled = !uiState.isLoading
    val sendQuestion: (String) -> Unit = { question ->
        val trimmed = question.trim()
        if (trimmed.isNotEmpty()) {
            viewModel.sendQuestion(trimmed)
            inputText = ""
            focusManager.clearFocus()
        }
    }

    val voiceController = rememberVoiceInputController(
        onTranscript = { transcript ->
            val trimmed = transcript.trim()
            if (trimmed.isBlank()) {
                snackbarScope.launch {
                    snackbarHostState.showSnackbar("No speech detected")
                }
            } else {
                sendQuestion(trimmed)
            }
        },
        onError = { message ->
            snackbarScope.launch {
                snackbarHostState.showSnackbar(message)
            }
        },
    )

    val voiceState by voiceController.state

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasRequestedMicPermission = true
        if (granted) {
            voiceController.startListening()
        } else {
            snackbarScope.launch {
                snackbarHostState.showSnackbar("Microphone access is needed for voice input.")
            }
        }
    }

    val onMicClick: () -> Unit = micClick@{
        when (voiceState) {
            is VoiceInputState.Listening -> voiceController.stopListening()
            is VoiceInputState.Processing -> return@micClick
            is VoiceInputState.Idle -> {
                if (inputText.trim().isNotEmpty()) {
                    sendQuestion(inputText)
                    return@micClick
                }

                if (!voiceController.isRecognitionAvailable) {
                    snackbarScope.launch {
                        snackbarHostState.showSnackbar(
                            "Speech recognition not available on this device.",
                        )
                    }
                    return@micClick
                }

                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED

                if (granted) {
                    voiceController.startListening()
                    return@micClick
                }

                val activity = context.findActivity()
                if (activity == null) {
                    snackbarScope.launch {
                        snackbarHostState.showSnackbar("Unable to request microphone permission.")
                    }
                    return@micClick
                }

                val showRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.RECORD_AUDIO,
                )

                when {
                    showRationale -> showPermissionRationaleDialog = true
                    hasRequestedMicPermission -> showOpenMicSettingsDialog = true
                    else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }

    val voiceStatusMessage = when (voiceState) {
        is VoiceInputState.Listening -> "Listening… tap mic to stop"
        is VoiceInputState.Processing -> "Processing speech…"
        is VoiceInputState.Idle -> null
    }

    LaunchedEffect(isInputEnabled, voiceState) {
        if (!isInputEnabled &&
            (voiceState is VoiceInputState.Listening || voiceState is VoiceInputState.Processing)
        ) {
            voiceController.cancel()
        }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, uiState.isLoading) {
        if (messages.isNotEmpty() || uiState.isLoading) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Catechist AI",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                actions = {
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirmDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear conversation history",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .imePadding(),
        ) {
            if (isApiKeyMissing && !isBannerDismissed) {
                InfoBanner(
                    onDismiss = { isBannerDismissed = true },
                    onAction = onNavigateToSettings,
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                if (messages.isEmpty() && !uiState.isLoading) {
                    EmptyChatPlaceholder(
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (uiState.isLoading) {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    uiState.pendingQuestion?.let { pendingQuestion ->
                                        MessageBubble(
                                            message = ChatMessage(
                                                id = Int.MIN_VALUE,
                                                role = "user",
                                                content = pendingQuestion,
                                                timestamp = System.currentTimeMillis(),
                                            ),
                                            onCitationClick = { selectedCitation = it },
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(32.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            strokeWidth = 3.dp,
                                        )
                                    }
                                }
                            }
                        }

                        items(messages) { message ->
                            MessageBubble(
                                message = message,
                                onCitationClick = { selectedCitation = it },
                            )
                        }
                    }
                }

                uiState.error?.let { errorMsg ->
                    ErrorBanner(
                        message = errorMsg,
                        onDismiss = { viewModel.dismissError() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp),
                    )
                }
            }

            MessageInputBar(
                text = inputText,
                onTextChange = { inputText = it },
                isEnabled = isInputEnabled,
                onFieldFocusChange = { isFieldFocused = it },
                onCancelVoice = { voiceController.cancel() },
                focusRequester = focusRequester,
                voiceState = voiceState,
                isVoiceInputAvailable = voiceController.isRecognitionAvailable,
                onMicClick = onMicClick,
                voiceStatusMessage = voiceStatusMessage,
            )
        }
    }

    selectedCitation?.let { citation ->
        CitationDialog(
            citation = citation,
            onDismiss = { selectedCitation = null },
        )
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(
                    text = "Clear conversation?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "This will delete all messages and conversation history. This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearConversation()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showPermissionRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionRationaleDialog = false },
            title = { Text("Microphone access") },
            text = {
                Text(
                    "Voice input needs microphone access so you can ask questions by speaking.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionRationaleDialog = false
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationaleDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showOpenMicSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showOpenMicSettingsDialog = false },
            title = { Text("Microphone permission required") },
            text = {
                Text(
                    "Microphone access was denied. Open Settings to enable it for voice input.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOpenMicSettingsDialog = false
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        )
                        context.startActivity(intent)
                    },
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOpenMicSettingsDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun EmptyChatPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = "Ask a question about Catholic teaching",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Your answer will be grounded in the Catechism of the Catholic Church",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun InfoBanner(
    onDismiss: () -> Unit,
    onAction: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "To get AI-powered answers, add your free Gemini API key in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Set Up",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onAction() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss error",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
