package com.musicplayer.app.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Força ícones claros na barra de status enquanto uma tela de fundo escuro estiver aberta. */
@Composable
fun LightStatusBarIcons() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        val previous = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = false
        onDispose { controller.isAppearanceLightStatusBars = previous }
    }
}

/** Ícones da barra de status escuros (fundo claro) ou claros (fundo escuro) enquanto a tela estiver aberta. */
@Composable
fun StatusBarIcons(dark: Boolean) {
    val view = LocalView.current
    DisposableEffect(dark) {
        val window = (view.context as? Activity)?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        val previous = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = dark
        onDispose { controller.isAppearanceLightStatusBars = previous }
    }
}
