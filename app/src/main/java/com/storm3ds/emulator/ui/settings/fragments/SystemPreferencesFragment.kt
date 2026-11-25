package com.storm3ds.emulator.ui.settings.fragments

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.storm3ds.emulator.R
import com.storm3ds.emulator.common.DirectoryAccessValidator
import com.storm3ds.emulator.common.UriPermissionManager
import com.storm3ds.emulator.impl.SettingsBackupManager
import com.storm3ds.emulator.ui.settings.PreferenceFragmentHelper
import com.storm3ds.emulator.ui.settings.PreferenceFragmentTitleProvider
import com.storm3ds.emulator.ui.settings.preferences.StoragePickerPreference
import javax.inject.Inject

@AndroidEntryPoint
class SystemPreferencesFragment : BasePreferenceFragment(), PreferenceFragmentTitleProvider {

    @Inject lateinit var uriPermissionManager: UriPermissionManager
    @Inject lateinit var directoryAccessValidator: DirectoryAccessValidator
    @Inject lateinit var settingsBackupManager: SettingsBackupManager
    @Inject lateinit var configurationDirectoryVerifier: com.storm3ds.emulator.domain.services.ConfigurationDirectoryVerifier
    private val helper by lazy { PreferenceFragmentHelper(this, uriPermissionManager, directoryAccessValidator) }
    private var updatingMirrorPreference = false

    private val backupInternalLayoutLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching { settingsBackupManager.backupInternalLayout(uri) }
                    .onSuccess {
                        withContext(Dispatchers.Main) {
                            if (!isAdded) return@withContext
                            AlertDialog.Builder(requireContext())
                                .setMessage(R.string.internal_layout_backup_success)
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                        }
                    }
                    .onFailure {
                        withContext(Dispatchers.Main) {
                            if (!isAdded) return@withContext
                            Toast.makeText(requireContext(), R.string.internal_layout_backup_error, Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }
    }

    private val backupExternalLayoutLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching { settingsBackupManager.backupExternalLayout(uri) }
                    .onSuccess {
                        withContext(Dispatchers.Main) {
                            if (!isAdded) return@withContext
                            AlertDialog.Builder(requireContext())
                                .setMessage(R.string.external_layout_backup_success)
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                        }
                    }
                    .onFailure {
                        withContext(Dispatchers.Main) {
                            if (!isAdded) return@withContext
                            Toast.makeText(requireContext(), R.string.external_layout_backup_error, Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }
    }

    private val restoreInternalLayoutLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching { settingsBackupManager.restoreInternalLayout(uri) }
                    .onSuccess {
                        withContext(Dispatchers.Main) {
                            if (!isAdded) return@withContext
                            AlertDialog.Builder(requireContext())
                                .setMessage(R.string.internal_layout_restore_success)
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                        }
                    }
                    .onFailure {
                        withContext(Dispatchers.Main) {
                            if (!isAdded) return@withContext
                            Toast.makeText(requireContext(), R.string.internal_layout_restore_error, Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }
    }

    private val restoreExternalLayoutLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching { settingsBackupManager.restoreExternalLayout(uri) }
                    .onSuccess {
                        withContext(Dispatchers.Main) {
                            if (!isAdded) return@withContext
                            AlertDialog.Builder(requireContext())
                                .setMessage(R.string.external_layout_restore_success)
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                        }
                    }
                    .onFailure {
                        withContext(Dispatchers.Main) {
                            if (!isAdded) return@withContext
                            Toast.makeText(requireContext(), R.string.external_layout_restore_error, Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }
    }

    override fun getTitle() = getString(R.string.category_system)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_system, rootKey)
        val jitPreference = findPreference<SwitchPreference>("enable_jit")
        val stormCompilerPreference = findPreference<SwitchPreference>("enable_storm_compiler")
        val mirrorPreference = findPreference<SwitchPreference>("save_internal_config_as_file")
        val sharedPrefs = preferenceManager.sharedPreferences

        val appLanguagePreference = findPreference<ListPreference>("app_language")
        appLanguagePreference?.setOnPreferenceChangeListener { _, newValue ->
            val langTag = newValue as? String ?: "default"
            if (langTag == "default") {
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.getEmptyLocaleList())
            } else {
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.forLanguageTags(langTag))
            }
            true
        }

        jitPreference?.isVisible = false
        val jitModePref = findPreference<ListPreference>("3ds_jit_mode")
        if (Build.SUPPORTED_64_BIT_ABIS.isEmpty()) {
            jitPreference?.isChecked = false
            stormCompilerPreference?.isChecked = false
            stormCompilerPreference?.isVisible = false
            jitModePref?.isVisible = false
            sharedPrefs?.edit()?.putBoolean("enable_jit", false)?.apply()
        } else {
            val legacyJitEnabled = jitPreference?.isChecked == true || sharedPrefs?.getBoolean("enable_jit", false) == true
            if (legacyJitEnabled && stormCompilerPreference?.isChecked == false) {
                stormCompilerPreference.isChecked = true
                sharedPrefs?.edit()?.putBoolean("enable_storm_compiler", true)?.apply()
            }
            if (legacyJitEnabled) {
                jitPreference?.isChecked = false
                sharedPrefs?.edit()?.putBoolean("enable_jit", false)?.apply()
            }
            jitModePref?.isEnabled = stormCompilerPreference?.isChecked != false
            stormCompilerPreference?.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as? Boolean ?: true
                jitModePref?.isEnabled = enabled
                if (!enabled) {
                    jitPreference?.isChecked = false
                    sharedPrefs?.edit()?.putBoolean("enable_jit", false)?.apply()
                }
                true
            }
        }

        findPreference<Preference>("3ds_low_end_preset")?.setOnPreferenceClickListener {
            sharedPrefs?.edit()?.apply {
                putBoolean("enable_storm_compiler", true)
                putString("3ds_jit_mode", "2")
                putString("3ds_cpu_clock_percentage", "75")
                putString("video_internal_resolution", "1")
                putBoolean("video_async_shader_compilation", true)
                putString("video_filtering", "none")
                putBoolean("3ds_fmv_hack", true)
                putBoolean("3ds_skip_slow_draw", true)
                putBoolean("3ds_skip_duplicate_frames", true)
                putBoolean("3ds_disable_right_eye", true)
                putBoolean("3ds_accurate_mul", false)
                putBoolean("3ds_spirv_opt", true)
                putBoolean("3ds_audio_stretch", true)
                putString("3ds_audio_emulation", "0")
                apply()
            }
            stormCompilerPreference?.isChecked = true
            jitModePref?.isEnabled = true
            jitModePref?.value = "2"
            findPreference<ListPreference>("3ds_cpu_clock_percentage")?.value = "75"
            Toast.makeText(requireContext(), R.string.pref_3ds_low_end_preset_applied, Toast.LENGTH_LONG).show()
            true
        }

        mirrorPreference?.setOnPreferenceChangeListener { _, newValue ->
            if (updatingMirrorPreference) {
                return@setOnPreferenceChangeListener true
            }
            if (newValue != true) {
                preferenceManager.sharedPreferences?.edit()?.putBoolean("save_internal_config_as_file", false)?.apply()
                return@setOnPreferenceChangeListener true
            }

            if (!settingsBackupManager.hasStorageAccess()) {
                Toast.makeText(requireContext(), R.string.settings_mirror_no_storage_permission, Toast.LENGTH_LONG).show()
                return@setOnPreferenceChangeListener false
            }

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val mirrorDirectory = settingsBackupManager.getActiveMirrorDirectory()
                    val hasMirror = mirrorDirectory != null && runCatching { settingsBackupManager.hasMirrorAt(mirrorDirectory) }.getOrDefault(false)

                    withContext(Dispatchers.Main) {
                        if (!isAdded) return@withContext
                        val ctx = context ?: return@withContext

                        if (!hasMirror || mirrorDirectory == null) {
                            setMirrorEnabled(mirrorPreference)
                            settingsBackupManager.requestMirrorWrite()
                        } else {
                            AlertDialog.Builder(ctx)
                                .setTitle(R.string.settings_mirror_detected_title)
                                .setMessage(R.string.settings_mirror_detected_message)
                                .setPositiveButton(R.string.settings_mirror_restore) { _, _ ->
                                    setMirrorEnabled(mirrorPreference)
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val restored = runCatching {
                                            settingsBackupManager.restoreMirrorFrom(mirrorDirectory)
                                            settingsBackupManager.requestMirrorWrite()
                                            true
                                        }.getOrDefault(false)
                                        if (restored) {
                                            withContext(Dispatchers.Main) {
                                                if (isAdded) {
                                                    activity?.recreate()
                                                }
                                            }
                                        }
                                    }
                                }
                                .setNegativeButton(R.string.settings_mirror_ignore) { _, _ ->
                                    setMirrorEnabled(mirrorPreference)
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        runCatching {
                                            settingsBackupManager.overwriteMirrorAt(mirrorDirectory)
                                            settingsBackupManager.requestMirrorWrite()
                                        }
                                    }
                                }
                                .setOnCancelListener {
                                    updatingMirrorPreference = true
                                    mirrorPreference.isChecked = false
                                    preferenceManager.sharedPreferences?.edit()?.putBoolean("save_internal_config_as_file", false)?.apply()
                                    updatingMirrorPreference = false
                                }
                                .show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        if (isAdded) {
                            setMirrorEnabled(mirrorPreference)
                            settingsBackupManager.requestMirrorWrite()
                        }
                    }
                }
            }
            false
        }

        findPreference<Preference>("backup_internal_layout")?.setOnPreferenceClickListener {
            backupInternalLayoutLauncher.launch(null)
            true
        }
        findPreference<Preference>("backup_external_layout")?.setOnPreferenceClickListener {
            backupExternalLayoutLauncher.launch(null)
            true
        }
        findPreference<Preference>("restore_internal_layout")?.setOnPreferenceClickListener {
            restoreInternalLayoutLauncher.launch(null)
            true
        }
        findPreference<Preference>("restore_external_layout")?.setOnPreferenceClickListener {
            restoreExternalLayoutLauncher.launch(null)
            true
        }

        findPreference<Preference>("system_share_app_log")?.setOnPreferenceClickListener {
            shareSystemLogFiles()
            true
        }

        findPreference<Preference>("system_3ds_setup")?.setOnPreferenceClickListener {
            com.storm3ds.emulator.ui.settings.showSystem3dsSetupDialog(requireActivity())
            true
        }

        findPreference<Preference>("3ds_system_files_check")?.setOnPreferenceClickListener {
            showThreeDsSystemFilesDialog()
            true
        }

        findPreference<Preference>("3ds_installed_titles_manager")?.setOnPreferenceClickListener {
            showInstalledTitlesManagerDialog()
            true
        }
    }

    private fun showThreeDsSystemFilesDialog() {
        val ctx = context ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val report = runCatching {
                com.storm3ds.emulator.NativeCoreLoader.load()
                val userDir = com.storm3ds.emulator.nativebridge.NativeBridgeHost.resolveUserDirectoryPath()
                val sysDir = java.io.File(userDir, "sysdata")
                val sdmcDir = java.io.File(userDir, "sdmc/Nintendo 3DS")
                val nandDir = java.io.File(userDir, "nand")
                val boot9Ok = java.io.File(sysDir, "boot9.bin").let { it.exists() && it.length() > 0 }
                val aesOk = java.io.File(sysDir, "aes_keys.txt").let { it.exists() && it.length() > 0 } ||
                    java.io.File(sysDir, "keys.txt").let { it.exists() && it.length() > 0 }
                val seedOk = java.io.File(sysDir, "seeddb.bin").let { it.exists() && it.length() > 0 }
                val homeMenuPath = runCatching {
                    com.storm3ds.emulator.nativebridge.NativeLibrary.getHomeMenuPath(-1)
                }.getOrDefault("")
                val installed = if (homeMenuPath.isNotBlank()) getString(R.string.setup_3ds_key_ok) else getString(R.string.setup_3ds_key_missing)
                val sysTitles = runCatching {
                    com.storm3ds.emulator.nativebridge.NativeLibrary.areSystemTitlesInstalled()
                }.getOrElse { booleanArrayOf(false, false) }
                val yes = getString(R.string.setup_3ds_key_ok)
                val no = getString(R.string.setup_3ds_key_missing)
                val baseReport = getString(
                    R.string.pref_3ds_system_files_report,
                    userDir,
                    if (boot9Ok) yes else no,
                    if (aesOk) yes else no,
                    if (seedOk) yes else no,
                    installed,
                    if (nandDir.exists()) nandDir.absolutePath else "-",
                    if (sdmcDir.exists()) sdmcDir.absolutePath else java.io.File(userDir, "sdmc").absolutePath,
                )
                val sysStatus = getString(
                    R.string.pref_3ds_system_titles_status_fmt,
                    if (sysTitles.getOrElse(0) { false }) yes else no,
                    if (sysTitles.getOrElse(1) { false }) yes else no,
                )
                "$baseReport\n$sysStatus"
            }.getOrElse { it.localizedMessage ?: "Error" }

            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                AlertDialog.Builder(ctx)
                    .setTitle(R.string.pref_3ds_check_system_files_title)
                    .setMessage(report)
                    .setPositiveButton(R.string.home_menu_3ds_open_setup) { _, _ ->
                        com.storm3ds.emulator.ui.settings.showSystem3dsSetupDialog(requireActivity())
                    }
                    .setNegativeButton(R.string.ok, null)
                    .show()
            }
        }
    }

    private data class InstalledTitleEntry(
        val title: String,
        val titleId: Long,
        val titleIdHex: String,
        val mediaType: com.storm3ds.emulator.nativebridge.model.GameMediaType,
    )

    private fun showInstalledTitlesManagerDialog() {
        val ctx = context ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val entries = runCatching {
                com.storm3ds.emulator.NativeCoreLoader.load()
                com.storm3ds.emulator.nativebridge.NativeLibrary.getInstalledGamePaths().mapNotNull { ig ->
                    val tid = runCatching {
                        com.storm3ds.emulator.nativebridge.NativeLibrary.getTitleId(ig.path)
                    }.getOrDefault(0L)
                    if (tid == 0L) return@mapNotNull null
                    val tidHex = String.format(java.util.Locale.ROOT, "%016X", tid)
                    val title = runCatching {
                        com.storm3ds.emulator.nativebridge.model.GameInfo(ig.path).getTitle()
                    }.getOrNull()?.takeIf { it.isNotBlank() } ?: tidHex
                    InstalledTitleEntry(title, tid, tidHex, ig.mediaType)
                }.sortedBy { it.title.lowercase() }
            }.getOrDefault(emptyList())

            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                if (entries.isEmpty()) {
                    AlertDialog.Builder(ctx)
                        .setTitle(R.string.pref_3ds_installed_titles_title)
                        .setMessage(R.string.pref_3ds_installed_titles_empty)
                        .setPositiveButton(R.string.ok, null)
                        .show()
                    return@withContext
                }
                val labels = entries.map { "${it.title} (${it.titleIdHex} • ${it.mediaType.name})" }.toTypedArray()
                AlertDialog.Builder(ctx)
                    .setTitle(R.string.pref_3ds_installed_titles_title)
                    .setItems(labels) { _, which ->
                        val picked = entries.getOrNull(which) ?: return@setItems
                        AlertDialog.Builder(ctx)
                            .setTitle(R.string.pref_3ds_uninstall_title_confirm_title)
                            .setMessage(getString(R.string.pref_3ds_uninstall_title_confirm_msg, picked.title, picked.titleIdHex))
                            .setPositiveButton(R.string.pref_3ds_uninstall_title_action) { _, _ ->
                                lifecycleScope.launch(Dispatchers.IO) {
                                    val ok = runCatching {
                                        com.storm3ds.emulator.nativebridge.NativeLibrary.uninstallTitle(picked.titleId, picked.mediaType)
                                    }.getOrDefault(false)
                                    withContext(Dispatchers.Main) {
                                        if (!isAdded) return@withContext
                                        Toast.makeText(
                                            ctx,
                                            if (ok) R.string.pref_3ds_uninstall_title_success else R.string.pref_3ds_uninstall_title_failed,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        if (ok) showInstalledTitlesManagerDialog()
                                    }
                                }
                            }
                            .setNegativeButton(R.string.cancel, null)
                            .show()
                    }
                    .setNegativeButton(R.string.ok, null)
                    .show()
            }
        }
    }

    private fun shareSystemLogFiles() {
        val ctx = context ?: return
        val appContext = ctx.applicationContext
        lifecycleScope.launch(Dispatchers.IO) {
            val shareData = runCatching {
                val candidateLogDirs = listOfNotNull(
                    java.io.File(android.os.Environment.getExternalStorageDirectory(), "STORM 3DS/logs"),
                    appContext.getExternalFilesDir(null)?.let { java.io.File(it, "logs") },
                    java.io.File(appContext.filesDir, "logs"),
                )
                fun findNonEmptyLogFile(name: String): java.io.File? {
                    return candidateLogDirs
                        .map { java.io.File(it, name) }
                        .firstOrNull { it.exists() && it.length() > 0L }
                }

                // Ensure fresh STORM_INFO.txt always exists
                val freshInfoReport = runCatching {
                    com.storm3ds.emulator.impl.StormDeviceSystemInfoReporter.saveUnifiedReport(appContext)
                }.getOrNull()

                val infoFile = findNonEmptyLogFile(com.storm3ds.emulator.impl.StormDeviceSystemInfoReporter.INFO_FILE_NAME)
                val appLogFile = findNonEmptyLogFile("STORM_3DS.log")

                val exportDir = java.io.File(appContext.cacheDir, "shared_logs").apply { mkdirs() }
                val exportFile = java.io.File(exportDir, "STORM_3DS_DIAGNOSTIC_LOG.txt")
                exportFile.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.appendLine("===== STORM 3DS SYSTEM INFO (STORM_INFO.txt) =====")
                    when {
                        infoFile != null -> writer.appendLine(infoFile.readText(Charsets.UTF_8))
                        !freshInfoReport.isNullOrBlank() -> writer.appendLine(freshInfoReport)
                        else -> writer.appendLine("(No system info generated)")
                    }
                    if (appLogFile != null) {
                        writer.appendLine()
                        writer.appendLine("===== STORM 3DS APP LOG (STORM_3DS.log, last 256 KB) =====")
                        val maxTail = 256 * 1024L
                        java.io.RandomAccessFile(appLogFile, "r").use { raf ->
                            val len = raf.length()
                            if (len > maxTail) raf.seek(len - maxTail)
                            val bytes = ByteArray((len - raf.filePointer).toInt().coerceAtLeast(0))
                            raf.readFully(bytes)
                            writer.write(String(bytes, Charsets.UTF_8))
                        }
                    }
                }
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.fileprovider",
                    exportFile
                )
                exportFile.name to uri
            }.getOrNull()

            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                if (shareData == null) {
                    Toast.makeText(requireContext(), R.string.system_share_log_error, Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                val (fileName, shareUri) = shareData
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "STORM 3DS Log")
                    putExtra(android.content.Intent.EXTRA_STREAM, shareUri)
                    clipData = android.content.ClipData.newUri(appContext.contentResolver, fileName, shareUri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching {
                    startActivity(android.content.Intent.createChooser(shareIntent, getString(R.string.system_share_log_title)))
                }.onFailure {
                    Toast.makeText(requireContext(), R.string.system_share_log_error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setMirrorEnabled(mirrorPreference: SwitchPreference) {
        updatingMirrorPreference = true
        mirrorPreference.isChecked = true
        preferenceManager.sharedPreferences?.edit()?.putBoolean("save_internal_config_as_file", true)?.apply()
        updatingMirrorPreference = false
    }

    override fun onResume() {
        super.onResume()
        updateThreeDsSystemSummary()
    }

    private fun updateThreeDsSystemSummary() {
        val statusText = runCatching {
            val userDir = com.storm3ds.emulator.nativebridge.NativeBridgeHost.resolveUserDirectoryPath()
            val sysDir = java.io.File(userDir, "sysdata")
            val boot9 = java.io.File(sysDir, "boot9.bin").let { it.exists() && it.length() > 0 }
            val keys = java.io.File(sysDir, "aes_keys.txt").let { it.exists() && it.length() > 0 } ||
                java.io.File(sysDir, "keys.txt").let { it.exists() && it.length() > 0 }
            val ok = getString(R.string.setup_3ds_key_ok)
            val miss = getString(R.string.setup_3ds_key_missing)
            "boot9: ${if (boot9) ok else miss} • aes_keys: ${if (keys) ok else miss}"
        }.getOrNull() ?: return
        findPreference<Preference>("3ds_system_files_check")?.summary = statusText
    }
}
