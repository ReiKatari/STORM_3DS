package com.storm3ds.emulator.ui.settings.fragments

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.storm3ds.emulator.NativeCoreLoader
import com.storm3ds.emulator.R
import com.storm3ds.emulator.nativebridge.NativeBridgeHost
import com.storm3ds.emulator.nativebridge.NativeLibrary
import com.storm3ds.emulator.ui.settings.PreferenceFragmentTitleProvider
import com.storm3ds.emulator.ui.settings.showSystem3dsSetupDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@AndroidEntryPoint
class CustomFirmwarePreferencesFragment : BasePreferenceFragment(), PreferenceFragmentTitleProvider {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_custom_firmware, rootKey)

        findPreference<Preference>("system_3ds_setup")?.setOnPreferenceClickListener {
            showSystem3dsSetupDialog(requireActivity())
            true
        }

        findPreference<Preference>("3ds_system_files_check")?.setOnPreferenceClickListener {
            val ctx = context ?: return@setOnPreferenceClickListener true
            lifecycleScope.launch(Dispatchers.IO) {
                val report = runCatching {
                    NativeCoreLoader.load()
                    val userDir = NativeBridgeHost.resolveUserDirectoryPath()
                    val sysDir = File(userDir, "sysdata")
                    val nandDir = File(userDir, "nand")
                    val sdmcDir = File(userDir, "sdmc/Nintendo 3DS")
                    val boot9Ok = File(sysDir, "boot9.bin").let { it.exists() && it.length() > 0 }
                    val aesOk = File(sysDir, "aes_keys.txt").let { it.exists() && it.length() > 0 } ||
                        File(sysDir, "keys.txt").let { it.exists() && it.length() > 0 }
                    val seedOk = File(sysDir, "seeddb.bin").let { it.exists() && it.length() > 0 }
                    val homeMenuOk = runCatching { NativeLibrary.getHomeMenuPath(-1).isNotBlank() }.getOrDefault(false)
                    val ok = getString(R.string.setup_3ds_key_ok)
                    val miss = getString(R.string.setup_3ds_key_missing)
                    getString(
                        R.string.pref_3ds_system_files_report,
                        userDir,
                        if (boot9Ok) ok else miss,
                        if (aesOk) ok else miss,
                        if (seedOk) ok else miss,
                        if (homeMenuOk) ok else miss,
                        if (nandDir.exists()) nandDir.absolutePath else "-",
                        if (sdmcDir.exists()) sdmcDir.absolutePath else File(userDir, "sdmc").absolutePath,
                    )
                }.getOrElse { it.localizedMessage ?: "Error" }

                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    AlertDialog.Builder(ctx)
                        .setTitle(R.string.pref_3ds_check_system_files_title)
                        .setMessage(report)
                        .setPositiveButton(R.string.home_menu_3ds_open_setup) { _, _ ->
                            showSystem3dsSetupDialog(requireActivity())
                        }
                        .setNegativeButton(R.string.ok, null)
                        .show()
                }
            }
            true
        }
    }

    override fun getTitle() = getString(R.string.pref_3ds_setup_title)
}
