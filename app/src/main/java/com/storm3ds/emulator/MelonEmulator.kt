package com.storm3ds.emulator

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.Surface
import com.storm3ds.emulator.common.camera.DSiCameraSource
import com.storm3ds.emulator.domain.model.Cheat
import com.storm3ds.emulator.domain.model.ConsoleType
import com.storm3ds.emulator.domain.model.EmulatorConfiguration
import com.storm3ds.emulator.domain.model.Input
import com.storm3ds.emulator.domain.model.retroachievements.RARuntimeBridgeConfig
import com.storm3ds.emulator.domain.model.retroachievements.RASimpleAchievement
import com.storm3ds.emulator.domain.model.retroachievements.RASimpleLeaderboard
import com.storm3ds.emulator.domain.model.retroachievements.RASimpleRuntimeAchievement
import com.storm3ds.emulator.domain.model.retroachievements.RASimpleRuntimeAchievementBucketEntry
import com.storm3ds.emulator.nativebridge.NativeBridgeHost
import com.storm3ds.emulator.nativebridge.NativeLibrary
import com.storm3ds.emulator.nativebridge.features.cheats.model.CheatEngine
import com.storm3ds.emulator.nativebridge.features.settings.SettingKeys
import com.storm3ds.emulator.nativebridge.utils.NetPlayManager
import com.storm3ds.emulator.ui.emulator.render.FrameRenderCallback
import com.storm3ds.emulator.ui.emulator.model.VulkanPresentationConfig
import com.storm3ds.emulator.ui.emulator.rewind.model.RewindSaveState
import com.storm3ds.emulator.ui.emulator.rewind.model.RewindWindow
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import com.storm3ds.emulator.nativebridge.features.cheats.model.Cheat as NativeCheat

object MelonEmulator {
    enum class LoadResult(val isTerminal: Boolean) {
        SUCCESS(false),
        SUCCESS_GBA_FAILED(false),
        NDS_FAILED(true),
        BIOS_FAILED(true)
    }

    enum class FirmwareLoadResult {
        SUCCESS,
        BIOS9_MISSING,
        BIOS9_BAD,
        BIOS7_MISSING,
        BIOS7_BAD,
        FIRMWARE_MISSING,
        FIRMWARE_BAD,
        FIRMWARE_NOT_BOOTABLE,
        DSI_BIOS9_MISSING,
        DSI_BIOS9_BAD,
        DSI_BIOS7_MISSING,
        DSI_BIOS7_BAD,
        DSI_NAND_MISSING,
        DSI_NAND_BAD
    }

    enum class GbaSlotType {
        NONE,
        GBA_ROM,
        RUMBLE_PAK,
        MEMORY_EXPANSION,
        ANALOG_INPUT,
    }

    @Volatile private var currentRomPath: String = ""
    @Volatile private var currentTitleId: Long = 0L
    @Volatile private var emulatorThread: Thread? = null
    @Volatile private var currentConfig: EmulatorConfiguration? = null
    @Volatile private var rewindEnabledFlag: Boolean = true
    @Volatile private var rewindPeriodSec: Int = 5
    @Volatile private var depth3dMode: Int = 0
    @Volatile private var depth3dStrength: Int = 0
    @Volatile private var currentSpeedMultiplier: Double = 100.0
    @Volatile private var fastForwardActive: Boolean = false
    @Volatile private var muteOnFastForward: Boolean = false
    @Volatile private var touchDown: Boolean = false
    private val rewindExecutor = Executors.newSingleThreadScheduledExecutor()
    private var rewindTask: ScheduledFuture<*>? = null
    private val diagnosticBuf = StringBuilder()

    fun setupEmulator(
        emulatorConfiguration: EmulatorConfiguration,
        dsiCameraSource: DSiCameraSource?,
        screenshotBuffer: ByteBuffer,
    ) {
        NativeCoreLoader.load()
        currentConfig = emulatorConfiguration
        val userDir = NativeBridgeHost.resolveUserDirectoryPath()
        NativeLibrary.setUserDirectory(userDir)
        NativeLibrary.createConfigFile()
        NativeLibrary.createLogFile()
        NativeLibrary.logUserDirectory(userDir)
        NativeLibrary.playTimeManagerInit()
        NativeLibrary.initMultiplayer()
        rewindEnabledFlag = emulatorConfiguration.rewindEnabled
        rewindPeriodSec = emulatorConfiguration.rewindPeriodSeconds.coerceIn(1, 30)
        NativeLibrary.setRewindBudgetMb(128)
        applyConfigurationToAzahar(emulatorConfiguration)
        NativeLibrary.reloadSettings()
    }

    private fun applyConfigurationToAzahar(cfg: EmulatorConfiguration) {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(NativeBridgeHost.appContext)
        NativeLibrary.addDefaultSetting(SettingKeys.use_virtual_sd(), "1")
        NativeLibrary.addDefaultSetting(SettingKeys.use_cpu_jit(), if (cfg.useJit) "1" else "0")

        val isNew3ds = prefs.getBoolean("3ds_is_new_3ds", true)
        val cpuClock = (prefs.getString("3ds_cpu_clock_percentage", "100") ?: "100").toIntOrNull()?.coerceIn(25, 200) ?: 100
        val regionVal = (prefs.getString("3ds_region_value", "-1") ?: "-1").toIntOrNull() ?: -1
        val regionFree = prefs.getBoolean("3ds_apply_region_free", true)
        NativeLibrary.addDefaultSetting(SettingKeys.is_new_3ds(), if (isNew3ds) "1" else "0")
        NativeLibrary.addDefaultSetting(SettingKeys.cpu_clock_percentage(), cpuClock.toString())
        NativeLibrary.addDefaultSetting(SettingKeys.region_value(), regionVal.toString())
        NativeLibrary.addDefaultSetting(SettingKeys.apply_region_free_patch(), if (regionFree) "1" else "0")

        val useVulkan = cfg.rendererConfiguration.renderer == com.storm3ds.emulator.domain.model.VideoRenderer.VULKAN
        val resScale = cfg.rendererConfiguration.internalResolutionScaling.coerceIn(1, 10)
        val asyncShaders = prefs.getBoolean("video_async_shader_compilation", true)
        val texFilter = (prefs.getString("video_texture_filter", "0") ?: "0").toIntOrNull()?.coerceIn(0, 5) ?: 0
        val hdTex = prefs.getBoolean("video_hd_textures_enabled", false)
        val dumpTex = prefs.getBoolean("video_dump_textures_enabled", false)
        val preloadTex = prefs.getBoolean("video_preload_textures_enabled", false)

        NativeLibrary.addDefaultSetting(SettingKeys.graphics_api(), if (useVulkan) "2" else "1")
        NativeLibrary.addDefaultSetting(SettingKeys.resolution_factor(), resScale.toString())
        NativeLibrary.addDefaultSetting(SettingKeys.async_shader_compilation(), if (asyncShaders) "1" else "0")
        NativeLibrary.addDefaultSetting(SettingKeys.filter_mode(), if (cfg.rendererConfiguration.videoFiltering.ordinal > 0) "1" else "0")
        NativeLibrary.addDefaultSetting(SettingKeys.texture_filter(), texFilter.toString())
        NativeLibrary.addDefaultSetting(SettingKeys.custom_textures(), if (hdTex) "1" else "0")
        NativeLibrary.addDefaultSetting(SettingKeys.async_custom_loading(), if (hdTex) "1" else "0")
        NativeLibrary.addDefaultSetting(SettingKeys.dump_textures(), if (dumpTex) "1" else "0")
        NativeLibrary.addDefaultSetting(SettingKeys.preload_textures(), if (preloadTex) "1" else "0")

        val fmvHack = prefs.getBoolean("3ds_fmv_hack", true)
        val skipSlowDraw = prefs.getBoolean("3ds_skip_slow_draw", false)
        val skipTexCopy = prefs.getBoolean("3ds_skip_tex_copy", false)
        val accurateMul = prefs.getBoolean("3ds_accurate_mul", false)
        val audioStretch = prefs.getBoolean("3ds_audio_stretch", true)
        val spirvOpt = prefs.getBoolean("3ds_spirv_opt", true)
        val disableRightEye = prefs.getBoolean("3ds_disable_right_eye", false)

        val videoDepthKey = prefs.getString("video_vulkan_3d_depth_mode", "off")
        val modeFromVideo = when (videoDepthKey) {
            "sbs_xr" -> NativeLibrary.StereoMode.SIDE_BY_SIDE.nativeValue
            "sbs_full" -> NativeLibrary.StereoMode.SIDE_BY_SIDE_FULL.nativeValue
            "anaglyph" -> NativeLibrary.StereoMode.ANAGLYPH.nativeValue
            "interlaced" -> NativeLibrary.StereoMode.INTERLACED.nativeValue
            "reverse_interlaced" -> NativeLibrary.StereoMode.REVERSE_INTERLACED.nativeValue
            "cardboard_vr" -> NativeLibrary.StereoMode.CARDBOARD_VR.nativeValue
            "gyro_parallax" -> NativeLibrary.StereoMode.GYRO_PARALLAX.nativeValue
            else -> 0
        }
        val render3dMode = if (prefs.contains("3ds_render_mode")) {
            prefs.getInt("3ds_render_mode", modeFromVideo).coerceIn(0, 7)
        } else {
            modeFromVideo
        }
        val depthFromVideo = prefs.getString("video_vulkan_3d_depth_strength", "0")?.toIntOrNull() ?: 0
        val factor3d = if (prefs.contains("3ds_depth_factor")) {
            prefs.getInt("3ds_depth_factor", depthFromVideo).coerceIn(0, 100)
        } else {
            depthFromVideo.coerceIn(0, 100)
        }

        NativeLibrary.addDefaultSetting(SettingKeys.skip_slow_draw(), if (skipSlowDraw) "1" else "0")
        NativeLibrary.addDefaultSetting(SettingKeys.skip_texture_copy(), if (skipTexCopy) "1" else "0")
        NativeLibrary.addDefaultSetting(SettingKeys.shaders_accurate_mul(), if (accurateMul) "1" else "0")
        NativeLibrary.addDefaultSetting(SettingKeys.enable_audio_stretching(), if (audioStretch) "1" else "0")
        NativeLibrary.addDefaultSetting(SettingKeys.optimize_spirv_output(), if (spirvOpt) "1" else "0")
        NativeLibrary.addDefaultSetting(SettingKeys.render_3d(), render3dMode.toString())
        NativeLibrary.addDefaultSetting(SettingKeys.factor_3d(), factor3d.toString())
        NativeLibrary.addDefaultSetting(SettingKeys.disable_right_eye_render(), if (disableRightEye) "1" else "0")
        NativeLibrary.addDefaultSetting(SettingKeys.volume(), (cfg.volume.coerceIn(0, 512) / 256f).coerceIn(0f, 1f).toString())
        NativeLibrary.addDefaultSetting(SettingKeys.audio_emulation(), if (cfg.soundEnabled) "1" else "0")

        val pretendoEnabled = prefs.getBoolean("3ds_pretendo_enabled", false)
        NativeLibrary.addDefaultSetting(SettingKeys.web_api_url(), if (pretendoEnabled) "https://api.pretendo.cc" else "")
        NativeLibrary.addDefaultSetting(SettingKeys.enable_required_online_lle_modules(), if (pretendoEnabled) "1" else "0")

        val articIp = prefs.getString("3ds_artic_ip", "").orEmpty().trim()
        val articController = prefs.getBoolean("3ds_artic_controller", false)
        if (articIp.isNotEmpty()) {
            NativeLibrary.addDefaultSetting(SettingKeys.last_artic_base_addr(), articIp)
        }
        NativeLibrary.addDefaultSetting(SettingKeys.use_artic_base_controller(), if (articController) "1" else "0")

        val shaderPresetPath = if (cfg.rendererConfiguration.videoFiltering == com.storm3ds.emulator.domain.model.VideoFiltering.RETROARCH) {
            cfg.rendererConfiguration.retroArchShader.presetPath.orEmpty()
        } else ""
        NativeLibrary.addDefaultSetting(SettingKeys.librashader_enabled(), if (shaderPresetPath.isNotBlank()) "1" else "0")
        NativeLibrary.addDefaultSetting(SettingKeys.librashader_preset_path(), shaderPresetPath)

        runCatching {
            NativeLibrary.setSpeedhacks(fmvHack, skipSlowDraw, skipTexCopy, accurateMul, audioStretch, false)
            NativeLibrary.setSpirvOptimization(if (spirvOpt) 1 else 0, false, false)
            NativeLibrary.setRender3dMode(render3dMode)
            NativeLibrary.setFactor3d(factor3d)
            NativeLibrary.setDisableRightEyeRender(disableRightEye)
            val logFilter = prefs.getString("3ds_log_filter", "*:Info") ?: "*:Info"
            val logRegex = prefs.getString("3ds_log_regex", "") ?: ""
            NativeLibrary.setLogFilterAndRegex(logFilter, logRegex)
        }

        val sysLang = (prefs.getString("3ds_system_language", "1") ?: "1").toIntOrNull()?.coerceIn(0, 11) ?: 1
        runCatching {
            com.storm3ds.emulator.nativebridge.utils.SystemSaveGame.load()
            com.storm3ds.emulator.nativebridge.utils.SystemSaveGame.setSystemLanguage(sysLang)
            com.storm3ds.emulator.nativebridge.utils.SystemSaveGame.save()
        }
    }

    fun setupCheats(cheats: Array<Cheat>) {
        NativeCoreLoader.load()
        val tid = currentTitleId
        if (tid != 0L) {
            runCatching {
                CheatEngine.loadCheatFile(tid)
                val existing = CheatEngine.getCheats()
                for (i in existing.indices.reversed()) {
                    CheatEngine.removeCheat(i)
                }
                if (cheats.isNotEmpty()) {
                    for (cheat in cheats) {
                        if (!cheat.enabled) continue
                        val gatewayCode = formatDSOrGatewayCheat(cheat.code)
                        val nc: NativeCheat = NativeCheat.createGatewayCode(cheat.name, cheat.description.orEmpty(), gatewayCode)
                        nc.setEnabled(true)
                        CheatEngine.addCheat(nc)
                    }
                    CheatEngine.saveCheatFile(tid)
                }
            }
        }
    }

    private fun formatDSOrGatewayCheat(raw: String): String {
        return raw.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .joinToString("\n")
    }

    fun setupAchievements(
        achievements: Array<RASimpleAchievement>,
        leaderboards: Array<RASimpleLeaderboard>,
        richPresenceScript: String?,
        runtimeConfig: RARuntimeBridgeConfig?,
    ): Boolean = true

    fun unloadRetroAchievementsData() {}
    fun getRichPresenceStatus(): String? = null
    fun getRuntimeAchievements(): Array<RASimpleRuntimeAchievement> = emptyArray()
    fun getRuntimeAchievementBuckets(): Array<RASimpleRuntimeAchievementBucketEntry> = emptyArray()
    fun getRuntimeSubsetIds(): LongArray = LongArray(0)
    fun retryPendingRetroAchievementsSubmissions(expectedNativeSubmissionIds: LongArray): LongArray? = expectedNativeSubmissionIds
    fun refreshPendingRetroAchievementsSubmissions(): Long = 0L
    fun discardPendingRetroAchievementsSubmissions(expectedNativeSubmissionIds: LongArray): Int = 0
    fun setRetroAchievementsSubmissionTransportSuspended(suspended: Boolean) {}

    fun loadRom(romUri: Uri, sramUri: Uri, gbaSlotType: GbaSlotType, gbaRomUri: Uri?, gbaSramUri: Uri?): LoadResult {
        NativeCoreLoader.load()
        var nativePath = resolveUriToNativePath(romUri)
        if (nativePath.isBlank()) {
            return LoadResult.NDS_FAILED
        }
        var titleId = runCatching { NativeLibrary.getTitleId(nativePath) }.getOrDefault(0L)
        val ext = listOf(nativePath, romUri.lastPathSegment.orEmpty(), romUri.toString())
            .firstNotNullOfOrNull { candidate ->
                candidate.substringBefore('?').substringAfterLast('.', "").lowercase()
                    .takeIf { it in setOf("cia", "zcia", "bcia") }
            } ?: ""
        if (ext in setOf("cia", "zcia", "bcia")) {
            val installStatus = runCatching { NativeLibrary.installCiaSimple(nativePath) }.getOrDefault(-1)
            if (installStatus != 0) {
                return LoadResult.NDS_FAILED
            }
            val tidHex = if (titleId != 0L) String.format("%016x", titleId) else ""
            val installedPath = runCatching {
                NativeLibrary.getInstalledGamePaths().firstOrNull { game ->
                    tidHex.isEmpty() || game.path.contains(tidHex, ignoreCase = true)
                }?.path
            }.getOrNull()
            if (!installedPath.isNullOrBlank()) {
                nativePath = installedPath
                titleId = runCatching { NativeLibrary.getTitleId(nativePath) }.getOrDefault(titleId)
            }
        }
        currentRomPath = nativePath
        currentTitleId = titleId
        if (currentTitleId != 0L) {
            val tidHex = String.format("%016X", currentTitleId)
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(NativeBridgeHost.appContext)
            if (prefs.getBoolean("3ds_auto_profiles_enabled", true)) {
                com.storm3ds.emulator.impl.profiles.GameProfilesRepository.findForTitleId(NativeBridgeHost.appContext, tidHex)?.let { profile ->
                    com.storm3ds.emulator.impl.profiles.GameProfilesRepository.applyProfile(profile)
                    NativeLibrary.reloadSettings()
                }
            }
            runCatching { NativeLibrary.playTimeManagerStart(currentTitleId) }
        }
        return LoadResult.SUCCESS
    }

    fun bootFirmware(): FirmwareLoadResult {
        NativeCoreLoader.load()
        val homePath = runCatching { NativeLibrary.getHomeMenuPath(-1) }.getOrDefault("")
        if (homePath.isBlank()) {
            return FirmwareLoadResult.FIRMWARE_MISSING
        }
        currentRomPath = homePath
        currentTitleId = runCatching { NativeLibrary.getTitleId(homePath) }.getOrDefault(0L)
        return FirmwareLoadResult.SUCCESS
    }

    private fun resolveUriToNativePath(uri: Uri): String {
        if (uri.scheme == "file") return uri.path.orEmpty()
        val raw = uri.toString()
        if (File(raw).exists()) return raw
        val fromBridge = runCatching { NativeLibrary.getNativePath(uri) }.getOrDefault("")
        if (fromBridge.isNotBlank() && File(fromBridge).exists()) return fromBridge
        return raw
    }

    @JvmStatic
    fun startBootDiagnosticCapture() {
        diagnosticBuf.clear()
        diagnosticBuf.appendLine("STORM 3DS Boot Capture started for $currentRomPath")
    }

    @JvmStatic
    fun stopAndGetBootDiagnostic(): String = diagnosticBuf.toString()

    fun startEmulation(startPaused: Boolean) {
        NativeCoreLoader.load()
        val rom = currentRomPath
        if (rom.isBlank()) return
        stopEmulation()
        NativeLibrary.clearRewindBuffer()
        val t = thread(start = true, name = "Storm3dsEmuThread", priority = Thread.MAX_PRIORITY) {
            runCatching {
                NativeLibrary.logDeviceInfo()
                NativeLibrary.run(rom)
            }.onFailure { ex ->
                diagnosticBuf.appendLine("Emulation exception: ${ex.message}")
            }
        }
        emulatorThread = t
        if (startPaused) {
            NativeLibrary.pauseEmulation()
        }
        startRewindTimerIfEnabled()
    }

    private fun startRewindTimerIfEnabled() {
        rewindTask?.cancel(false)
        if (!rewindEnabledFlag) return
        val interval = rewindPeriodSec.toLong().coerceIn(2L, 30L)
        rewindTask = rewindExecutor.scheduleAtFixedRate({
            if (NativeLibrary.isRunning()) {
                runCatching { NativeLibrary.captureRewindSnapshot() }
            }
        }, interval, interval, TimeUnit.SECONDS)
    }

    fun precompileVulkanPipelines(
        videoFilteringOrdinal: Int,
        retroShaderPresetPath: String?,
        retroShaderSourceResolution: String,
        retroShaderPassCount: Int,
        retroShaderParameterOverrides: Map<String, Float>,
    ): Boolean {
        val enabled = !retroShaderPresetPath.isNullOrBlank()
        NativeLibrary.setLibrashaderPreset(enabled, retroShaderPresetPath.orEmpty())
        return true
    }

    fun configureOpenGlRetroArchFilter(
        enabled: Boolean,
        presetPath: String?,
        parameterOverrides: String?,
        clearHistory: Boolean,
        sourceResolution: String,
        maxLayoutWidth: Int,
        maxLayoutHeight: Int,
        passCount: Int,
    ) {
        NativeLibrary.setLibrashaderPreset(enabled && !presetPath.isNullOrBlank(), presetPath.orEmpty())
    }

    fun prewarmOpenGlRetroArchFilter(atlasWidth: Int, atlasHeight: Int): Boolean = true
    fun releaseOpenGlRetroArchFilter() {
        NativeLibrary.setLibrashaderPreset(false, "")
    }
    fun consumeShaderDiagnostics(): Array<String>? = null
    fun presentFrame(deadlineNs: Long, frameRenderCallback: FrameRenderCallback) {}
    fun attachVulkanSurface(surface: Surface, width: Int, height: Int): Int {
        NativeLibrary.surfaceChanged(surface)
        return 1
    }
    fun resizeVulkanSurface(surfaceId: Int, width: Int, height: Int) {}
    fun configureVulkanSurface(surfaceId: Int, presentationConfig: VulkanPresentationConfig, backgroundBitmap: Bitmap?) {}
    fun detachVulkanSurface(surfaceId: Int) {
        NativeLibrary.surfaceDestroyed()
    }
    fun presentVulkanFrame(deadlineNs: Long, budgetDeadlineNs: Long) {}

    fun getFPS(): Float {
        val stats = runCatching { NativeLibrary.getPerfStats() }.getOrNull() ?: return 0f
        return stats.getOrElse(2) { 0.0 }.toFloat()
    }

    fun getCurrentRenderer(): Int =
        if (currentConfig?.rendererConfiguration?.renderer == com.storm3ds.emulator.domain.model.VideoRenderer.VULKAN) 1 else 0

    fun pauseEmulation() {
        runCatching { NativeLibrary.pauseEmulation() }
    }

    fun resumeEmulation() {
        runCatching { NativeLibrary.unPauseEmulation() }
    }

    fun debugStepFrame(): Boolean = false

    fun resetEmulation() {
        val rom = currentRomPath
        if (rom.isNotBlank()) {
            stopEmulation()
            startEmulation(false)
        }
    }

    fun stopEmulation() {
        rewindTask?.cancel(false)
        rewindTask = null
        runCatching { NativeLibrary.playTimeManagerStop() }
        runCatching { NativeLibrary.stopEmulation() }
        runCatching { emulatorThread?.join(1500L) }
        emulatorThread = null
    }

    fun saveState(path: Uri): Boolean {
        val slot = extractSlotFromUri(path)
        NativeLibrary.saveState(slot)
        return true
    }

    fun loadState(path: Uri): Boolean {
        val slot = extractSlotFromUri(path)
        return NativeLibrary.loadStateIfAvailable(slot)
    }

    private fun extractSlotFromUri(uri: Uri): Int {
        val name = uri.lastPathSegment.orEmpty()
        if (name.contains("autosave", ignoreCase = true) || name.contains("periodic", ignoreCase = true)) {
            return NativeLibrary.SAVESTATE_SLOT_COUNT - 1
        }
        val match = Regex("""(\d+)""").findAll(name).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
        return (match ?: 1).coerceIn(0, NativeLibrary.SAVESTATE_SLOT_COUNT - 1)
    }

    fun getCurrentTitleId(): Long = currentTitleId

    fun loadRewindState(rewindSaveState: RewindSaveState): Boolean {
        val stepsBack = rewindSaveState.bufferContentSize.toInt().coerceAtLeast(1)
        return NativeLibrary.rewindStepsBack(stepsBack)
    }

    fun setRewindActive(active: Boolean) {
        if (active) {
            pauseEmulation()
        } else {
            resumeEmulation()
        }
    }

    fun getRewindWindow(): RewindWindow {
        val timestamps = runCatching { NativeLibrary.getRewindTimelineTimestamps() }.getOrElse { LongArray(0) }
        val total = timestamps.size
        val stepFrames = rewindPeriodSec.coerceAtLeast(1) * 60
        val currentFrame = total * stepFrames
        val states = ArrayList<RewindSaveState>(total)
        val emptyBuf = ByteBuffer.allocateDirect(4)
        val emptyShot = ByteBuffer.allocateDirect(256 * 384 * 4)
        for (i in timestamps.indices.reversed()) {
            val stepsBack = (total - i).toLong()
            val frame = (i * stepFrames).coerceAtLeast(0)
            states.add(
                RewindSaveState(
                    buffer = emptyBuf,
                    bufferContentSize = stepsBack,
                    screenshotBuffer = emptyShot,
                    frame = frame,
                )
            )
        }
        return RewindWindow(currentEmulationFrame = currentFrame, rewindStates = states)
    }

    fun onScreenTouch(x: Int, y: Int) {
        val rect = com.storm3ds.emulator.ui.emulator.render.Storm3dsSurfaceRenderCoordinator.lastBottomRect
        val sx: Float
        val sy: Float
        if (rect != null && rect.width > 0 && rect.height > 0) {
            sx = rect.x + (x.coerceIn(0, 255) / 256f) * rect.width
            sy = rect.y + (y.coerceIn(0, 191) / 192f) * rect.height
        } else {
            sx = x.toFloat()
            sy = y.toFloat()
        }
        if (!touchDown) {
            touchDown = true
            NativeLibrary.onTouchEvent(sx, sy, true)
        } else {
            NativeLibrary.onTouchMoved(sx, sy)
        }
    }

    fun onScreenRelease() {
        if (touchDown) {
            touchDown = false
            NativeLibrary.onTouchEvent(0f, 0f, false)
        }
    }

    fun onInputDown(input: Input) {
        val code = mapInputToNativeButton(input) ?: return
        NativeLibrary.onGamePadEvent(NativeLibrary.TOUCHSCREEN_DEVICE, code, NativeLibrary.ButtonState.PRESSED)
    }

    fun onInputUp(input: Input) {
        val code = mapInputToNativeButton(input) ?: return
        NativeLibrary.onGamePadEvent(NativeLibrary.TOUCHSCREEN_DEVICE, code, NativeLibrary.ButtonState.RELEASED)
    }

    private fun mapInputToNativeButton(input: Input): Int? = when (input) {
        Input.A -> NativeLibrary.ButtonType.BUTTON_A
        Input.B -> NativeLibrary.ButtonType.BUTTON_B
        Input.X -> NativeLibrary.ButtonType.BUTTON_X
        Input.Y -> NativeLibrary.ButtonType.BUTTON_Y
        Input.START -> NativeLibrary.ButtonType.BUTTON_START
        Input.SELECT -> NativeLibrary.ButtonType.BUTTON_SELECT
        Input.L -> NativeLibrary.ButtonType.TRIGGER_L
        Input.R -> NativeLibrary.ButtonType.TRIGGER_R
        Input.UP -> NativeLibrary.ButtonType.DPAD_UP
        Input.DOWN -> NativeLibrary.ButtonType.DPAD_DOWN
        Input.LEFT -> NativeLibrary.ButtonType.DPAD_LEFT
        Input.RIGHT -> NativeLibrary.ButtonType.DPAD_RIGHT
        else -> when (input.name) {
            "ZL" -> NativeLibrary.ButtonType.BUTTON_ZL
            "ZR" -> NativeLibrary.ButtonType.BUTTON_ZR
            "HOME" -> NativeLibrary.ButtonType.BUTTON_HOME
            else -> null
        }
    }

    fun setCirclePadAxis(x: Float, y: Float) {
        NativeLibrary.onGamePadMoveEvent(NativeLibrary.TOUCHSCREEN_DEVICE, NativeLibrary.ButtonType.STICK_LEFT, x, y)
    }

    fun setCStickAxis(x: Float, y: Float) {
        NativeLibrary.onGamePadMoveEvent(NativeLibrary.TOUCHSCREEN_DEVICE, NativeLibrary.ButtonType.STICK_C, x, y)
    }

    fun setSlot2AnalogInput(x: Float, y: Float) {
        setCirclePadAxis(x, y)
    }

    fun takeScreenshot(): Boolean = true

    fun setFastForwardEnabled(enabled: Boolean) {
        fastForwardActive = enabled
        if (enabled) {
            NativeLibrary.setTemporaryFrameLimit(currentSpeedMultiplier.coerceAtLeast(200.0))
        } else {
            NativeLibrary.disableTemporaryFrameLimit()
        }
        runCatching { NativeLibrary.setAudioMuted(fastForwardActive && muteOnFastForward) }
    }

    fun setMuteOnFastForwardEnabled(enabled: Boolean) {
        muteOnFastForward = enabled
        runCatching { NativeLibrary.setAudioMuted(fastForwardActive && muteOnFastForward) }
    }

    fun setFrameLimitSpeedMultiplier(multiplier: Float) {
        currentSpeedMultiplier = (multiplier * 100.0).coerceIn(10.0, 500.0)
        NativeLibrary.setTemporaryFrameLimit(currentSpeedMultiplier)
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        runCatching { NativeLibrary.setMicrophoneEnabled(enabled) }
    }

    fun getDetailedEmulationDiagnostic(): String =
        "ROM=$currentRomPath titleId=0x${currentTitleId.toString(16)} fps=${getFPS()} keys=0x${NativeLibrary.getAesKeyStatusMask().toString(16)}"

    fun updateEmulatorConfiguration(emulatorConfiguration: EmulatorConfiguration) {
        currentConfig = emulatorConfiguration
        applyConfigurationToAzahar(emulatorConfiguration)
        NativeLibrary.reloadSettings()
    }

    fun startLanHost(playerName: String, maxPlayers: Int): Boolean {
        return NetPlayManager.netPlayCreateRoom(
            "0.0.0.0",
            24872,
            playerName.ifBlank { "Player" },
            File(currentRomPath).nameWithoutExtension.ifBlank { "Nintendo 3DS" },
            currentTitleId,
            "",
            "${playerName}'s Room",
            maxPlayers.coerceIn(2, 8)
        ) == 0
    }

    fun startLanClient(playerName: String, hostIp: String): Boolean {
        return NetPlayManager.netPlayJoinRoom(hostIp, 24872, playerName.ifBlank { "Player" }, "") == 0
    }

    fun stopLanSession() {
        runCatching { NetPlayManager.netPlayLeaveRoom() }
    }

    fun startLanDiscovery(): Boolean = true
    fun stopLanDiscovery() {}

    fun getLanDiscoveredSessions(): Array<String> {
        return NetPlayManager.getPublicRooms().map { "${it.name}|${it.ip}:${it.port}|${it.members.size}/${it.maxPlayers}" }.toTypedArray()
    }

    @JvmStatic fun setVulkanCompositorMode(mode: Int) {}
    @JvmStatic fun setVulkanThickHighResEdges(enabled: Boolean) {}
    @JvmStatic fun setVulkanDebugViewMode(mode: Int) {}
    @JvmStatic fun dumpVulkanDebugReport(outputDir: String): Boolean = false

    @JvmStatic
    fun setVulkanDepth3DMode(mode: Int) {
        depth3dMode = mode
        NativeLibrary.setRender3dMode(mode)
    }

    @JvmStatic
    fun setVulkanDepth3DStrength(strength: Int) {
        depth3dStrength = strength.coerceIn(0, 100)
        NativeLibrary.setFactor3d(depth3dStrength)
    }

    @JvmStatic fun setVulkanSsaoEnabled(enabled: Boolean) {}
    @JvmStatic fun setDisplayColorProfile(profile: Int) {}
    @JvmStatic fun setDisplayLcdGridMode(mode: Int) {}
    @JvmStatic fun setHingeGapPixels(pixels: Int) {}

    @JvmStatic
    fun setVulkanGyroTilt(tiltX: Float, tiltY: Float) {
        NativeLibrary.setGyro3dParams(tiltX, tiltY, 1.0f)
    }

    @JvmStatic fun setSolarSensorLightLevel(level: Int) {}
    @JvmStatic fun setGuitarGripKeyState(fretIndex: Int, pressed: Boolean) {}
    @JvmStatic fun setHardwareMotionState(ax: Float, ay: Float, az: Float, gx: Float, gy: Float, gz: Float) {}
    @JvmStatic fun setIrPedometerStepCount(steps: Int) {}
    @JvmStatic fun addIrPedometerSteps(delta: Int): Int = 0
    @JvmStatic fun getIrPedometerStepCount(): Int = 0
    @JvmStatic fun setSlot2AddonType(addonType: Int) {}

    @JvmStatic
    fun readMainRamSnapshot(): ByteArray? = null

    @JvmStatic
    fun writeMainRamValue(offset: Int, byteSize: Int, value: Long): Boolean {
        NativeLibrary.writeMemoryValue(offset.toLong() and 0xFFFFFFFFL, byteSize, value)
        return true
    }

    @JvmStatic fun setMicrophoneAgcEnabled(enabled: Boolean) {}
    @JvmStatic fun setMicrophoneNoiseGateThreshold(thresholdPercent: Int) {}
    @JvmStatic fun setVulkanPipelineCacheFile(filePath: String) {}
}
