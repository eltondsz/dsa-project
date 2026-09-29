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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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
import com.app.chat.theme.WhiteButton
import com.app.chat.theme.WhiteButtonText
import com.app.chat.ui.components.MeshNetworkGraphic

@Composable
fun SplashScreen(
    onContinue: () -> Unit,
    onImportPhrase: () -> Unit = onContinue,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .clickable(onClick = onContinue)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MeshNetworkGraphic(modifier = Modifier.size(170.dp))
            Spacer(Modifier.height(36.dp))
            Text(
                text = "NetChat",
                color = TextPrimary,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Decentralized BLE Mesh Messenger",
                color = TextSecondary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Zero Internet • AES-256 Encrypted • Multi-hop",
                color = PrimaryBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue, contentColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("Get Started", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Import Existing Seed Phrase",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier
                    .clickable(onClick = onImportPhrase)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
fun OnboardingScreen(
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "← Back",
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.clickable(onClick = onBack)
            )
            Text(
                text = "Skip",
                color = PrimaryBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onSkip)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MeshNetworkGraphic(modifier = Modifier.size(200.dp))
            Spacer(Modifier.height(32.dp))
            Text(
                text = "Connect Around You",
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "NetChat uses Bluetooth Low Energy mesh routing to relay messages through nearby peers — keeping communications alive even in complete network blackouts.",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.widthIn(max = 320.dp)
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(containerColor = WhiteButton, contentColor = WhiteButtonText),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("Continue", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun PermissionScreen(
    onAllow: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "← Back",
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.clickable(onClick = onBack)
            )
            Text(
                text = "Skip",
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.clickable(onClick = onSkip)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(SurfaceDark)
                    .border(1.dp, DividerColor, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("📡", fontSize = 46.sp)
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = "Bluetooth & Nearby Devices",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "To scan, advertise, and relay encrypted packets over the local mesh network, NetChat needs Bluetooth and Nearby permissions.",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.widthIn(max = 310.dp)
            )

            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark)
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔒", fontSize = 18.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "All messages stay 100% on your device and are encrypted with AES-256 before transmission.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onAllow,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("Allow Bluetooth Access", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Not now (Use limited mode)",
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable(onClick = onSkip)
                    .padding(6.dp)
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun MnemonicDisplayScreen(
    words: List<String>,
    onSaved: () -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "← Back",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable(onClick = onBack)
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Secret Recovery Phrase",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Write down or copy these 12 words in order. This phrase is your only key to recover your identity and decrypt stored chats.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(24.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(words) { index, word ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceDark)
                            .border(1.dp, DividerColor, RoundedCornerShape(10.dp))
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(22.dp)
                        )
                        Text(
                            text = word,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = {
                    val fullPhrase = words.joinToString(" ")
                    clipboardManager.setText(AnnotatedString(fullPhrase))
                    Toast.makeText(context, "Seed phrase copied to clipboard!", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("📋 Copy to Clipboard", color = PrimaryBlue, fontSize = 14.sp)
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onSaved,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("I've Saved My Phrase", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}
