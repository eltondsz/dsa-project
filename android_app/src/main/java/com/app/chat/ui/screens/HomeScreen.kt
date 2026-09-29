package com.app.chat.ui.screens

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.chat.model.ChatConversation
import com.app.chat.model.Screen
import com.app.chat.theme.BackgroundDark
import com.app.chat.theme.DividerColor
import com.app.chat.theme.PrimaryBlue
import com.app.chat.theme.SurfaceDark
import com.app.chat.theme.TextPrimary
import com.app.chat.theme.TextSecondary
import com.app.chat.ui.components.AvatarView
import com.app.chat.ui.components.BottomNavigationBar
import com.app.chat.ui.components.SearchInputField
import com.app.chat.ui.components.UnreadBadgeView
import com.app.chat.viewmodel.ChatViewModel

@Composable
fun HomeScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val filteredConversations = viewModel.conversations.filter {
        it.name.contains(viewModel.searchQuery, ignoreCase = true) ||
            it.lastMessage.contains(viewModel.searchQuery, ignoreCase = true)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BackgroundDark,
        bottomBar = {
            BottomNavigationBar(
                currentTab = viewModel.activeTab,
                onTabSelected = { viewModel.selectTab(it) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.navigateTo(Screen.Nearby) },
                containerColor = PrimaryBlue,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text("+", fontSize = 28.sp, fontWeight = FontWeight.Light)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "NetChat",
                        color = TextPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Offline Bluetooth Mesh",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceDark)
                        .border(1.dp, DividerColor, RoundedCornerShape(20.dp))
                        .clickable { viewModel.navigateTo(Screen.Nearby) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E676))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${viewModel.nearbyPeers.size} Nodes",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Search Bar
            SearchInputField(
                query = viewModel.searchQuery,
                onQueryChange = { viewModel.searchQuery = it },
                placeholder = "Search chats or peers..."
            )

            Spacer(Modifier.height(14.dp))

            // Mesh Routing Status Ribbon
            val meshStatusText = if (viewModel.nearbyPeers.isEmpty()) {
                if (viewModel.isBluetoothEnabled) "BLE Mesh active: Scanning for nodes..." else "Bluetooth is OFF: Tap to enable"
            } else {
                "Mesh active: ${viewModel.nearbyPeers.size} node(s) in direct range"
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceDark.copy(alpha = 0.6f))
                    .clickable {
                        if (!viewModel.isBluetoothEnabled) {
                            viewModel.requestEnableBluetooth()
                        } else {
                            viewModel.navigateTo(Screen.MeshGraph)
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(if (viewModel.isBluetoothEnabled) "⚡" else "📡", fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = meshStatusText,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = if (viewModel.isBluetoothEnabled) "View Graph →" else "Turn ON →",
                    color = PrimaryBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(12.dp))

            // Conversation List
            if (filteredConversations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(SurfaceDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("💬", fontSize = 32.sp)
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = if (viewModel.searchQuery.isEmpty()) "No Conversations Yet" else "No matching chats",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (viewModel.searchQuery.isEmpty())
                                "NetChat works 100% offline. Scan for nearby Bluetooth devices around you to start an encrypted direct chat."
                            else "No chat matches \"${viewModel.searchQuery}\"",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        if (viewModel.searchQuery.isEmpty()) {
                            Spacer(Modifier.height(18.dp))
                            Button(
                                onClick = { viewModel.navigateTo(Screen.Nearby) },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("📡 Discover Nearby Peers", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredConversations, key = { it.id }) { conv ->
                        ConversationRowItem(
                            conversation = conv,
                            onClick = { viewModel.openConversation(conv.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationRowItem(
    conversation: ChatConversation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarView(
            initials = conversation.avatarInitials,
            size = 46,
            isOnline = conversation.isOnline,
            showOnlineBadge = true
        )

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.name,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = conversation.lastTimestamp,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.lastMessage,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (conversation.unreadCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    UnreadBadgeView(count = conversation.unreadCount)
                }
            }
        }
    }
}
