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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.chat.model.PeerDevice
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
import com.app.chat.ui.components.MeshNetworkGraphic
import com.app.chat.ui.components.RadarPulseGraphic
import com.app.chat.viewmodel.ChatViewModel

@Composable
fun NearbyRadarScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler { viewModel.handleBack() }

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
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // Top Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Nearby Radar",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedButton(
                    onClick = { viewModel.navigateTo(Screen.MeshGraph) },
                    shape = RoundedCornerShape(20.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(DividerColor))
                ) {
                    Text("Mesh Graph", color = PrimaryBlue, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            if (!viewModel.isBluetoothEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DangerRed.copy(alpha = 0.15f))
                        .border(1.dp, DangerRed.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .clickable { viewModel.requestEnableBluetooth() }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Text("📡", fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Bluetooth is Turned OFF", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Tap to turn ON and discover nearby nodes", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                    Button(
                        onClick = { viewModel.requestEnableBluetooth() },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Turn ON", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(SurfaceDark.copy(alpha = 0.5f))
                    .border(1.dp, DividerColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                RadarPulseGraphic(
                    isScanning = viewModel.isScanningNearby,
                    modifier = Modifier.size(112.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (viewModel.isScanningNearby) "Scanning for NetChat mobile mesh nodes..." else "Scan paused",
                color = TextSecondary,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(12.dp))

            // Found Peers List
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Discovered Nodes (${viewModel.nearbyPeers.size})",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (viewModel.isScanningNearby) "● Live (Tap to Pause)" else "○ Paused (Tap to Scan)",
                    color = if (viewModel.isScanningNearby) Color(0xFF00E676) else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { viewModel.toggleScanning() }
                )
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (viewModel.nearbyPeers.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📡", fontSize = 28.sp)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "No NetChat nodes detected yet",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Make sure the other phone has NetChat running with Bluetooth enabled.",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
                items(viewModel.nearbyPeers, key = { it.id }) { peer ->
                    DiscoveredPeerRow(
                        peer = peer,
                        onChatClick = { viewModel.openDirectChatWithPeer(peer) }
                    )
                }
            }
        }
    }
}

@Composable
fun DiscoveredPeerRow(
    peer: PeerDevice,
    onChatClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .border(1.dp, DividerColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarView(
                initials = peer.name.take(1),
                size = 40,
                isOnline = true,
                showOnlineBadge = true
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = peer.name,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${peer.distanceText} • ${peer.rssi} dBm",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Button(
            onClick = onChatClick,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            shape = RoundedCornerShape(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text("Chat", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun CreateGroupScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler { viewModel.handleBack() }

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
        ) {
            Spacer(Modifier.height(16.dp))

            Text(
                text = "New Mesh Group",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Packets are broadcasted and multi-hop relayed across all peers",
                color = TextSecondary,
                fontSize = 13.sp
            )

            Spacer(Modifier.height(20.dp))

            // Group Name Input
            Column {
                Text("Group Name", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
                        value = viewModel.newGroupName,
                        onValueChange = { viewModel.newGroupName = it },
                        textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(PrimaryBlue),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (viewModel.newGroupName.isEmpty()) {
                                Text("e.g. Hike Rescue Team", color = TextSecondary, fontSize = 14.sp)
                            }
                            innerTextField()
                        }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Select In-Range Nodes (${viewModel.selectedGroupPeerIds.size} selected)",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(viewModel.nearbyPeers, key = { it.id }) { peer ->
                    val isSelected = viewModel.selectedGroupPeerIds.contains(peer.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceDark)
                            .border(1.dp, if (isSelected) PrimaryBlue else DividerColor, RoundedCornerShape(12.dp))
                            .clickable { viewModel.togglePeerSelection(peer.id) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AvatarView(initials = peer.name.take(1), size = 38)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(peer.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(peer.distanceText, color = TextSecondary, fontSize = 12.sp)
                            }
                        }

                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { viewModel.togglePeerSelection(peer.id) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = PrimaryBlue,
                                uncheckedColor = TextSecondary,
                                checkmarkColor = Color.White
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = { viewModel.createGroup() },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Create Encrypted Mesh Group", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun MeshGraphScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler { viewModel.handleBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(20.dp)
    ) {
        Spacer(Modifier.height(10.dp))

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
                Text("Mesh Topology Graph", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Real-time multi-hop Bluetooth routing", color = TextSecondary, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(24.dp))

        // Visual Graph
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .border(1.dp, DividerColor, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            MeshNetworkGraphic(modifier = Modifier.size(230.dp))
        }

        Spacer(Modifier.height(20.dp))

        Text("Active Routes & Relays", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))

        if (viewModel.nearbyPeers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📡", fontSize = 28.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("No Mesh Nodes in Direct Range", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "When other devices run NetChat nearby, their cryptographic routing hops will appear here in real-time.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                viewModel.nearbyPeers.forEach { peer ->
                    val isNetChat = peer.name.contains("Mesh") || peer.name.contains("NetChat")
                    val via = if (isNetChat) "Direct BLE Link" else "BLE Beacon"
                    RouteRow(
                        from = "You (${viewModel.userProfile.displayName.take(12)})",
                        via = via,
                        to = peer.name,
                        status = "1 Hop • ${peer.rssi} dBm"
                    )
                }
            }
        }
    }
}

@Composable
fun RouteRow(from: String, via: String, to: String, status: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("$from → $to", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(via, color = PrimaryBlue, fontSize = 11.sp)
        }
        Text(status, color = TextSecondary, fontSize = 11.sp)
    }
}
