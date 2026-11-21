package com.storm3ds.emulator.ui.settings.vulkandriver

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import com.storm3ds.emulator.ui.theme.AppThemeManager
import com.storm3ds.emulator.ui.theme.MelonTheme

import com.storm3ds.emulator.extensions.applyImmersiveFullscreen

@AndroidEntryPoint
class VulkanDriverManagerActivity : AppCompatActivity() {

    private val viewModel by viewModels<VulkanDriverManagerViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemeManager.applyEdgeToEdgeTheme(this)
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        setContent {
            MelonTheme {
                VulkanDriverManagerScreen(
                    viewModel = viewModel,
                    onBackClick = { finish() }
                )
            }
        }
    }
}
