package com.app.chat.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.chat.model.NavigationTab
import com.app.chat.theme.AvatarBg
import com.app.chat.theme.BackgroundDark
import com.app.chat.theme.DangerRed
import com.app.chat.theme.ImageContainerBg
import com.app.chat.theme.PrimaryBlue
import com.app.chat.theme.SurfaceDark
import com.app.chat.theme.SurfaceNavbar
import com.app.chat.theme.TextPrimary
import com.app.chat.theme.TextSecondary
import com.app.chat.theme.TrackColor

@Composable
fun AvatarView(
    initials: String,
    size: Int = 42,
    isOnline: Boolean = true,
    showOnlineBadge: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(AvatarBg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials.uppercase(),
            color = TextPrimary,
            fontSize = (size * 0.38f).sp,
            fontWeight = FontWeight.SemiBold
        )
        if (showOnlineBadge) {
            Box(
                modifier = Modifier
                    .size((size * 0.28f).dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(if (isOnline) Color(0xFF00E676) else TextSecondary)
                    .border(2.dp, BackgroundDark, CircleShape)
            )
        }
    }
}

@Composable
fun UnreadBadgeView(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(PrimaryBlue)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count.toString(),
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SearchInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search conversations",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(SurfaceDark)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("⌕", color = TextSecondary, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(placeholder, color = TextSecondary, fontSize = 14.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(PrimaryBlue),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        if (query.isNotEmpty()) {
            Text(
                "✕",
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(4.dp)
                    .clickable { onQueryChange("") }
            )
        }
    }
}

@Composable
fun MessageInputField(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit = {},
    onVoiceRecord: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BackgroundDark)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Attachment Button
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(SurfaceDark)
                .clickable(onClick = onAttach),
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = TextSecondary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.width(8.dp))

        // Text Field
        Row(
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceDark)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (text.isEmpty()) {
                    Text("Type to chat...", color = TextSecondary, fontSize = 14.sp)
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(PrimaryBlue),
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Mic or Send Button
        if (text.isBlank()) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SurfaceDark)
                    .clickable(onClick = onVoiceRecord),
                contentAlignment = Alignment.Center
            ) {
                Text("🎙", fontSize = 16.sp)
            }
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PrimaryBlue)
                    .clickable(onClick = onSend),
                contentAlignment = Alignment.Center
            ) {
                Text("➤", color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    currentTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        containerColor = SurfaceNavbar,
        tonalElevation = 0.dp,
        modifier = modifier
    ) {
        NavigationTab.entries.forEach { tab ->
            val isSelected = currentTab == tab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Text(
                        text = tab.iconText,
                        color = if (isSelected) PrimaryBlue else TextSecondary,
                        fontSize = 18.sp
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        color = if (isSelected) PrimaryBlue else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PrimaryBlue,
                    selectedTextColor = PrimaryBlue,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
fun VoiceMessageBar(
    duration: String,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .width(230.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .clickable(onClick = onTogglePlay)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (isPlaying) PrimaryBlue else AvatarBg),
            contentAlignment = Alignment.Center
        ) {
            Text(if (isPlaying) "❚❚" else "▶", color = Color.White, fontSize = 12.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            if (isPlaying) "▅▃▇▅▃▆▂▅▃" else "〰〰〰〰〰",
            color = if (isPlaying) PrimaryBlue else TextSecondary,
            fontSize = 14.sp
        )
        Spacer(Modifier.weight(1f))
        Text(duration, color = TextSecondary, fontSize = 10.sp)
    }
}

@Composable
fun ImagePreviewCard(
    imageLabel: String = "Encrypted Media",
    thumbnailBase64: String? = null,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(thumbnailBase64) {
        if (!thumbnailBase64.isNullOrBlank()) {
            try {
                val bytes = android.util.Base64.decode(thumbnailBase64, android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        } else null
    }

    Column(
        modifier = modifier
            .width(210.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ImageContainerBg),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = imageLabel,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📷", fontSize = 26.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        imageLabel,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun StorageMeterBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(TrackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(999.dp))
                .background(PrimaryBlue)
        )
    }
}

@Composable
fun RadarPulseGraphic(
    isScanning: Boolean = true,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.width * 0.45f

        if (isScanning) {
            drawCircle(
                color = PrimaryBlue.copy(alpha = pulseAlpha),
                radius = maxRadius * pulseScale,
                center = center,
                style = Stroke(3f)
            )
        }

        repeat(3) { index ->
            val ringRadius = maxRadius * ((index + 1) / 3f)
            drawCircle(
                color = PrimaryBlue.copy(alpha = 0.15f),
                radius = ringRadius,
                center = center,
                style = Stroke(2f)
            )
        }

        drawCircle(
            color = PrimaryBlue.copy(alpha = 0.25f),
            radius = 24f,
            center = center
        )
        drawCircle(
            color = PrimaryBlue,
            radius = 12f,
            center = center
        )

        val peerOffsets = listOf(
            Offset(size.width * 0.26f, size.height * 0.36f),
            Offset(size.width * 0.76f, size.height * 0.32f),
            Offset(size.width * 0.68f, size.height * 0.74f)
        )
        peerOffsets.forEach { pos ->
            drawCircle(color = AvatarBg, radius = 10f, center = pos)
            drawCircle(color = PrimaryBlue.copy(alpha = 0.8f), radius = 4f, center = pos)
        }
    }
}

@Composable
fun MeshNetworkGraphic(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val nodes = listOf(
            center,
            Offset(size.width * 0.25f, size.height * 0.35f),
            Offset(size.width * 0.75f, size.height * 0.35f),
            Offset(size.width * 0.32f, size.height * 0.72f),
            Offset(size.width * 0.68f, size.height * 0.72f)
        )
        nodes.drop(1).forEach {
            drawLine(
                color = PrimaryBlue.copy(alpha = 0.55f),
                start = center,
                end = it,
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
        }
        nodes.forEachIndexed { index, node ->
            drawCircle(
                color = if (index == 0) PrimaryBlue else AvatarBg,
                radius = if (index == 0) 18f else 12f,
                center = node
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.12f),
                radius = if (index == 0) 32f else 22f,
                center = node,
                style = Stroke(2f)
            )
        }
    }
}

@Composable
fun PanicWipeConfirmDialog(
    isOpen: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Text("Emergency Panic Wipe", color = DangerRed, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                "This action will immediately destroy all local encryption keys, drop all chat databases, and wipe message history. This cannot be undone.",
                color = TextPrimary,
                fontSize = 14.sp
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = Color.White)
            ) {
                Text("Wipe Everything")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
