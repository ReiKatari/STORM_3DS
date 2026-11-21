package com.storm3ds.emulator.ui.shortcutsetup

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.storm3ds.emulator.R
import com.storm3ds.emulator.domain.model.ConsoleType
import com.storm3ds.emulator.ui.emulator.EmulatorActivity

abstract class FirmwareShortcutSetupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(com.storm3ds.emulator.ui.theme.AppThemeManager.currentTheme.getThemeResId())
        super.onCreate(savedInstanceState)

        val console = getConsoleType()
        val intent = Intent("${packageName}.LAUNCH_FIRMWARE").apply {
            setPackage(packageName)
            putExtra(EmulatorActivity.KEY_BOOT_FIRMWARE_ONLY, true)
            putExtra(EmulatorActivity.KEY_BOOT_FIRMWARE_CONSOLE, console.ordinal)
        }

        val shortcutInfo = ShortcutInfoCompat.Builder(this, console.name.lowercase())
            .setShortLabel(getConsoleName(console))
            .setIcon(getConsoleIcon(console))
            .setIntent(intent)
            .build()

        val shortcutIntent = ShortcutManagerCompat.createShortcutResultIntent(this, shortcutInfo)

        setResult(Activity.RESULT_OK, shortcutIntent)
        finish()
    }

    private fun getConsoleName(consoleType: ConsoleType): String {
        return when (consoleType) {
            ConsoleType.DS -> getString(R.string.console_ds_full)
            ConsoleType.DSi -> getString(R.string.console_dsi_full)
        }
    }

    private fun getConsoleIcon(consoleType: ConsoleType): IconCompat {
        return when (consoleType) {
            ConsoleType.DS -> IconCompat.createWithResource(this, R.mipmap.ic_platform_ds)
            ConsoleType.DSi -> IconCompat.createWithResource(this, R.mipmap.ic_platform_dsi)
        }
    }

    abstract fun getConsoleType(): ConsoleType
}