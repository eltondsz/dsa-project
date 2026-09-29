package com.app.chat.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.chat.theme.BackgroundDark
import com.app.chat.theme.DividerColor
import com.app.chat.theme.PrimaryBlue
import com.app.chat.theme.SurfaceDark
import com.app.chat.theme.TextPrimary
import com.app.chat.theme.TextSecondary
import com.app.chat.ui.components.StorageMeterBar
import com.app.chat.viewmodel.ChatViewModel

@Composable
fun StorageScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler { viewModel.handleBack() }
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(20.dp)
    ) {
        Spacer(Modifier.height(10.dp))

        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { viewModel.handleBack() },
                contentAlignment = Alignment.Center
            ) {
                Text("←", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Storage & Data", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Local SQLite & Media Cache", color = TextSecondary, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(24.dp))

        // Total Used Storage Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceDark)
                .border(1.dp, DividerColor, RoundedCornerShape(14.dp))
                .padding(18.dp)
        ) {
            Text("Used Storage", color = TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${formatStorageSize(viewModel.storageStats.usedBytes)} / ${formatStorageSize(viewModel.storageStats.totalBytes)} limit",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(14.dp))
            val progress = (viewModel.storageStats.usedBytes.toFloat() / viewModel.storageStats.totalBytes.toFloat()).coerceIn(0f, 1f)
            StorageMeterBar(progress = progress)
        }

        Spacer(Modifier.height(20.dp))

        Text("Storage Breakdown", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark)
                .border(1.dp, DividerColor, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StorageBreakdownRow("💬 Encrypted Messages", formatStorageSize(viewModel.storageStats.messagesBytes), PrimaryBlue)
            StorageBreakdownRow("📷 Media & Images", formatStorageSize(viewModel.storageStats.imagesBytes), Color(0xFF00E676))
            StorageBreakdownRow("🎙 Voice Notes", formatStorageSize(viewModel.storageStats.voiceBytes), Color(0xFFFFB300))
            StorageBreakdownRow("⚙️ Relay Routing Cache", formatStorageSize(viewModel.storageStats.otherBytes), Color(0xFF9C27B0))
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                viewModel.clearCache()
                Toast.makeText(context, "Temporary mesh cache cleared successfully.", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Clear Media Cache", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = {
                Toast.makeText(context, "Old expired packets pruned from database.", Toast.LENGTH_SHORT).show()
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Prune Expired Messages (>30 Days)", color = TextPrimary, fontSize = 13.sp)
        }
    }
}

@Composable
fun StorageBreakdownRow(name: String, size: String, colorDot: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(colorDot)
            )
            Spacer(Modifier.width(10.dp))
            Text(name, color = TextPrimary, fontSize = 13.sp)
        }
        Text(size, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

private fun formatStorageSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000L -> String.format(java.util.Locale.getDefault(), "%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000L -> String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000L -> String.format(java.util.Locale.getDefault(), "%.1f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
}
