package com.app.chat.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.chat.model.MediaType
import com.app.chat.model.MessageItem
import com.app.chat.model.MessageStatus
import com.app.chat.theme.BackgroundDark
import com.app.chat.theme.PrimaryBlue
import com.app.chat.theme.SurfaceDark
import com.app.chat.theme.TextPrimary
import com.app.chat.theme.TextSecondary
import com.app.chat.ui.components.AvatarView
import com.app.chat.ui.components.ImagePreviewCard
import com.app.chat.ui.components.MessageInputField
import com.app.chat.ui.components.VoiceMessageBar
import com.app.chat.viewmodel.ChatViewModel

@Composable
fun DirectChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler { viewModel.handleBack() }

    val convId = viewModel.activeConversationId ?: return
    val conversation = viewModel.conversations.find { it.id == convId }
    val messages = viewModel.messagesMap[convId] ?: emptyList()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val context = LocalContext.current
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.sendImageMedia(uri, context)
        }
    }

    if (viewModel.showAttachmentDialog) {
        AttachmentChooserDialog(
            onPickGallery = {
                viewModel.showAttachmentDialog = false
                photoPickerLauncher.launch("image/*")
            },
            onDismiss = { viewModel.showAttachmentDialog = false }
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = BackgroundDark,
        topBar = {
            Box(Modifier.statusBarsPadding()) {
                val targetPeerHex = conversation?.peerIdHex ?: convId.removePrefix("chat-")
                ChatTopBar(
                    title = conversation?.name ?: "Direct Chat",
                    subtitle = if (conversation?.isOnline == true) "● Online via Bluetooth" else "○ Last seen 10m ago",
                    isOnline = conversation?.isOnline == true,
                    avatarInitials = conversation?.avatarInitials ?: "P",
                    onBack = { viewModel.handleBack() },
                    onPing = if (targetPeerHex.length == 16) { { viewModel.sendPing(targetPeerHex) } } else null
                )
            }
        },
        bottomBar = {
            Box(Modifier.navigationBarsPadding()) {
                if (viewModel.isRecordingVoice) {
                    VoiceRecordingActiveBar(
                        durationSec = viewModel.voiceRecordDurationSeconds,
                        onSend = { viewModel.toggleVoiceRecording() },
                        onCancel = { viewModel.cancelVoiceRecording() }
                    )
                } else {
                    MessageInputField(
                        text = inputText,
                        onTextChange = { inputText = it },
                        onSend = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            }
                        },
                        onAttach = { viewModel.showAttachmentDialog = true },
                        onVoiceRecord = { viewModel.toggleVoiceRecording() }
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                MeshEncryptionBanner()
            }
            if (messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("👋", fontSize = 36.sp)
                            Spacer(Modifier.height(10.dp))
                            Text("No messages yet", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Send a message or voice note to begin chatting offline over Bluetooth mesh.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            items(messages, key = { it.id }) { msg ->
                MessageBubble(
                    message = msg,
                    isPlayingVoice = viewModel.isPlayingVoiceId == msg.id,
                    onToggleVoice = { viewModel.toggleVoicePlay(msg.id) }
                )
            }
        }
    }
}

@Composable
fun GroupChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler { viewModel.handleBack() }

    val convId = viewModel.activeConversationId ?: return
    val conversation = viewModel.conversations.find { it.id == convId }
    val messages = viewModel.messagesMap[convId] ?: emptyList()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val context = LocalContext.current
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.sendImageMedia(uri, context)
        }
    }

    if (viewModel.showAttachmentDialog) {
        AttachmentChooserDialog(
            onPickGallery = {
                viewModel.showAttachmentDialog = false
                photoPickerLauncher.launch("image/*")
            },
            onDismiss = { viewModel.showAttachmentDialog = false }
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = BackgroundDark,
        topBar = {
            Box(Modifier.statusBarsPadding()) {
                ChatTopBar(
                    title = conversation?.name ?: "Group Chat",
                    subtitle = "${conversation?.membersCount ?: 4} members • Multi-hop Mesh",
                    isOnline = true,
                    avatarInitials = conversation?.avatarInitials ?: "G",
                    onBack = { viewModel.handleBack() }
                )
            }
        },
        bottomBar = {
            Box(Modifier.navigationBarsPadding()) {
                if (viewModel.isRecordingVoice) {
                    VoiceRecordingActiveBar(
                        durationSec = viewModel.voiceRecordDurationSeconds,
                        onSend = { viewModel.toggleVoiceRecording() },
                        onCancel = { viewModel.cancelVoiceRecording() }
                    )
                } else {
                    MessageInputField(
                        text = inputText,
                        onTextChange = { inputText = it },
                        onSend = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            }
                        },
                        onAttach = { viewModel.showAttachmentDialog = true },
                        onVoiceRecord = { viewModel.toggleVoiceRecording() }
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                MeshEncryptionBanner(text = "Encrypted Mesh Group • All packets relayed peer-to-peer")
            }
            if (messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("👥", fontSize = 36.sp)
                            Spacer(Modifier.height(10.dp))
                            Text("No group messages yet", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Messages sent here are broadcast to all nodes in this encrypted mesh group.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            items(messages, key = { it.id }) { msg ->
                MessageBubble(
                    message = msg,
                    showSenderName = msg.isIncoming,
                    isPlayingVoice = viewModel.isPlayingVoiceId == msg.id,
                    onToggleVoice = { viewModel.toggleVoicePlay(msg.id) }
                )
            }
        }
    }
}

@Composable
fun ChatTopBar(
    title: String,
    subtitle: String,
    isOnline: Boolean,
    avatarInitials: String,
    onBack: () -> Unit,
    onPing: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Text("←", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.width(8.dp))

        AvatarView(
            initials = avatarInitials,
            size = 40,
            isOnline = isOnline,
            showOnlineBadge = true
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = if (isOnline) Color(0xFF00E676) else TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onPing != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(PrimaryBlue.copy(alpha = 0.2f))
                        .clickable(onClick = onPing)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("🏓 Ping", color = PrimaryBlue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(BackgroundDark)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("🔒 Mesh", color = PrimaryBlue, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun MeshEncryptionBanner(
    text: String = "🔒 End-to-end encrypted direct BLE session. No logs stored on relay nodes.",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = TextSecondary.copy(alpha = 0.8f),
            fontSize = 11.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceDark.copy(alpha = 0.6f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun MessageBubble(
    message: MessageItem,
    showSenderName: Boolean = false,
    isPlayingVoice: Boolean = false,
    onToggleVoice: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (message.isSystem) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark.copy(alpha = 0.8f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = message.text,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val bubbleColor = if (message.isIncoming) SurfaceDark else PrimaryBlue
    val textColor = if (message.isIncoming) TextPrimary else Color.White
    val alignment = if (message.isIncoming) Alignment.Start else Alignment.End

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = if (message.isIncoming) 8.dp else 0.dp, end = if (!message.isIncoming) 8.dp else 0.dp, bottom = 2.dp)
        ) {
            if (showSenderName && message.isIncoming) {
                Text(
                    text = message.senderName,
                    color = PrimaryBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(6.dp))
            }
            if (message.isIncoming) {
                val hopLabel = if (message.isDirect || message.hopCount <= 1) "Direct (1 hop)" else "${message.hopCount} hops"
                val hopBg = if (message.isDirect || message.hopCount <= 1) Color(0xFF1B5E20) else Color(0xFF0D47A1)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(hopBg)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(hopLabel, color = Color.White, fontSize = 9.sp)
                }
            }
        }

        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (message.isIncoming) 4.dp else 16.dp,
                        bottomEnd = if (message.isIncoming) 16.dp else 4.dp
                    )
                )
                .background(bubbleColor)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                when (message.mediaType) {
                    MediaType.Image -> {
                        ImagePreviewCard(
                            imageLabel = message.text,
                            thumbnailBase64 = message.mediaThumbnail,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    MediaType.Voice -> {
                        VoiceMessageBar(
                            duration = message.mediaDuration ?: "0:08",
                            isPlaying = isPlayingVoice,
                            onTogglePlay = onToggleVoice,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    MediaType.Text -> {
                        Text(
                            text = message.text,
                            color = textColor,
                            fontSize = 14.sp,
                            lineHeight = 19.sp
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message.timestamp,
                        color = if (message.isIncoming) TextSecondary else Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                    if (!message.isIncoming) {
                        Spacer(Modifier.width(4.dp))
                        val statusIcon = when (message.status) {
                            MessageStatus.Draft -> "✎"
                            MessageStatus.Pending, MessageStatus.Sending -> "⏱"
                            MessageStatus.Sent -> "✓"
                            MessageStatus.Delivered -> "✓✓"
                            MessageStatus.Read -> "✓✓"
                            MessageStatus.Failed -> "!"
                        }
                        Text(
                            text = statusIcon,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceRecordingActiveBar(
    durationSec: Int,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val durationText = String.format("0:%02d", durationSec)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color.Red)
            )
            Spacer(Modifier.width(10.dp))
            Text("Recording $durationText", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Cancel",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
            Spacer(Modifier.width(6.dp))
            Button(
                onClick = onSend,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Send Voice", fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun AttachmentChooserDialog(
    onPickGallery: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Text("Attach Photo", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text("Select an image from device gallery to encrypt and transmit peer-to-peer:", color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BackgroundDark)
                        .clickable { onPickGallery() }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🖼️", fontSize = 22.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Photo from Device Gallery", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Compressed and sent over Bluetooth mesh", color = TextSecondary, fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
