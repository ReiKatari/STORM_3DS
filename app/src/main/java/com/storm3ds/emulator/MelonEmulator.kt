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
        NativeLibrary.addDefaultSetting(SettingKeys.use_virtual_sd(), "1")
        NativeLibrary.addDefaultSetting(SettingKeys.use_cpu_jit(), if (cfg.useJit) "1" else "0")
        NativeLibrary.addDefaultSetting(SettingKeys.is_new_3ds(), if (cfg.consoleType == ConsoleType.DSi) "1" else "1")
        val useVulkan = cfg.rendererConfiguration.renderer == com.storm3ds.emulator.domain.model.VideoRenderer.VULKAN
        NativeLibrary.addDefaultSetting(SettingKeys.graphics_api(), if (useVulkan) "2" else "1")
        NativeLibrary.addDefaultSetting(SettingKeys.volume(), (cfg.volume / 100f).toString())
        NativeLibrary.addDefaultSetting(SettingKeys.audio_emulation(), if (cfg.soundEnabled) "1" else "0")
        NativeLibrary.addDefaultSetting(SettingKeys.filter_mode(), if (cfg.rendererConfiguration.videoFiltering.ordinal > 0) "1" else "0")
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
        val nativePath = resolveUriToNativePath(romUri)
        if (nativePath.isBlank()) {
            return LoadResult.NDS_FAILED
        }
        currentRomPath = nativePath
        currentTitleId = runCatching { NativeLibrary.getTitleId(nativePath) }.getOrDefault(0L)
        if (currentTitleId != 0L) {
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
        if (rect != null && rect.width > 0 && rect.height > 0) {
            val sx = rect.x + (x.coerceIn(0, 255) / 256f) * rect.width
            val sy = rect.y + (y.coerceIn(0, 191) / 192f) * rect.height
            NativeLibrary.onTouchEvent(sx, sy, true)
        } else {
            NativeLibrary.onTouchEvent(x.toFloat(), y.toFloat(), true)
        }
    }

    fun onScreenRelease() {
        NativeLibrary.onTouchEvent(0f, 0f, false)
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
    }

    fun setMuteOnFastForwardEnabled(enabled: Boolean) {}

    fun setFrameLimitSpeedMultiplier(multiplier: Float) {
        currentSpeedMultiplier = (multiplier * 100.0).coerceIn(10.0, 500.0)
        NativeLibrary.setTemporaryFrameLimit(currentSpeedMultiplier)
    }

    fun setMicrophoneEnabled(enabled: Boolean) {}

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
