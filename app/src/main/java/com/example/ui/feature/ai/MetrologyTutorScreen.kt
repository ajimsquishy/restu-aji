package com.example.ui.feature.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.model.InstrumentType
import com.example.ui.components.MeasureLabTopBar
import com.example.ui.theme.CyanHighlight
import com.example.ui.theme.LabBackground
import com.example.ui.theme.LabOutline
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.OrangeAccent
import com.example.ui.theme.OrangeContainer
import com.example.ui.theme.TealSecondary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ToleranceRed

@Composable
fun MetrologyTutorScreen(
    initialTopicParam: String? = null,
    onBack: () -> Unit,
    onLaunchSimulator: (InstrumentType) -> Unit
) {
    val initialTopic = TutorFocusTopic.fromRouteParam(initialTopicParam)
    val vm: MetrologyTutorViewModel = viewModel(
        key = "metrology_tutor_${initialTopic.id}",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MetrologyTutorViewModel(initialTopic) as T
            }
        }
    )

    val state by vm.uiState.collectAsStateWithLifecycle()
    var draftMessage by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    val isBusy = state.status is TutorStatus.Loading || state.status is TutorStatus.Streaming

    // Auto-scroll to latest message as conversation grows or streams
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text?.length) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size)
        }
    }

    fun submitDraft() {
        val text = draftMessage.trim()
        if (text.isNotEmpty() && !isBusy) {
            draftMessage = ""
            vm.sendMessage(text)
        }
    }

    Scaffold(
        containerColor = LabBackground,
        topBar = {
            MeasureLabTopBar(
                title = "Asisten Alat Ukur AI",
                subtitle = "Tutor Interaktif Metrologi • ${state.selectedTopic.label}",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = { vm.resetConversation() },
                        enabled = !isBusy && state.messages.isNotEmpty(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("tutor_reset_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Mulai Percakapan Baru",
                            tint = if (!isBusy && state.messages.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.45f)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .testTag("metrology_tutor_screen")
        ) {
            // 1. Instrument Focus Filter Bar + Direct Simulator Shortcut
            Surface(
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.testTag("tutor_topic_selector_row")
                    ) {
                        items(TutorFocusTopic.entries, key = { it.id }) { topic ->
                            val selected = state.selectedTopic == topic
                            FilterChip(
                                selected = selected,
                                onClick = { vm.selectTopic(topic) },
                                label = {
                                    Text(
                                        text = topic.shortLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NavyPrimary,
                                    selectedLabelColor = Color.White,
                                    containerColor = LabBackground,
                                    labelColor = NavyPrimary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = LabOutline,
                                    selectedBorderColor = NavyPrimary
                                ),
                                modifier = Modifier.testTag("tutor_topic_${topic.id}")
                            )
                        }
                    }

                    state.selectedTopic.relatedInstrument?.let { instrument ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Fokus: ${state.selectedTopic.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                color = OrangeContainer,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, OrangeAccent.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onLaunchSimulator(instrument) }
                                    .testTag("tutor_open_simulator_chip")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Buka Simulator",
                                        tint = OrangeAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Buka Simulator",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NavyDark,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Scrollable Multi-Turn Conversation Thread
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("tutor_message_list"),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "tutor_welcome_header") {
                    TutorWelcomeCard(
                        topic = state.selectedTopic,
                        showStarterPrompts = state.messages.isEmpty(),
                        isBusy = isBusy,
                        onSelectPrompt = { prompt -> vm.sendMessage(prompt) }
                    )
                }

                items(state.messages, key = { it.id }) { message ->
                    ChatMessageBubble(message = message)
                }

                val status = state.status
                if (status is TutorStatus.Error) {
                    item(key = "tutor_error_item") {
                        TutorErrorBanner(
                            message = status.message,
                            canRetry = state.lastFailedPrompt != null,
                            onRetry = { vm.retryLastMessage() },
                            onDismiss = { vm.clearError() }
                        )
                    }
                }
            }

            // 3. Quick Prompt Chips Strip (visible even after conversation starts for follow-up exploration)
            if (state.messages.isNotEmpty() && !isBusy) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(state.selectedTopic.quickPrompts) { idx, prompt ->
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, TealSecondary.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { vm.sendMessage(prompt) }
                                .testTag("tutor_followup_prompt_$idx")
                        ) {
                            Text(
                                text = prompt,
                                style = MaterialTheme.typography.labelSmall,
                                color = NavyPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .widthIn(max = 240.dp)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // 4. Message Composer Bar
            Surface(
                color = Color.White,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, LabOutline),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = draftMessage,
                        onValueChange = { draftMessage = it },
                        placeholder = {
                            Text(
                                text = "Tanya cara pakai, baca skala, atau kalibrasi...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        },
                        enabled = !isBusy,
                        maxLines = 4,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NavyPrimary,
                            unfocusedBorderColor = LabOutline,
                            focusedContainerColor = LabBackground,
                            unfocusedContainerColor = LabBackground
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submitDraft() }),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tutor_message_input")
                    )

                    val canSend = draftMessage.isNotBlank() && !isBusy
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (canSend) OrangeAccent else LabOutline)
                            .clickable(enabled = canSend) { submitDraft() }
                            .testTag("tutor_send_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                color = NavyPrimary,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Kirim Pertanyaan",
                                tint = if (canSend) Color.White else TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorWelcomeCard(
    topic: TutorFocusTopic,
    showStarterPrompts: Boolean,
    isBusy: Boolean,
    onSelectPrompt: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(NavyDark, NavyPrimary, Color(0xFF0E5A7B))
                        )
                    )
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(OrangeAccent.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Instruktur AI MEASURE LAB",
                                tint = OrangeAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Instruktur AI MEASURE LAB",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Panduan Pengetahuan & Penggunaan Alat Ukur Presisi",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanHighlight
                            )
                        }
                    }
                    Text(
                        text = "Tanyakan apa saja mengenai fungsi komponen, teknik memegang alat, pembacaan skala nonius & thimble, penggunaan tombol digital, kalibrasi nol, hingga analisis toleransi dan galat pengukuran.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }

        if (showStarterPrompts) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.TipsAndUpdates,
                    contentDescription = null,
                    tint = OrangeAccent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Contoh Pertanyaan (${topic.label}):",
                    style = MaterialTheme.typography.labelLarge,
                    color = NavyPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            topic.quickPrompts.forEachIndexed { index, prompt ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isBusy) { onSelectPrompt(prompt) }
                        .testTag("tutor_quick_prompt_$index"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, LabOutline)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(OrangeContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = OrangeAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodySmall,
                            color = NavyDark,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(message: TutorChatMessage) {
    val isUser = message.author == ChatAuthor.USER
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(if (isUser) "tutor_user_bubble" else "tutor_model_bubble"),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (isUser) {
            Surface(
                color = NavyPrimary,
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 4.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 18.dp
                ),
                modifier = Modifier.widthIn(max = 310.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!message.topicLabel.isNullOrBlank()) {
                        Text(
                            text = message.topicLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanHighlight,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }
        } else {
            Card(
                shape = RoundedCornerShape(
                    topStart = 4.dp,
                    topEnd = 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 18.dp
                ),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, LabOutline),
                modifier = Modifier.widthIn(max = 330.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = "Instruktur AI",
                            tint = OrangeAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Instruktur AI",
                            style = MaterialTheme.typography.labelMedium,
                            color = NavyPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        if (message.isStreaming) {
                            Spacer(Modifier.width(4.dp))
                            CircularProgressIndicator(
                                color = TealSecondary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Mengetik...",
                                style = MaterialTheme.typography.labelSmall,
                                color = TealSecondary
                            )
                        }
                    }
                    Text(
                        text = if (message.text.isBlank() && message.isStreaming) {
                            "Sedang menyusun penjelasan alat ukur..."
                        } else {
                            message.text
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (message.text.isBlank()) TextSecondary else NavyDark
                    )
                }
            }
        }
    }
}

@Composable
private fun TutorErrorBanner(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F2)),
        border = BorderStroke(1.dp, ToleranceRed.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tutor_error_banner")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "Terjadi Kesalahan",
                    tint = ToleranceRed
                )
                Text(
                    text = "Permintaan AI Gagal",
                    style = MaterialTheme.typography.titleSmall,
                    color = ToleranceRed,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = NavyDark
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canRetry) {
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                        modifier = Modifier.height(48.dp).testTag("tutor_retry_button")
                    ) {
                        Text("Coba Lagi")
                    }
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Tutup")
                }
            }
        }
    }
}
