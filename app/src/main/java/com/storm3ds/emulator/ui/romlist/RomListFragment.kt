package com.storm3ds.emulator.ui.romlist

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.storm3ds.emulator.domain.model.RomScanningStatus
import com.storm3ds.emulator.domain.model.rom.Rom
import com.storm3ds.emulator.domain.repositories.SettingsRepository
import com.storm3ds.emulator.impl.RomSaveFileManager
import com.storm3ds.emulator.parcelables.RomParcelable
import com.storm3ds.emulator.R
import com.storm3ds.emulator.ui.romdetails.RomDetailsActivity
import com.storm3ds.emulator.ui.romlist.composables.RomBrowserScreen
import com.storm3ds.emulator.ui.romlist.composables.RomContextMenu
import com.storm3ds.emulator.ui.theme.MelonTheme

@AndroidEntryPoint
class RomListFragment : Fragment() {
    companion object {
        private const val KEY_ALLOW_ROM_CONFIGURATION = "allow_rom_configuration"
        private const val KEY_ROM_ENABLE_CRITERIA = "rom_enable_criteria"

        fun newInstance(allowRomConfiguration: Boolean, enableCriteria: RomEnableCriteria): RomListFragment {
            return RomListFragment().also {
                it.arguments = bundleOf(
                    KEY_ALLOW_ROM_CONFIGURATION to allowRomConfiguration,
                    KEY_ROM_ENABLE_CRITERIA to enableCriteria.toString(),
                )
            }
        }
    }

    enum class RomEnableCriteria {
        ENABLE_ALL,
        ENABLE_NON_DSIWARE,
    }

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var romSaveFileManager: RomSaveFileManager
    @Inject lateinit var boxArtRepository: com.storm3ds.emulator.ui.romlist.boxart.BoxArtRepository

    private val romListViewModel: RomListViewModel by activityViewModels()
    private var pendingCoverRom: Rom? = null
    private val pickCustomCoverLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        val rom = pendingCoverRom
        pendingCoverRom = null
        if (uri != null && rom != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val success = boxArtRepository.saveCustomCover(requireContext(), rom, uri)
                if (success) {
                    android.widget.Toast.makeText(requireContext(), R.string.custom_cover_updated, android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(requireContext(), R.string.custom_cover_error, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private var romSelectedListener: ((Rom) -> Unit)? = null
    private var pendingSaveImportRom: Rom? = null

    private val saveFileImportLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val rom = pendingSaveImportRom
        pendingSaveImportRom = null

        if (uri != null && rom != null) {
            validateAndConfirmSaveImport(rom, uri)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val allowRomConfiguration = arguments?.getBoolean(KEY_ALLOW_ROM_CONFIGURATION) ?: true

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MelonTheme {
                    val state by romListViewModel.browserState.collectAsState()
                    val scanningStatus by romListViewModel.romScanningStatus
                        .collectAsState(initial = RomScanningStatus.NOT_SCANNING)
                    val confirmedAchievementHashes by romListViewModel.confirmedAchievementHashes.collectAsState()
                    val isRaAuthenticated by romListViewModel.isRaAuthenticated.collectAsState()
                    val raCoverByHash by romListViewModel.raCoverByHash.collectAsState()
                    val boxArtByUri by romListViewModel.boxArtByUri.collectAsState()
                    val searchQuery by romListViewModel.searchQuery.collectAsState()
                    var contextRomUri by remember { mutableStateOf<String?>(null) }

                    val currentContextRom: Rom? = remember(contextRomUri, state.entries, state.continuePlaying) {
                        val target = contextRomUri ?: return@remember null
                        state.entries.firstNotNullOfOrNull {
                            (it as? RomBrowserEntry.RomItem)?.rom?.takeIf { r -> r.uri.toString() == target }
                        } ?: state.continuePlaying.firstOrNull { it.uri.toString() == target }
                    }

                    val checkAndLaunch = { rom: Rom ->
                        romSelectedListener?.invoke(rom)
                    }

                    val topContinueRom = state.continuePlaying.firstOrNull()
                    androidx.compose.runtime.LaunchedEffect(topContinueRom?.uri, topContinueRom?.lastPlayed) {
                        if (topContinueRom != null) {
                            com.storm3ds.emulator.ui.quickresume.QuickResumeStore.syncFromContinuePlaying(
                                requireContext(),
                                topContinueRom,
                            )
                        }
                    }

                    RomBrowserScreen(
                        state = state,
                        coverByHash = raCoverByHash,
                        boxArtByUri = boxArtByUri,
                        searchQuery = searchQuery,
                        allowConfiguration = allowRomConfiguration,
                        scanningStatus = scanningStatus,
                        confirmedAchievementHashes = confirmedAchievementHashes,
                        isRaAuthenticated = isRaAuthenticated,
                        onFolderClick = {},
                        onRomClick = { rom ->
                            checkAndLaunch(rom)
                        },
                        onQuickResumeRom = { rom ->
                            if (state.quickResumeHasSaveState ||
                                com.storm3ds.emulator.ui.quickresume.QuickResumeStore.getQuickSaveTimestamp(requireContext(), rom.uri.toString()) != null
                            ) {
                                com.storm3ds.emulator.ui.quickresume.QuickResumeRequest.request(rom.uri)
                            }
                            checkAndLaunch(rom)
                        },
                        onRomLongPress = { rom -> contextRomUri = rom.uri.toString() },
                        onRomConfigClick = { rom -> contextRomUri = rom.uri.toString() },
                        onFilterSelected = { filter -> romListViewModel.setFilter(filter) },
                        onSortSelected = { mode -> romListViewModel.setRomSorting(mode) },
                        onNavigateUp = {},
                        onRefresh = {
                            romListViewModel.refreshRoms()
                            (activity as? RomListActivity)?.checkForUpdatesSilently()
                        },
                        onSearchQueryChanged = { query ->
                            romListViewModel.setRomSearchQuery(query)
                        },
                        onToggleViewMode = { romListViewModel.toggleViewMode() },
                        onBootFirmwareDs = { (activity as? RomListActivity)?.bootFirmware(com.storm3ds.emulator.domain.model.ConsoleType.DS) },
                        onBootFirmwareDsi = { (activity as? RomListActivity)?.bootFirmware(com.storm3ds.emulator.domain.model.ConsoleType.DSi) },
                        onOpenSingleRom = { (activity as? RomListActivity)?.openSingleRom() },
                        onInstallCia = { (activity as? RomListActivity)?.openCiaInstaller() },
                        onRandomRom = { (activity as? RomListActivity)?.launchRandomRom() },
                        onCheckUpdates = { (activity as? RomListActivity)?.checkForUpdatesManually() },
                        onOpenSettings = { (activity as? RomListActivity)?.openSettings() },
                        onOpenPlayerStats = { (activity as? RomListActivity)?.openPlayerStats() },
                        onReportProblem = { (activity as? RomListActivity)?.reportProblem(topContinueRom) },
                        onRomVisible = { rom -> romListViewModel.requestBoxArt(rom) },
                        onFocusedRomChanged = { rom ->
                            (activity as? RomListActivity)?.onLibraryRomHighlighted(rom)
                        },
                        onDpadDownGateChanged = { gate ->
                            (activity as? RomListActivity)?.setRomBrowserDpadDownGate(gate)
                        },
                    )

                    RomContextMenu(
                        rom = currentContextRom,
                        onDismiss = { contextRomUri = null },
                        onToggleFavorite = { rom -> romListViewModel.toggleFavorite(rom) },
                        onShowDetails = { rom -> openRomDetails(rom) },
                        onSendSaveFile = { rom -> shareSaveFile(rom) },
                        onImportSaveFile = { rom -> requestSaveFileImport(rom) },
                        onLaunchRom = { rom -> checkAndLaunch(rom) },
                        onPickCustomCover = { rom ->
                            pendingCoverRom = rom
                            pickCustomCoverLauncher.launch("image/*")
                        },
                        onClearCustomCover = { rom ->
                            viewLifecycleOwner.lifecycleScope.launch {
                                val success = boxArtRepository.clearCustomCover(requireContext(), rom)
                                if (success) {
                                    android.widget.Toast.makeText(requireContext(), R.string.custom_cover_removed, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onDeleteShaderCache = { rom -> clearRomShaderCache(rom) },
                        onCompressOrDecompressZ3ds = { rom -> confirmAndConvertZ3ds(rom) },
                        onSetInsertedCartridge = { rom -> setInsertedCartridgeForHomeMenu(rom) },
                        onUninstallInstalledTitle = { rom -> uninstallInstalledTitleForRom(rom) },
                    )
                }
            }
        }
    }

    private suspend fun resolveRomTitleId(rom: Rom): Long {
        val hexTid = rom.gameCode.trim().removePrefix("0x").removePrefix("0X")
        if (hexTid.length == 16) {
            hexTid.toULongOrNull(16)?.toLong()?.let { if (it != 0L) return it }
        }
        return withContext(Dispatchers.IO) {
            com.storm3ds.emulator.NativeCoreLoader.load()
            val path = com.storm3ds.emulator.nativebridge.NativeLibrary.resolveNativeOrContentPath(rom.uri)
            if (path.isBlank()) return@withContext 0L
            com.storm3ds.emulator.nativebridge.NativeLibrary.getTitleId(path).takeIf { it != 0L }
                ?: com.storm3ds.emulator.nativebridge.NativeLibrary.getCiaTitleId(path)
        }
    }

    private fun clearRomShaderCache(rom: Rom) {
        viewLifecycleOwner.lifecycleScope.launch {
            val tid = resolveRomTitleId(rom)
            if (tid == 0L) {
                Toast.makeText(requireContext(), R.string.rom_shader_cache_tid_missing, Toast.LENGTH_SHORT).show()
                return@launch
            }
            withContext(Dispatchers.IO) {
                com.storm3ds.emulator.NativeCoreLoader.load()
                com.storm3ds.emulator.nativebridge.NativeLibrary.deleteVulkanShaderCache(tid)
                com.storm3ds.emulator.nativebridge.NativeLibrary.deleteOpenGLShaderCache(tid)
            }
            Toast.makeText(
                requireContext(),
                getString(R.string.rom_shader_cache_cleared_fmt, String.format(java.util.Locale.ROOT, "%016X", tid)),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun setInsertedCartridgeForHomeMenu(rom: Rom) {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                com.storm3ds.emulator.NativeCoreLoader.load()
                val path = com.storm3ds.emulator.nativebridge.NativeLibrary.resolveNativeOrContentPath(rom.uri)
                if (path.isNotBlank()) {
                    com.storm3ds.emulator.nativebridge.NativeLibrary.setInsertedCartridge(path)
                }
            }
            Toast.makeText(
                requireContext(),
                getString(R.string.rom_set_inserted_cart_success_fmt, rom.config.customName ?: rom.name),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun uninstallInstalledTitleForRom(rom: Rom) {
        viewLifecycleOwner.lifecycleScope.launch {
            val tid = resolveRomTitleId(rom)
            if (tid == 0L) {
                Toast.makeText(requireContext(), R.string.pref_3ds_uninstall_title_not_installed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val match = withContext(Dispatchers.IO) {
                com.storm3ds.emulator.NativeCoreLoader.load()
                com.storm3ds.emulator.nativebridge.NativeLibrary.getInstalledGamePaths().firstOrNull { ig ->
                    runCatching { com.storm3ds.emulator.nativebridge.NativeLibrary.getTitleId(ig.path) == tid }.getOrDefault(false)
                }
            }
            if (match == null) {
                Toast.makeText(requireContext(), R.string.pref_3ds_uninstall_title_not_installed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val tidHex = String.format(java.util.Locale.ROOT, "%016X", tid)
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.pref_3ds_uninstall_title_confirm_title)
                .setMessage(getString(R.string.pref_3ds_uninstall_title_confirm_msg, rom.config.customName ?: rom.name, tidHex))
                .setPositiveButton(R.string.pref_3ds_uninstall_title_action) { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            com.storm3ds.emulator.nativebridge.NativeLibrary.uninstallTitle(tid, match.mediaType)
                        }
                        Toast.makeText(
                            requireContext(),
                            if (ok) R.string.pref_3ds_uninstall_title_success else R.string.pref_3ds_uninstall_title_failed,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun confirmAndConvertZ3ds(rom: Rom) {
        val ctx = context ?: return
        val extLower = rom.fileName.substringAfterLast('.', "").lowercase()
        val isCompressed = extLower in setOf("z3ds", "zcci", "zcxi", "z3dsx", "zcia")
        val shouldCompress = !isCompressed
        val titleRes = if (shouldCompress) R.string.rom_action_compress_z3ds else R.string.rom_action_decompress_z3ds
        AlertDialog.Builder(ctx)
            .setTitle(titleRes)
            .setMessage(getString(R.string.z3ds_convert_confirm_fmt, rom.fileName))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                runZ3dsConversion(rom, shouldCompress)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun runZ3dsConversion(rom: Rom, shouldCompress: Boolean) {
        val ctx = context ?: return
        val progressBar = android.widget.ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            max = 1000
            setPadding(48, 32, 48, 16)
        }
        val statusText = android.widget.TextView(ctx).apply {
            setPadding(48, 8, 48, 24)
            text = rom.fileName
        }
        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(progressBar)
            addView(statusText)
        }
        val dialog = AlertDialog.Builder(ctx)
            .setTitle(if (shouldCompress) R.string.rom_action_compress_z3ds else R.string.rom_action_decompress_z3ds)
            .setView(container)
            .setCancelable(false)
            .show()

        viewLifecycleOwner.lifecycleScope.launch {
            com.storm3ds.emulator.nativebridge.NativeBridgeHost.compressProgressCallback = { total, current ->
                view?.post {
                    if (total > 0L) {
                        progressBar.isIndeterminate = false
                        val pct = ((current * 1000L) / total).toInt().coerceIn(0, 1000)
                        progressBar.progress = pct
                        statusText.text = "${rom.fileName} (${pct / 10}%)"
                    }
                }
            }
            val resultMsg = try {
                withContext(Dispatchers.IO) {
                    com.storm3ds.emulator.NativeCoreLoader.load()
                    val inputPath = com.storm3ds.emulator.nativebridge.NativeLibrary.resolveNativeOrContentPath(rom.uri)
                    val recExt = com.storm3ds.emulator.nativebridge.NativeLibrary.getRecommendedExtension(inputPath, shouldCompress)
                    if (recExt.isBlank()) {
                        return@withContext getString(R.string.z3ds_compress_unsupported)
                    }
                    val baseName = rom.fileName.substringBeforeLast('.', rom.fileName)
                    val outFileName = "$baseName.$recExt"
                    val isDirectNative = inputPath.startsWith("!")
                    if (isDirectNative) {
                        val inFile = java.io.File(inputPath.removePrefix("!"))
                        val outFile = java.io.File(inFile.parentFile, outFileName)
                        val outPath = "!${outFile.absolutePath}"
                        val status = if (shouldCompress) {
                            com.storm3ds.emulator.nativebridge.NativeLibrary.compressFile(inputPath, outPath)
                        } else {
                            com.storm3ds.emulator.nativebridge.NativeLibrary.decompressFile(inputPath, outPath)
                        }
                        if (status == com.storm3ds.emulator.nativebridge.NativeLibrary.CompressStatus.SUCCESS) {
                            romListViewModel.refreshRoms()
                            getString(R.string.z3ds_compress_success_fmt, outFileName)
                        } else {
                            getString(R.string.z3ds_compress_failed_fmt, status.name)
                        }
                    } else {
                        val tempOut = java.io.File(ctx.cacheDir, outFileName)
                        tempOut.delete()
                        try {
                            val status = if (shouldCompress) {
                                com.storm3ds.emulator.nativebridge.NativeLibrary.compressFile(inputPath, "!${tempOut.absolutePath}")
                            } else {
                                com.storm3ds.emulator.nativebridge.NativeLibrary.decompressFile(inputPath, "!${tempOut.absolutePath}")
                            }
                            if (status != com.storm3ds.emulator.nativebridge.NativeLibrary.CompressStatus.SUCCESS || !tempOut.exists()) {
                                return@withContext getString(R.string.z3ds_compress_failed_fmt, status.name)
                            }
                            val treeUri = rom.parentTreeUri
                            val copied = if (treeUri != null) {
                                val dirDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(ctx, treeUri)
                                val targetDoc = dirDoc?.createFile("application/octet-stream", outFileName)
                                if (targetDoc != null) {
                                    ctx.contentResolver.openOutputStream(targetDoc.uri)?.use { os ->
                                        tempOut.inputStream().use { ins -> ins.copyTo(os) }
                                    }
                                    true
                                } else false
                            } else false
                            if (copied) {
                                romListViewModel.refreshRoms()
                                getString(R.string.z3ds_compress_success_fmt, outFileName)
                            } else {
                                getString(R.string.z3ds_compress_failed_fmt, "SAF_WRITE_FAILED")
                            }
                        } finally {
                            tempOut.delete()
                        }
                    }
                }
            } finally {
                com.storm3ds.emulator.nativebridge.NativeBridgeHost.compressProgressCallback = null
                runCatching { dialog.dismiss() }
            }
            if (isAdded) {
                Toast.makeText(requireContext(), resultMsg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openRomDetails(rom: Rom) {
        val intent = Intent(requireContext(), RomDetailsActivity::class.java).apply {
            putExtra(RomDetailsActivity.KEY_ROM, RomParcelable(rom))
        }
        startActivity(intent)
    }

    private fun shareSaveFile(rom: Rom) {
        viewLifecycleOwner.lifecycleScope.launch {
            val sharedSaveFile = runCatching {
                withContext(Dispatchers.IO) {
                    romSaveFileManager.prepareShareFile(rom)
                }
            }.getOrElse {
                Toast.makeText(requireContext(), R.string.rom_save_file_share_failed, Toast.LENGTH_LONG).show()
                return@launch
            }

            if (sharedSaveFile == null) {
                Toast.makeText(requireContext(), R.string.rom_save_file_missing, Toast.LENGTH_LONG).show()
                return@launch
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, sharedSaveFile.uri)
                putExtra(Intent.EXTRA_TITLE, sharedSaveFile.fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(requireContext().contentResolver, sharedSaveFile.fileName, sharedSaveFile.uri)
            }
            val chooser = Intent.createChooser(shareIntent, getString(R.string.rom_save_file_share_chooser))
            runCatching { startActivity(chooser) }
                .onFailure {
                    Toast.makeText(requireContext(), R.string.rom_save_file_share_failed, Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun requestSaveFileImport(rom: Rom) {
        pendingSaveImportRom = rom
        saveFileImportLauncher.launch(arrayOf("*/*"))
    }

    private fun validateAndConfirmSaveImport(rom: Rom, sourceUri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val isPlausibleSaveFile = runCatching {
                withContext(Dispatchers.IO) {
                    romSaveFileManager.isPlausibleSaveFile(sourceUri)
                }
            }.getOrDefault(false)

            if (!isPlausibleSaveFile) {
                Toast.makeText(requireContext(), R.string.rom_save_file_import_invalid, Toast.LENGTH_LONG).show()
                return@launch
            }

            AlertDialog.Builder(requireContext())
                .setTitle(R.string.rom_save_file_import_title)
                .setMessage(getString(R.string.rom_save_file_import_message, rom.config.customName ?: rom.name))
                .setPositiveButton(android.R.string.ok) { _, _ -> importSaveFile(rom, sourceUri) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun importSaveFile(rom: Rom, sourceUri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    romSaveFileManager.importSaveFile(rom, sourceUri)
                }
            }
            val message = if (result.isSuccess) {
                R.string.rom_save_file_import_success
            } else {
                R.string.rom_save_file_import_failed
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        romListViewModel.refreshAuthenticationState()
        romListViewModel.refreshSavesStatus()
    }

    fun setRomSelectedListener(listener: (Rom) -> Unit) {
        romSelectedListener = listener
    }
}
