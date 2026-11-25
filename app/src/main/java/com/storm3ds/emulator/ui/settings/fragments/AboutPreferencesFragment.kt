package com.storm3ds.emulator.ui.settings.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import com.storm3ds.emulator.R
import com.storm3ds.emulator.ui.settings.PreferenceFragmentTitleProvider

@AndroidEntryPoint
class AboutPreferencesFragment : BasePreferenceFragment(), PreferenceFragmentTitleProvider {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_about, rootKey)

        val vName = (runCatching { requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName }.getOrNull() ?: com.storm3ds.emulator.BuildConfig.VERSION_NAME)
            .removePrefix("v")
            .removePrefix("V")
        findPreference<Preference>("about_version")?.summary = vName

        findPreference<Preference>("manual_check_updates")?.setOnPreferenceClickListener {
            com.storm3ds.emulator.ui.settings.ManualUpdateChecker.checkNow(activity ?: requireContext())
            true
        }

        val actManager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        val totalRamGb = memInfo.totalMem / (1024 * 1024 * 1024.0)
        val availRamGb = memInfo.availMem / (1024 * 1024 * 1024.0)
        val ramFormatted = String.format(java.util.Locale.US, "%.1f / %.1f GB", availRamGb, totalRamGb)

        val pm = requireContext().packageManager
        val vulkanSupported = pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)
        val glesVersion = actManager?.deviceConfigurationInfo?.glEsVersion ?: "3.2"
        val storagePath = requireContext().getExternalFilesDir(null)?.absolutePath ?: requireContext().filesDir.absolutePath

        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.takeIf { !it.isNullOrBlank() } ?: Build.HARDWARE
        } else {
            Build.HARDWARE
        }
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"

        findPreference<Preference>("about_copy_diagnostics")?.summary =
            "${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) • RAM: $ramFormatted • Vulkan: ${getString(if (vulkanSupported) R.string.yes else R.string.no)}"

        findPreference<Preference>("about_copy_diagnostics")?.setOnPreferenceClickListener {
            val vulkanStatus = if (vulkanSupported) {
                getString(R.string.v780_diagnostics_vulkan_supported)
            } else {
                getString(R.string.v780_diagnostics_vulkan_unsupported)
            }

            val diagnosticsText = buildString {
                appendLine("STORM 3DS $vName")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
                appendLine("SoC: $soc (Hardware: ${Build.HARDWARE})")
                appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("ABI: $abi (Supported: ${Build.SUPPORTED_ABIS.joinToString(", ")})")
                appendLine("RAM: $ramFormatted (Available / Total)")
                appendLine("OpenGL ES: $glesVersion")
                appendLine("Vulkan: $vulkanStatus")
                appendLine("Storage: $storagePath")
            }.trimEnd()

            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null) {
                val clip = ClipData.newPlainText(getString(R.string.about_diagnostics_title), diagnosticsText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(
                    requireContext(),
                    R.string.v780_about_copy_diagnostics_success,
                    Toast.LENGTH_SHORT
                ).show()
            }
            true
        }

        findPreference<Preference>("about_fork_info")?.setOnPreferenceClickListener {
            openExternalUrl("https://github.com/azahar-emu/azahar")
            true
        }

        findPreference<Preference>("about_github")?.setOnPreferenceClickListener {
            openExternalUrl("https://github.com/ReiKatari/STORM_3DS")
            true
        }

        findPreference<Preference>("credits_azahar")?.setOnPreferenceClickListener {
            openExternalUrl("https://github.com/azahar-emu/azahar")
            true
        }

        findPreference<Preference>("credits_citra_mmj")?.setOnPreferenceClickListener {
            openExternalUrl("https://github.com/weihuoya/citra")
            true
        }

        findPreference<Preference>("credits_borked3ds")?.setOnPreferenceClickListener {
            openExternalUrl("https://github.com/Borked3DS/Borked3DS")
            true
        }

        findPreference<Preference>("credits_azaharplus")?.setOnPreferenceClickListener {
            openExternalUrl("https://github.com/AzaharPlus/AzaharPlus")
            true
        }

        findPreference<Preference>("credits_watermelonds")?.setOnPreferenceClickListener {
            openExternalUrl("https://github.com/SapphireRhodonite/WatermelonDS")
            true
        }

        findPreference<Preference>("credits_libraries")?.setOnPreferenceClickListener {
            val ctx = activity ?: requireContext()
            val libsText =
                "- Azahar & Citra Core (C++20 Nintendo 3DS Engine)\n" +
                "- Citra MMJ (weihuoya - Speedhacks, Cheat DB & Memory Tools)\n" +
                "- Borked3DS (SPIRV-Tools-opt Shader Optimization)\n" +
                "- AzaharPlus, Lime3DS & Mandarine (Decryption & Formats)\n" +
                "- WatermelonDS (SapphireRhodonite - Android UI Interface)\n" +
                "- Librashader (SnowflakePowered - RetroArch .slangp Shaders)\n" +
                "- libadrenotools (Bylaws - Custom Turnip Vulkan Drivers)\n" +
                "- Dynarmic, Teakra, SoundTouch, Cubeb, Zstandard, Crypto++\n" +
                "- SPIRV-Tools, Glslang & Vulkan Memory Allocator (VMA)\n" +
                "- Google ML Kit Text Recognition & Translation\n" +
                "- Jetpack Compose, Material, AndroidX, Hilt, Room & Coil\n" +
                "- Apache Commons Compress, junrar, XZ, OkHttp & Retrofit"
            com.storm3ds.emulator.ui.updates.showStormUpdateInfoDialog(
                context = ctx,
                title = getString(R.string.about_credits_libraries_title),
                message = libsText,
            )
            true
        }
    }

    private fun openExternalUrl(url: String) {
        val ctx = context ?: return
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(ctx, getString(R.string.about_no_browser_toast), Toast.LENGTH_SHORT).show()
        }
    }

    override fun getTitle(): String = getString(R.string.about_app_title)
}
