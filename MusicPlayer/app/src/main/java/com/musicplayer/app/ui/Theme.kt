package com.musicplayer.app.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/** Laranja da marca. No tema escuro é a cor principal; no claro, só para ícones sobre fundo escuro. */
val Accent = Color(0xFFFF5A1F)

/** Laranja mais fechado para o tema claro: texto branco sobre ele (5,2:1) e ele como texto (4,8:1) passam na WCAG AA. */
private val AccentOnLight = Color(0xFFC2410C)

private val Light = lightColorScheme(
    primary = AccentOnLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3D6),
    onPrimaryContainer = Color(0xFF5C1D00),
    secondaryContainer = Color(0xFFFFE3D6),
    onSecondaryContainer = Color(0xFF5C1D00),
    background = Color(0xFFF7F7F8),
    onBackground = Color(0xFF151515),
    surface = Color(0xFFF7F7F8),
    onSurface = Color(0xFF151515),
    surfaceVariant = Color(0xFFECECEF),
    onSurfaceVariant = Color(0xFF616166), // 5,2:1 até sobre as barras cinza
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F7F8),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFF0F0F2),
    surfaceTint = Color.Transparent,
    outline = Color(0xFFC8C8CC),
)

private val Dark = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF1A0A00), // texto quase preto sobre o laranja vivo: 6,2:1 (branco dava 3,1:1)
    primaryContainer = Color(0xFF4A2312),
    onPrimaryContainer = Color(0xFFFFDBCB),
    secondaryContainer = Color(0xFF3D2216),
    onSecondaryContainer = Color(0xFFFFC2A8),
    background = Color(0xFF0E0E10),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF0E0E10),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF1E1E21),
    onSurfaceVariant = Color(0xFFA0A0A6),
    surfaceContainerLowest = Color(0xFF0A0A0B),
    surfaceContainerLow = Color(0xFF141416),
    surfaceContainer = Color(0xFF1A1A1D),
    surfaceContainerHigh = Color(0xFF222225),
    surfaceContainerHighest = Color(0xFF2A2A2E),
    surfaceTint = Color.Transparent,
    outline = Color(0xFF4A4A50),
)

@Composable
fun MusicTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) Dark else Light
    MaterialTheme(colorScheme = colors) {
        // Cor padrão de textos e ícones que não definem cor própria
        CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
    }
}
