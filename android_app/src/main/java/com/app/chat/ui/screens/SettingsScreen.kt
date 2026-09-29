package com.app.chat.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.chat.model.Screen
import com.app.chat.theme.BackgroundDark
import com.app.chat.theme.DangerRed
import com.app.chat.theme.DividerColor
import com.app.chat.theme.PrimaryBlue
import com.app.chat.theme.SurfaceDark
import com.app.chat.theme.TextPrimary
import com.app.chat.theme.TextSecondary
import com.app.chat.ui.components.AvatarView
import com.app.chat.ui.components.BottomNavigationBar
import com.app.chat.ui.components.PanicWipeConfirmDialog
import com.app.chat.viewmodel.ChatViewModel

@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler { viewModel.handleBack() }

    var isRelayEnabled by remember { mutableStateOf(true) }
    var isBleAdvEnabled by remember { mutableStateOf(true) }
    var isAutoPruneEnabled by remember { mutableStateOf(true) }

    if (viewModel.showPanicDialog) {
        PanicWipeConfirmDialog(
            isOpen = viewModel.showPanicDialog,
            onConfirm = { viewModel.triggerPanicWipe() },
            onDismiss = { viewModel.showPanicDialog = false }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BackgroundDark,
        bottomBar = {
            BottomNavigationBar(
                currentTab = viewModel.activeTab,
                onTabSelected = { viewModel.selectTab(it) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))

            Text(
                text = "Settings",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(16.dp))

            // User Identity Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceDark)
                    .border(1.dp, DividerColor, RoundedCornerShape(14.dp))
                    .clickable { viewModel.navigateTo(Screen.Profile) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarView(
                    initials = viewModel.userProfile.displayName.take(1),
                    size = 48,
                    isOnline = true
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = viewModel.userProfile.displayName,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Key: ${viewModel.userProfile.publicKey}",
                        color = PrimaryBlue,
                        fontSize = 12.sp
                    )
                }
                Text("Edit →", color = TextSecondary, fontSize = 13.sp)
            }

            Spacer(Modifier.height(20.dp))

            // Mesh Routing Section
            Text("Mesh & Relay Options", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark)
                    .border(1.dp, DividerColor, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                SettingSwitchRow(
                    title = "Background Mesh Relay",
                    subtitle = "Forward encrypted packets for nearby nodes",
                    checked = isRelayEnabled,
                    onCheckedChange = { isRelayEnabled = it }
                )
                SettingSwitchRow(
                    title = "BLE Advertising",
                    subtitle = "Allow nearby nodes to discover your beacon",
                    checked = isBleAdvEnabled,
                    onCheckedChange = { isBleAdvEnabled = it }
                )
                SettingSwitchRow(
                    title = "Auto-prune Expired Packets",
                    subtitle = "Delete TTL-expired multi-hop packets",
                    checked = isAutoPruneEnabled,
                    onCheckedChange = { isAutoPruneEnabled = it }
                )
            }

            Spacer(Modifier.height(20.dp))

            // Navigation Sections
            Text("Data & Security", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark)
                    .border(1.dp, DividerColor, RoundedCornerShape(12.dp))
            ) {
                SettingNavRow(
                    icon = "💾",
                    title = "Storage & Media Breakdown",
                    subtitle = "Cache and message database",
                    onClick = { viewModel.navigateTo(Screen.Storage) }
                )
                SettingNavRow(
                    icon = "🔑",
                    title = "View Seed Phrase Backup",
                    subtitle = "12-word cryptographic recovery key",
                    onClick = { viewModel.navigateTo(Screen.MnemonicDisplay) }
                )
            }

            Spacer(Modifier.height(28.dp))

            // Panic Button
            Button(
                onClick = { viewModel.showPanicDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed.copy(alpha = 0.15f), contentColor = DangerRed),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .border(1.dp, DangerRed.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            ) {
                Text("⚠️ Emergency Panic Wipe", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrimaryBlue,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = BackgroundDark
            )
        )
    }
}

@Composable
fun SettingNavRow(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 18.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = TextSecondary, fontSize = 11.sp)
            }
        }
        Text("→", color = TextSecondary, fontSize = 14.sp)
    }
}
