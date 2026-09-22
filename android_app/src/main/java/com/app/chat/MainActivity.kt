package com.app.chat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

private val BackgroundDark = Color(0xFF090D10)
private val SurfaceDark = Color(0xFF171D22)
private val SurfaceNavbar = Color(0xFF0D1216)
private val PrimaryBlue = Color(0xFF1687FF)
private val TextPrimary = Color(0xFFF4F6F8)
private val TextSecondary = Color(0xFF9AA4AE)
private val TextOnAccent = Color(0xFFDCEEFF)
private val AvatarBg = Color(0xFF3A444D)
private val DividerColor = Color(0xFF26292C)
private val TrackColor = Color(0xFF2D353D)
private val ImageContainerBg = Color(0xFF29383E)
private val WhiteButton = Color.White
private val WhiteButtonText = Color(0xFF101417)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        setContent { NetChatTheme { PermissionGate() } }
    }
}

@Composable
private fun NetChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = BackgroundDark,
            surface = SurfaceDark,
            primary = PrimaryBlue,
            onBackground = TextPrimary,
            onSurface = TextPrimary,
            onPrimary = Color.White,
        ),
        content = content,
    )
}

@Composable
private fun PermissionGate() {
    val context = LocalContext.current
    val permissions = remember { requiredBlePermissions() }
    var hasPermissions by remember {
        mutableStateOf(permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        hasPermissions = permissions.all { results[it] == true }
    }

    NetChatApp(
        hasPermissions = hasPermissions,
        onAllowBluetooth = { launcher.launch(permissions) },
        onSkipBluetooth = { hasPermissions = true },
    )
}

@Composable
private fun NetChatApp(
    hasPermissions: Boolean,
    onAllowBluetooth: () -> Unit,
    onSkipBluetooth: () -> Unit,
) {
    var screen by rememberSaveable { mutableStateOf(SvgPage.Splash) }

    LaunchedEffect(hasPermissions) {
        if (screen == SvgPage.Permissions && hasPermissions) screen = SvgPage.Home
    }

    SvgScreen(screen) {
        when (screen) {
            SvgPage.Splash -> TapZone(0f, 0f, 1f, 1f) { screen = SvgPage.Onboarding }
            SvgPage.Onboarding -> {
                TapZone(0.04f, 0.82f, 0.92f, 0.89f) { screen = SvgPage.Permissions }
                TapZone(0.74f, 0.06f, 0.96f, 0.11f) { screen = SvgPage.Home }
            }
            SvgPage.Permissions -> {
                TapZone(0.04f, 0.82f, 0.92f, 0.89f, onAllowBluetooth)
                TapZone(0.04f, 0.90f, 0.92f, 0.96f) {
                    onSkipBluetooth()
                    screen = SvgPage.Home
                }
            }
            SvgPage.Home -> {
                TapZone(0f, 0.19f, 1f, 0.27f) { screen = SvgPage.Chat }
                BottomNavZones { screen = it }
            }
            SvgPage.Chat -> TapZone(0f, 0.04f, 0.11f, 0.11f) { screen = SvgPage.Home }
            SvgPage.Nearby -> BottomNavZones { screen = it }
            SvgPage.CreateGroup -> BottomNavZones { screen = it }
            SvgPage.GroupChat -> TapZone(0f, 0.04f, 0.11f, 0.11f) { screen = SvgPage.CreateGroup }
            SvgPage.Settings -> {
                TapZone(0f, 0.20f, 1f, 0.28f) { screen = SvgPage.Profile }
                TapZone(0f, 0.49f, 1f, 0.56f) { screen = SvgPage.Storage }
                BottomNavZones { screen = it }
            }
            SvgPage.Storage -> TapZone(0f, 0.04f, 0.11f, 0.11f) { screen = SvgPage.Settings }
            SvgPage.Profile -> TapZone(0f, 0.04f, 0.11f, 0.11f) { screen = SvgPage.Settings }
        }
    }
}

@Composable
private fun SvgScreen(page: SvgPage, overlays: @Composable BoxWithConstraintsScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                }
            },
            update = { webView ->
                val svg = webView.context.assets.open("figma/${page.asset}").bufferedReader().use { it.readText() }
                val html = """
                    <!doctype html>
                    <html>
                    <head>
                      <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
                      <style>
                        html,body{margin:0;width:100%;height:100%;background:#090D10;overflow:hidden;}
                        svg{width:100vw;height:100vh;display:block;}
                      </style>
                    </head>
                    <body>$svg</body>
                    </html>
                """.trimIndent()
                webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            },
        )
        BoxWithConstraints(modifier = Modifier.fillMaxSize(), content = overlays)
    }
}

@Composable
private fun BoxWithConstraintsScope.TapZone(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    onTap: () -> Unit,
) {
    Box(
        modifier = Modifier
            .offset(maxWidth * left, maxHeight * top)
            .width(maxWidth * (right - left))
            .height(maxHeight * (bottom - top))
            .clickable(onClick = onTap),
    )
}

@Composable
private fun BoxWithConstraintsScope.BottomNavZones(onSelected: (SvgPage) -> Unit) {
    TapZone(0.00f, 0.91f, 0.25f, 1.00f) { onSelected(SvgPage.Home) }
    TapZone(0.25f, 0.91f, 0.50f, 1.00f) { onSelected(SvgPage.Nearby) }
    TapZone(0.50f, 0.91f, 0.75f, 1.00f) { onSelected(SvgPage.CreateGroup) }
    TapZone(0.75f, 0.91f, 1.00f, 1.00f) { onSelected(SvgPage.Settings) }
}

@Composable
private fun MainShell(
    selected: Tab,
    onSelected: (Tab) -> Unit,
    onOpenChat: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when (selected) {
                Tab.Chats -> ChatsScreen(onOpenChat)
                Tab.Nearby -> NearbyScreen()
                Tab.Groups -> GroupsScreen()
                Tab.Settings -> SettingsScreen(onOpenStorage, onOpenProfile)
            }
        }
        BottomTabs(selected, onSelected)
    }
}

@Composable
private fun OnboardingScreen(onNext: () -> Unit, onSkip: () -> Unit) {
    FullScreenCenter {
        MeshGraphic(modifier = Modifier.size(180.dp))
        Spacer(Modifier.height(34.dp))
        Text("Connect Around You", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(
            "Uses Bluetooth to find and connect with people nearby.",
            color = TextSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp),
        )
        Spacer(Modifier.height(34.dp))
        PrimaryLightButton("Next", onNext)
        TextButtonLine("Skip", onSkip)
    }
}

@Composable
private fun PermissionScreen(onAllow: () -> Unit, onSkip: () -> Unit) {
    FullScreenCenter {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark),
            contentAlignment = Alignment.Center,
        ) {
            MeshGraphic(modifier = Modifier.size(58.dp))
        }
        Spacer(Modifier.height(30.dp))
        Text("Enable Bluetooth", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(
            "We need Bluetooth access to find and connect with nearby devices.",
            color = TextSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp),
        )
        Spacer(Modifier.height(30.dp))
        PrimaryLightButton("Allow Bluetooth", onAllow)
        TextButtonLine("Not now", onSkip)
    }
}

@Composable
private fun ChatsScreen(onOpenChat: () -> Unit) {
    val chats = listOf(
        ChatItem("Alex", "Meet near the gate?", "2m", 3),
        ChatItem("Hike Crew", "Maya: I packed lights", "12m", 7),
        ChatItem("Sam", "Voice message", "54m", 0),
        ChatItem("Maya", "Image", "2h", 0),
        ChatItem("Local Mesh", "4 relays active nearby", "5h", 1),
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item { TopTitle("Chats", "+") }
        item { SearchBox() }
        items(chats) { chat -> ChatRow(chat, onOpenChat) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun DirectChatScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundTextButton("<", onBack)
            Spacer(Modifier.width(12.dp))
            Avatar("A", 42)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Alex", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("Connected", color = TextSecondary, fontSize = 12.sp)
            }
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { DatePill("Today") }
            item { MessageBubble("You nearby?", "10:28", incoming = true) }
            item { MessageBubble("Yep. Mesh is holding.", "10:29  OK", incoming = false) }
            item { ImageMessage() }
            item { VoiceMessage() }
            item { MessageBubble("Sending this without internet still feels unreal.", "10:32  OK", incoming = false) }
        }
        MessageInput()
    }
}

@Composable
private fun NearbyScreen() {
    val peers = listOf(
        Peer("Alex's iPhone", "~ 5 m"),
        Peer("Sam's Android", "~ 12 m"),
        Peer("Maya's Phone", "~ 18 m"),
        Peer("Unknown Device", "~ 25 m"),
    )
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
    ) {
        item {
            TopTitle("Nearby", null)
            Text("Devices Nearby", color = TextSecondary, fontSize = 15.sp, modifier = Modifier.padding(bottom = 22.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                RadarGraphic(Modifier.size(190.dp))
            }
            Spacer(Modifier.height(26.dp))
        }
        items(peers) { peer -> PeerRow(peer.name, peer.distance) }
    }
}

@Composable
private fun GroupsScreen() {
    val members = listOf("Alex", "Maya", "Sam", "Unknown Device")
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Create Group", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text("Create", color = PrimaryBlue, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Avatar("CAM", 88)
            }
            FieldBlock("Group Name")
            Text("Add Members (Nearby)", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
        }
        items(members) { MemberRow(it, checked = it != "Unknown Device") }
    }
}

@Composable
private fun SettingsScreen(onOpenStorage: () -> Unit, onOpenProfile: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
    ) {
        item {
            TopTitle("Settings", null)
            SettingsRow("Bluetooth", "On")
            SettingsRow("My Profile", "Name", onOpenProfile)
            SettingsRow("Discovery", "Visible to nearby")
            SettingsRow("Notifications", "")
            SettingsRow("Storage", "", onOpenStorage)
            SettingsRow("About", "")
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF331B1F), contentColor = Color(0xFFFF8A8A)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text("Panic Wipe")
            }
        }
    }
}

@Composable
private fun StorageScreen(onBack: () -> Unit) {
    DetailScaffold("Storage", onBack) {
        Text("1.4 GB of 5.0 GB", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Meter(0.28f)
        Spacer(Modifier.height(24.dp))
        SettingsRow("Messages", "842 MB")
        SettingsRow("Images", "392 MB")
        SettingsRow("Voice messages", "121 MB")
        SettingsRow("Other", "45 MB")
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DarkActionButton("Clear Cache", Modifier.weight(1f))
            DarkActionButton("Manage Media", Modifier.weight(1f))
        }
        Spacer(Modifier.height(18.dp))
        Text("Messages are kept locally on this device.", color = TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun ProfileScreen(onBack: () -> Unit) {
    DetailScaffold("My Profile", onBack) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Avatar("E", 100)
            Spacer(Modifier.height(14.dp))
            Text("Elton", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text("Visible to nearby", color = TextSecondary, fontSize = 14.sp)
        }
        Spacer(Modifier.height(28.dp))
        SettingsRow("Name", "Elton")
        SettingsRow("Device", "This Device")
        SettingsRow("Discovery", "Visible")
        SettingsRow("Bio", "Share your name with nearby people.")
        Spacer(Modifier.height(18.dp))
        Text("Your profile is shared only when discovery is enabled.", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {},
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue, contentColor = Color.White),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
        ) { Text("Edit Profile") }
    }
}

@Composable
private fun DetailScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp, bottom = 22.dp)) {
            RoundTextButton("<", onBack)
            Spacer(Modifier.width(14.dp))
            Text(title, color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(content = content)
    }
}

@Composable
private fun BottomTabs(selected: Tab, onSelected: (Tab) -> Unit) {
    NavigationBar(containerColor = SurfaceNavbar, tonalElevation = 0.dp) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelected(tab) },
                icon = { Text(tab.icon, color = if (selected == tab) PrimaryBlue else TextSecondary, fontSize = 18.sp) },
                label = { Text(tab.label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PrimaryBlue,
                    selectedTextColor = TextPrimary,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                    indicatorColor = Color.Transparent,
                ),
            )
        }
    }
}

@Composable
private fun TopTitle(title: String, action: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        if (action != null) RoundTextButton(action) {}
    }
}

@Composable
private fun SearchBox() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(SurfaceDark)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("search", color = TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.width(10.dp))
        Text("Search conversations", color = TextSecondary, fontSize = 14.sp)
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ChatRow(chat: ChatItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(chat.name.take(1), 42)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(chat.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(chat.last, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(chat.time, color = TextSecondary, fontSize = 10.sp)
            if (chat.unread > 0) {
                Spacer(Modifier.height(6.dp))
                Badge(chat.unread.toString())
            }
        }
    }
    DividerLine()
}

@Composable
private fun MessageBubble(text: String, time: String, incoming: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (incoming) Arrangement.Start else Arrangement.End,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 286.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (incoming) SurfaceDark else PrimaryBlue)
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Text(text, color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                time,
                color = if (incoming) TextSecondary else TextOnAccent,
                fontSize = 9.sp,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
private fun ImageMessage() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Column(
            modifier = Modifier
                .width(210.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(SurfaceDark)
                .padding(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ImageContainerBg),
                contentAlignment = Alignment.Center,
            ) {
                Text("Image", color = TextSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text("10:30", color = TextSecondary, fontSize = 9.sp, modifier = Modifier.align(Alignment.End))
        }
    }
}

@Composable
private fun VoiceMessage() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Row(
            modifier = Modifier
                .width(230.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(SurfaceDark)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundTextButton("play") {}
            Spacer(Modifier.width(12.dp))
            Text("| || ||| || ||", color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Text("0:08", color = TextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun MessageInput() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BackgroundDark)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceDark)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text("Type a message...", color = TextSecondary, fontSize = 14.sp)
        }
        Spacer(Modifier.width(10.dp))
        RoundTextButton("send") {}
    }
}

@Composable
private fun PeerRow(name: String, distance: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name.take(1), 42)
        Spacer(Modifier.width(12.dp))
        Text(name, color = TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(distance, color = PrimaryBlue, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
    DividerLine()
}

@Composable
private fun MemberRow(name: String, checked: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name.take(1), 42)
        Spacer(Modifier.width(12.dp))
        Text(name, color = TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (checked) PrimaryBlue else Color.Transparent)
                .border(1.dp, if (checked) PrimaryBlue else TextSecondary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Text("OK", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
    DividerLine()
}

@Composable
private fun SettingsRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(SurfaceDark)
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (value.isNotBlank()) {
            Text(value, color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.End)
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun FieldBlock(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label, color = TextSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun Avatar(text: String, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(AvatarBg),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), color = TextPrimary, fontSize = (size / 3).sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Badge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(PrimaryBlue)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DatePill(text: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(SurfaceDark)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            Text(text, color = TextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun Meter(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(TrackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(PrimaryBlue),
        )
    }
}

@Composable
private fun PrimaryLightButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = WhiteButton, contentColor = WhiteButtonText),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DarkActionButton(text: String, modifier: Modifier = Modifier) {
    Button(
        onClick = {},
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark, contentColor = TextPrimary),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(text, fontSize = 13.sp)
    }
}

@Composable
private fun TextButtonLine(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = TextPrimary,
        fontSize = 15.sp,
        modifier = Modifier
            .padding(top = 18.dp)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun RoundTextButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (text == "send") PrimaryBlue else SurfaceDark)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = if (text.length > 1) 10.sp else 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FullScreenCenter(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

@Composable
private fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(DividerColor),
    )
}

@Composable
private fun MeshGraphic(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val nodes = listOf(
            center,
            Offset(size.width * 0.25f, size.height * 0.35f),
            Offset(size.width * 0.75f, size.height * 0.35f),
            Offset(size.width * 0.32f, size.height * 0.72f),
            Offset(size.width * 0.68f, size.height * 0.72f),
        )
        nodes.drop(1).forEach { drawLine(PrimaryBlue.copy(alpha = 0.55f), center, it, strokeWidth = 4f, cap = StrokeCap.Round) }
        nodes.forEachIndexed { index, node ->
            drawCircle(if (index == 0) PrimaryBlue else AvatarBg, radius = if (index == 0) 18f else 13f, center = node)
            drawCircle(Color.White.copy(alpha = 0.08f), radius = if (index == 0) 34f else 24f, center = node, style = Stroke(2f))
        }
    }
}

@Composable
private fun RadarGraphic(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        repeat(3) { index ->
            drawCircle(PrimaryBlue.copy(alpha = 0.18f - index * 0.04f), radius = 42f + index * 35f, center = center, style = Stroke(3f))
        }
        drawCircle(PrimaryBlue, radius = 13f, center = center)
        listOf(
            Offset(size.width * 0.24f, size.height * 0.42f),
            Offset(size.width * 0.73f, size.height * 0.36f),
            Offset(size.width * 0.62f, size.height * 0.78f),
        ).forEach { drawCircle(AvatarBg, radius = 10f, center = it) }
    }
}

private fun requiredBlePermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

private enum class Tab(val label: String, val icon: String) {
    Chats("Chats", "C"),
    Nearby("Nearby", "N"),
    Groups("Groups", "G"),
    Settings("Settings", "S"),
}

private enum class SvgPage(val asset: String) {
    Splash("01 Splash.svg"),
    Onboarding("02 Onboarding.svg"),
    Permissions("03 Permissions.svg"),
    Home("04 Home Chats.svg"),
    Chat("05 Chat.svg"),
    Nearby("06 Nearby Devices.svg"),
    CreateGroup("08 Create Group.svg"),
    GroupChat("09 Group Chat.svg"),
    Settings("10 Settings.svg"),
    Storage("XXXXX.svg"),
    Profile("10 My-profile.svg"),
}

private data class ChatItem(val name: String, val last: String, val time: String, val unread: Int)
private data class Peer(val name: String, val distance: String)
