package com.example.catechismapp.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.catechismapp.voice.VoiceInputState

@Composable
fun MessageInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    isEnabled: Boolean,
    onFieldFocusChange: (Boolean) -> Unit,
    onCancelVoice: () -> Unit,
    focusRequester: FocusRequester,
    voiceState: VoiceInputState,
    isVoiceInputAvailable: Boolean,
    onMicClick: () -> Unit,
    voiceStatusMessage: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        Column {
            voiceStatusMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = {
                        Text(
                            text = "Ask about Catholic doctrine…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    maxLines = 4,
                    enabled = isEnabled,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            onFieldFocusChange(focusState.isFocused)
                            if (focusState.isFocused && voiceState is VoiceInputState.Listening) {
                                onCancelVoice()
                            }
                        },
                )

                val listening = voiceState is VoiceInputState.Listening
                val hasText = text.trim().isNotEmpty()
                val actionEnabled = isEnabled &&
                    voiceState !is VoiceInputState.Processing &&
                    (hasText || isVoiceInputAvailable || listening)
                IconButton(
                    onClick = onMicClick,
                    enabled = actionEnabled,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (listening || hasText) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                            },
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = when {
                            listening -> "Stop voice input"
                            hasText -> "Send typed question"
                            else -> "Start voice input"
                        },
                        tint = if (listening || hasText) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (actionEnabled) 0.87f else 0.38f,
                            )
                        },
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
