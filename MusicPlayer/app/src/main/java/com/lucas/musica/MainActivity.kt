package com.lucas.musica

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucas.musica.ui.MusicApp
import com.lucas.musica.ui.MusicTheme
import com.lucas.musica.ui.PermissionScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MusicViewModel = viewModel()
            val dark = when (vm.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            MusicTheme(dark) {
                val context = LocalContext.current
                val audioPermission =
                    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
                    else Manifest.permission.READ_EXTERNAL_STORAGE
                var granted by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
                    )
                }
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { result -> granted = result[audioPermission] == true }

                val permissions = when {
                    Build.VERSION.SDK_INT >= 33 -> arrayOf(audioPermission, Manifest.permission.POST_NOTIFICATIONS)
                    Build.VERSION.SDK_INT <= 29 -> arrayOf(audioPermission, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    else -> arrayOf(audioPermission)
                }

                LaunchedEffect(granted) {
                    if (granted) vm.loadSongs() else launcher.launch(permissions)
                }

                if (granted) MusicApp(vm)
                else PermissionScreen(onRequest = { launcher.launch(permissions) })
            }
        }
    }
}
