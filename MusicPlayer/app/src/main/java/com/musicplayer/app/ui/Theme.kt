package com.musicplayer.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Cores de destaque que o usuário pode escolher.
 * [bright] é a cor principal no tema escuro (e o fundo do botão de play, sempre com ícone preto);
 * [onLight] é um tom mais fechado, para texto colorido continuar legível sobre o fundo branco.
 */
enum class AccentColor(val label: String, val bright: Color, val onLight: Color) {
    GREEN("Verde", Color(0xFFC7F43D), Color(0xFF547C00)),
    TEAL("Turquesa", Color(0xFF5EEAD4), Color(0xFF00796B)),
    BLUE("Azul", Color(0xFF7CC4FF), Color(0xFF0061A4)),
    PURPLE("Roxo", Color(0xFFC9A7FF), Color(0xFF6B4BB8)),
    PINK("Rosa", Color(0xFFFF8FC7), Color(0xFFB0246E)),
    RED("Vermelho", Color(0xFFFF8A80), Color(0xFFB3261E)),
    ORANGE("Laranja", Color(0xFFFFB35C), Color(0xFF9A4F00)),
}

/** Cor de destaque viva atual (a [AccentColor.bright] escolhida), disponível em qualquer tema. */
val LocalAccent = staticCompositionLocalOf { AccentColor.GREEN.bright }

private val Light = lightColorScheme(
    primary = AccentColor.GREEN.onLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F7BC),
    onPrimaryContainer = Color(0xFF263900),
    secondaryContainer = Color(0xFFE8F7BC),
    onSecondaryContainer = Color(0xFF263900),
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
    primary = AccentColor.GREEN.bright,
    onPrimary = Color(0xFF000000), // texto preto sobre o verde, como nos botões do Spotify
    primaryContainer = Color(0xFF263414),
    onPrimaryContainer = Color(0xFFDDF594),
    secondaryContainer = Color(0xFF202D18),
    onSecondaryContainer = Color(0xFFDDF594),
    background = Color(0xFF060C0A),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF060C0A),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF15201B),
    onSurfaceVariant = Color(0xFFA0A0A6),
    surfaceContainerLowest = Color(0xFF0A0A0B),
    surfaceContainerLow = Color(0xFF141416),
    surfaceContainer = Color(0xFF101B16),
    surfaceContainerHigh = Color(0xFF222225),
    surfaceContainerHighest = Color(0xFF2A2A2E),
    surfaceTint = Color.Transparent,
    outline = Color(0xFF4A4A50),
)

/** Esquema claro com a cor escolhida; o verde usa os valores originais, ajustados à mão. */
private fun lightScheme(accent: AccentColor): ColorScheme {
    if (accent == AccentColor.GREEN) return Light
    val container = lerp(Color.White, accent.onLight, 0.16f)
    val onContainer = lerp(accent.onLight, Color.Black, 0.55f)
    return Light.copy(
        primary = accent.onLight,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        secondaryContainer = container,
        onSecondaryContainer = onContainer,
    )
}

/** Esquema escuro com a cor escolhida; fundos levemente tingidos pela cor, como no verde original. */
private fun darkScheme(accent: AccentColor): ColorScheme {
    if (accent == AccentColor.GREEN) return Dark
    val c = accent.bright
    val background = lerp(Color(0xFF060606), c, 0.03f)
    val onContainer = lerp(c, Color.White, 0.45f)
    return Dark.copy(
        primary = c,
        primaryContainer = lerp(Color(0xFF101010), c, 0.18f),
        onPrimaryContainer = onContainer,
        secondaryContainer = lerp(Color(0xFF101010), c, 0.13f),
        onSecondaryContainer = onContainer,
        background = background,
        surface = background,
        surfaceVariant = lerp(Color(0xFF161616), c, 0.06f),
        surfaceContainer = lerp(Color(0xFF111111), c, 0.05f),
    )
}

@Composable
fun MusicTheme(dark: Boolean, accent: AccentColor = AccentColor.GREEN, content: @Composable () -> Unit) {
    val colors = if (dark) darkScheme(accent) else lightScheme(accent)
    MaterialTheme(colorScheme = colors) {
        // Cor padrão de textos e ícones que não definem cor própria
        CompositionLocalProvider(
            LocalContentColor provides colors.onBackground,
            LocalAccent provides accent.bright,
            content = content,
        )
    }
}
