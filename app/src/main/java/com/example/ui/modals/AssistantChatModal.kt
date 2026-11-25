package com.example.ui.modals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.remote.AssistantAction
import com.example.i18n.AppStrings
import com.example.ui.AssistantChatMessage
import com.example.ui.AssistantChatUiState
import com.example.ui.components.SmartShelfLogo
import com.example.ui.theme.LocalSmartShelfColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssistantChatModal(
    chatState: AssistantChatUiState,
    strings: AppStrings,
    onSendMessage: (String) -> Unit,
    onRetryLast: () -> Unit,
    onClearChat: () -> Unit,
    onTriggerAction: (AssistantAction) -> Unit,
    onDismiss: () -> Unit
) {
    val ext = LocalSmartShelfColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

    LaunchedEffect(chatState.messages.size, chatState.isThinking) {
        val totalItems = 1 + chatState.messages.size + if (chatState.isThinking) 1 else 0
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    fun submitCurrent() {
        val trimmed = inputText.trim()
        if (trimmed.isNotBlank() && !chatState.isThinking) {
            onSendMessage(trimmed)
            inputText = ""
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ext.card
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    SmartShelfLogo(showWordmark = false)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = strings.assistantTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = ext.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (chatState.messages.isNotEmpty()) {
                        TextButton(
                            onClick = onClearChat,
                            modifier = Modifier.testTag("assistant_clear_button")
                        ) {
                            Text(
                                text = strings.assistantClearChat,
                                style = MaterialTheme.typography.labelMedium,
                                color = ext.accent
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("assistant_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = strings.close,
                            tint = ext.textSecondary
                        )
                    }
                }
            }

            HorizontalDivider(color = ext.border)

            // Messages Stream
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(ext.canvas),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Built-in Welcome Message + Quick Prompt Suggestions
                item(key = "welcome_message") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AssistantBubble(
                            message = AssistantChatMessage(
                                id = "welcome",
                                isUser = false,
                                text = strings.assistantWelcomeMessage,
                                actions = emptyList()
                            ),
                            strings = strings,
                            onTriggerAction = onTriggerAction,
                            onRetry = {}
                        )

                        if (chatState.messages.isEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                strings.assistantPromptChips.forEachIndexed { idx, prompt ->
                                    Surface(
                                        onClick = { onSendMessage(prompt) },
                                        shape = CircleShape,
                                        color = ext.card,
                                        border = BorderStroke(1.dp, ext.accent.copy(alpha = 0.45f)),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("assistant_prompt_chip_$idx")
                                    ) {
                                        Text(
                                            text = prompt,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = ext.textPrimary,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                items(chatState.messages, key = { it.id }) { msg ->
                    if (msg.isUser) {
                        UserBubble(text = msg.text)
                    } else {
                        AssistantBubble(
                            message = msg,
                            strings = strings,
                            onTriggerAction = onTriggerAction,
                            onRetry = onRetryLast
                        )
                    }
                }

                if (chatState.isThinking) {
                    item(key = "thinking_indicator") {
                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 4.dp,
                                topEnd = 16.dp,
                                bottomStart = 16.dp,
                                bottomEnd = 16.dp
                            ),
                            color = ext.elevated,
                            border = BorderStroke(1.dp, ext.border)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = ext.accent
                                )
                                Text(
                                    text = strings.assistantThinking,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = ext.border)

            // Bottom Chat Composer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ext.card)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = strings.assistantInputPlaceholder,
                            color = ext.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submitCurrent() }),
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ext.elevated,
                        unfocusedContainerColor = ext.elevated,
                        focusedBorderColor = ext.accent,
                        unfocusedBorderColor = ext.border
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("assistant_text_input")
                )

                val canSend = inputText.isNotBlank() && !chatState.isThinking
                Surface(
                    onClick = { if (canSend) submitCurrent() },
                    enabled = canSend,
                    shape = CircleShape,
                    color = if (canSend) ext.accent else ext.elevated,
                    border = if (canSend) null else BorderStroke(1.dp, ext.border),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("assistant_send_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = strings.confirm,
                            tint = if (canSend) {
                                if (ext.isDark) Color(0xFF06130A) else Color.White
                            } else ext.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    val ext = LocalSmartShelfColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 4.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            color = ext.accent,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (ext.isDark) Color(0xFF06130A) else Color.White,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssistantBubble(
    message: AssistantChatMessage,
    strings: AppStrings,
    onTriggerAction: (AssistantAction) -> Unit,
    onRetry: () -> Unit
) {
    val ext = LocalSmartShelfColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            color = if (message.isError) ext.warningBg else ext.elevated,
            border = BorderStroke(
                1.dp,
                if (message.isError) ext.warningBorder else ext.border
            ),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isError) ext.warning else ext.textPrimary
                )

                if (message.isError) {
                    Surface(
                        onClick = onRetry,
                        shape = CircleShape,
                        color = ext.card,
                        border = BorderStroke(1.dp, ext.warningBorder),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = ext.warning,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = strings.assistantRetry,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = ext.warning
                            )
                        }
                    }
                }

                if (message.actions.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        message.actions.forEach { action ->
                            val label = strings.assistantActionLabels[action.name] ?: action.name
                            Surface(
                                onClick = { onTriggerAction(action) },
                                shape = CircleShape,
                                color = ext.accentMuted,
                                border = BorderStroke(1.dp, ext.accent.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("assistant_action_${action.name}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = ext.accent
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                        contentDescription = null,
                                        tint = ext.accent,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
