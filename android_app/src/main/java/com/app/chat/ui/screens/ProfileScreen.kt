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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.chat.theme.BackgroundDark
import com.app.chat.theme.DividerColor
import com.app.chat.theme.PrimaryBlue
import com.app.chat.theme.SurfaceDark
import com.app.chat.theme.TextPrimary
import com.app.chat.theme.TextSecondary
import com.app.chat.ui.components.AvatarView
import com.app.chat.viewmodel.ChatViewModel

@Composable
fun ProfileScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit = { viewModel.handleBack() },
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var editName by remember { mutableStateOf(viewModel.userProfile.displayName) }
    var editBio by remember { mutableStateOf(viewModel.userProfile.bio) }
    var isDiscoveryEnabled by remember { mutableStateOf(viewModel.userProfile.isDiscoveryEnabled) }

    if (viewModel.showQrDialog) {
        QrCodeDialog(
            name = viewModel.userProfile.displayName,
            publicKey = viewModel.userProfile.publicKey,
            onDismiss = { viewModel.showQrDialog = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
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
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Text("←", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Node Identity", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Cryptographic BLE Profile", color = TextSecondary, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(28.dp))

        // Avatar Header
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AvatarView(
                initials = if (editName.isNotBlank()) editName.take(1) else "N",
                size = 76,
                isOnline = true,
                showOnlineBadge = true
            )
            Spacer(Modifier.height(12.dp))
            Text(editName, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "ID: ${viewModel.userProfile.id.take(12)}...",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(28.dp))

        // Profile Form Fields
        Text("Display Name", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark)
                .border(1.dp, DividerColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = editName,
                onValueChange = { text: String -> editName = text },
                textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(PrimaryBlue),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        Spacer(Modifier.height(16.dp))

        Text("Status / Bio", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark)
                .border(1.dp, DividerColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = editBio,
                onValueChange = { text: String -> editBio = text },
                textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(PrimaryBlue),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        Spacer(Modifier.height(16.dp))

        // Public Key Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark)
                .border(1.dp, DividerColor, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Public Encryption Key (Curve25519)", color = TextSecondary, fontSize = 11.sp)
                Text(
                    "Copy",
                    color = PrimaryBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        clipboardManager.setText(AnnotatedString(viewModel.userProfile.publicKey))
                        Toast.makeText(context, "Public key copied!", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = viewModel.userProfile.publicKey,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(16.dp))

        // Discovery Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark)
                .border(1.dp, DividerColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Visible in Nearby Radar", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("Allow other offline peers to scan and chat with you", color = TextSecondary, fontSize = 11.sp)
            }
            Switch(
                checked = isDiscoveryEnabled,
                onCheckedChange = { isDiscoveryEnabled = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = PrimaryBlue,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = BackgroundDark
                )
            )
        }

        Spacer(Modifier.height(20.dp))

        // Show QR Code Button
        OutlinedButton(
            onClick = { viewModel.showQrDialog = true },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("📷 Show Offline Pairing QR Code", color = PrimaryBlue, fontSize = 14.sp)
        }

        Spacer(Modifier.height(12.dp))

        // Save Button
        Button(
            onClick = {
                viewModel.updateProfile(editName, isDiscoveryEnabled, editBio)
                Toast.makeText(context, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                onBack()
            },
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Save Profile", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun QrCodeDialog(
    name: String,
    publicKey: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Text("Offline Pairing QR", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Have a friend scan this QR code to establish an instant direct Bluetooth mesh link without discovery scanning.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))

                // Simulated QR Graphic
                Box(
                    modifier = Modifier
                        .size(170.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            QrCornerBox()
                            QrCornerBox()
                        }
                        Text("NETCHAT\nOFFLINE MESH", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            QrCornerBox()
                            Box(modifier = Modifier.size(16.dp).background(Color.Black))
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(publicKey, color = PrimaryBlue, fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = PrimaryBlue)
            }
        }
    )
}

@Composable
fun QrCornerBox() {
    Box(
        modifier = Modifier
            .size(32.dp)
            .border(4.dp, Color.Black, RoundedCornerShape(4.dp))
            .padding(4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black))
    }
}
