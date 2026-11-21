package com.storm3ds.emulator.ui.layouts

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import com.storm3ds.emulator.extensions.applyImmersiveFullscreen
import com.storm3ds.emulator.ui.layouts.ui.LayoutsScreen
import com.storm3ds.emulator.ui.layouts.viewmodel.LayoutsViewModel
import com.storm3ds.emulator.ui.theme.MelonTheme

@AndroidEntryPoint
class LayoutListActivity : AppCompatActivity() {

    private val viewModel: LayoutsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        com.storm3ds.emulator.ui.theme.AppThemeManager.applyEdgeToEdgeTheme(this)
        super.onCreate(savedInstanceState)
        window.applyImmersiveFullscreen()

        setContent {
            MelonTheme {
                LayoutsScreen(
                    viewModel = viewModel,
                    onNavigateBack = ::finish,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        window.applyImmersiveFullscreen()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.applyImmersiveFullscreen()
        }
    }
}