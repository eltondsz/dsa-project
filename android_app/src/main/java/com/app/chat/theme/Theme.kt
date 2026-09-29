package com.app.chat.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BackgroundDark = Color(0xFF090D10)
val SurfaceDark = Color(0xFF171D22)
val SurfaceNavbar = Color(0xFF0D1216)
val PrimaryBlue = Color(0xFF1687FF)
val TextPrimary = Color(0xFFF4F6F8)
val TextSecondary = Color(0xFF9AA4AE)
val TextOnAccent = Color(0xFFDCEEFF)
val AvatarBg = Color(0xFF3A444D)
val DividerColor = Color(0xFF26292C)
val TrackColor = Color(0xFF2D353D)
val ImageContainerBg = Color(0xFF29383E)
val WhiteButton = Color.White
val WhiteButtonText = Color(0xFF101417)
val DangerRed = Color(0xFFFF5252)
val DangerContainer = Color(0xFF331B1F)

@Composable
fun NetChatTheme(content: @Composable () -> Unit) {
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
