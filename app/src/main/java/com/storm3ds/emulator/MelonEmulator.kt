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
    @Volatile private var configuredFastForwardMultiplier: Float = -1.0f
    @Volatile private var slowMotionMultiplier: Float = 1.0f
    @Volatile private var fastForwardActive: Boolean = false
    @Volatile private var muteOnFastForward: Boolean = false
    @Volatile private var touchDown: Boolean = false
    @Volatile var captureFrameBitmapCallback: (() -> Bitmap?)? = null
    @Volatile private var latestScreenshotBitmap: Bitmap? = null
    @Volatile var isLongPreparationInProgress: Boolean = false
    @Volatile var lastLoadFailureWasContainerOrFile: Boolean = false
    private val rewindScreenshots = java.util.ArrayDeque<ByteBuffer>()
    private val rewindPixelScratch = IntArray(256 * 384)
    private val rewindExecutor = Executors.newSingleThreadScheduledExecutor()
    private var rewindTask: ScheduledFuture<*>? = null
    private val diagnosticBuf = StringBuilder()

    fun consumeLatestScreenshotBitmap(): Bitmap? {
        val bmp = latestScreenshotBitmap
        latestScreenshotBitmap = null
        return bmp
    }

    fun setupEmulator(
        emulatorConfiguration: EmulatorConfiguration,
        dsiCameraSource: DSiCameraSource?,
        screenshotBuffer: ByteBuffer,
    ) {
        NativeCoreLoader.load()
        currentConfig = emulatorConfiguration
        val userDir = NativeBridgeHost.resolveUserDirectoryPath()
        NativeLibrary.setUserDirectory(userDir)
        NativeLibrary.createLogFile()
        NativeLibrary.logUserDirectory(userDir)
        NativeLibrary.createConfigFile()
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

        val jitMode = (prefs.getString("3ds_jit_mode", "3") ?: "3").toIntOrNull()?.coerceIn(0, 3) ?: 3
        val adaptiveCacheMb = runCatching {
            val am = NativeBridgeHost.appContext.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            val mi = android.app.ActivityManager.MemoryInfo()
            am?.getMemoryInfo(mi)
            val totalMb = (mi.totalMem / (1024L * 1024L)).toInt()
            when {
                jitMode == 3 && totalMb >= 7000 -> 128
                jitMode == 3 && totalMb >= 5000 -> 96
                jitMode == 3 && totalMb in 1..3399 -> 48
                jitMode == 3 -> 64
                jitMode == 2 -> 48
                totalMb in 1..3200 -> 48
                totalMb in 3201..6400 -> 64
                else -> 96
            }
        }.getOrDefault(if (jitMode == 3) 96 else 64)
        runCatching { NativeLibrary.setStormJitOptions(jitMode, adaptiveCacheMb) }

        val isNew3ds = cfg.isNew3dsOverride ?: prefs.getBoolean("3ds_is_new_3ds", true)
        val cpuClock = (cfg.cpuClockPercentageOverride ?: (prefs.getString("3ds_cpu_clock_percentage", "100") ?: "100").toIntOrNull() ?: 100).coerceIn(25, 200)
        val regionVal = (prefs.getString("3ds_region_value", "-1") ?: "-1").toIntOrNull() ?: -1
        val regionFree = prefs.getBoolean("3ds_apply_region_free", true)
        NativeLibrary.addDefaultSetting(SettingKeys.is_new_3ds(), if (isNew3ds) "1" else "0")
        NativeLibrary.addDefaultSetting(SettingKeys.cpu_clock_percentage(), cpuClock.toString())
        NativeLibrary.addDefaultSetting(SettingKeys.region_value(), regionVal.toString())
        NativeLibrary.addDefaultSetting(SettingKeys.apply_region_free_patch(), if (regionFree) "1" else "0")

        val useVulkan = cfg.rendererConfiguration.renderer == com.storm3ds.emulator.domain.model.VideoRenderer.VULKAN
        val resScale = cfg.rendererConfiguration.internalResolutionScaling.coerceIn(1, 10)
        val asyncShaders = prefs.getBoolean("video_async_shader_compilation", true)
        val skipDupFrames = prefs.getBoolean("3ds_skip_duplicate_frames", false)
        val texFilter = (prefs.getString("video_texture_filter", "0") ?: "0").toIntOrNull()?.coerceIn(0, 5) ?: 0
        val hdTex = prefs.getBoolean("video_hd_textures_enabled", false)
        val dumpTex = prefs.getBoolean("video_dump_textures_enabled", false)
        val preloadTex = prefs.getBoolean("video_preload_textures_enabled", false)

        NativeLibrary.addDefaultSetting(SettingKeys.graphics_api(), if (useVulkan) "2" else "1")
        NativeLibrary.addDefaultSetting(SettingKeys.use_hw_shader(), "1")
        NativeLibrary.addDefaultSetting(SettingKeys.use_shader_jit(), "1")
        NativeLibrary.addDefaultSetting(SettingKeys.use_disk_shader_cache(), "1")
        NativeLibrary.addDefaultSetting(SettingKeys.async_presentation(), "1")
        NativeLibrary.addDefaultSetting(SettingKeys.use_skip_duplicate_frames(), if (skipDupFrames) "1" else "0")
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
        val audioStretch = prefs.getBoolean("3ds_audio_stretch", false)
        val spirvOpt = prefs.getBoolean("3ds_spirv_opt", false)
        val disableRightEye = prefs.getBoolean("3ds_disable_right_eye", false)

        val videoDepthKey = prefs.getString("video_vulkan_3d_depth_mode", "off")?.lowercase()
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
        val baseRender3dMode = if (prefs.contains("video_vulkan_3d_depth_mode")) {
            modeFromVideo
        } else {
            prefs.getInt("3ds_render_mode", modeFromVideo).coerceIn(0, 7)
        }
        val render3dMode = (cfg.render3dModeOverride ?: baseRender3dMode).coerceIn(0, 7)
        val depthFromVideo = prefs.getString("video_vulkan_3d_depth_strength", "50")?.toIntOrNull()?.coerceIn(0, 100) ?: 50
        val baseFactor3d = if (prefs.contains("video_vulkan_3d_depth_strength")) {
            depthFromVideo
        } else {
            prefs.getInt("3ds_depth_factor", depthFromVideo).coerceIn(0, 100)
        }
        val factor3d = (cfg.render3dFactorOverride ?: baseFactor3d).coerceIn(0, 100)
        val whichDisplay = if (render3dMode == 0) "0" else "2"
        depth3dMode = render3dMode

        configuredFastForwardMultiplier = cfg.fastForwardSpeedMultiplier
        slowMotionMultiplier = cfg.frameLimitSpeedMultiplier.coerceIn(0.1f, 2.0f)
        val initialLimitPct = (slowMotionMultiplier * 100.0).toInt().coerceIn(10, 200)
        val inputType = when (cfg.micSource) {
            com.storm3ds.emulator.domain.model.MicSource.NONE -> 0
            com.storm3ds.emulator.domain.model.MicSource.DEVICE -> 1
            com.storm3ds.emulator.domain.model.MicSource.BLOW -> 2
        }

        NativeLibrary.addDefaultSetting(SettingKeys.use_frame_limit(), "1")
        NativeLibrary.addDefaultSetting(SettingKeys.frame_limit(), initialLimitPct.toString())
        NativeLibrary.addDefaultSetting(SettingKeys.input_type(), inputType.toString())
        NativeLibrary.addDefaultSetting(SettingKeys.skip_slow_draw(), if (skipSlowDraw) "1" else "0")
        NativeLibrary.addDefaultSetting(SettingKeys.skip_texture_copy(), if (skipTexCopy) "1" else "0")
        NativeLibrary.addDefaultSetting(SettingKeys.shaders_accurate_mul(), if (accurateMul) "1" else "0")
        NativeLibrary.addDefaultSetting(SettingKeys.enable_audio_stretching(), if (audioStretch) "1" else "0")
        NativeLibrary.addDefaultSetting(SettingKeys.optimize_spirv_output(), if (spirvOpt) "1" else "0")
        NativeLibrary.addDefaultSetting(SettingKeys.render_3d(), render3dMode.toString())
        NativeLibrary.addDefaultSetting(SettingKeys.render_3d_which_display(), whichDisplay)
        NativeLibrary.addDefaultSetting(SettingKeys.factor_3d(), factor3d.toString())
        NativeLibrary.addDefaultSetting(SettingKeys.disable_right_eye_render(), if (disableRightEye) "1" else "0")
        val audioEmulation = (prefs.getString("3ds_audio_emulation", "0") ?: "0").toIntOrNull()?.coerceIn(0, 2) ?: 0
        val realtimeAudio = prefs.getBoolean("3ds_realtime_audio", false)
        NativeLibrary.addDefaultSetting(
            SettingKeys.volume(),
            if (cfg.soundEnabled) (cfg.volume.coerceIn(0, 512) / 256f).coerceIn(0f, 1f).toString() else "0",
        )
        NativeLibrary.addDefaultSetting(SettingKeys.audio_emulation(), audioEmulation.toString())
        NativeLibrary.addDefaultSetting(SettingKeys.enable_realtime_audio(), if (realtimeAudio) "1" else "0")
        runCatching {
            NativeLibrary.setAudioMuted(!cfg.soundEnabled)
            NativeLibrary.setMicrophoneInputType(inputType)
        }

        val cameraSourcePref = prefs.getString("dsi_camera_source", "physical_cameras") ?: "physical_cameras"
        val cameraDriver = when (cameraSourcePref) {
            "black_screen" -> "blank"
            "static_image" -> "image"
            else -> "ndk"
        }
        NativeLibrary.addDefaultSetting(SettingKeys.camera_inner_name(), cameraDriver)
        NativeLibrary.addDefaultSetting(SettingKeys.camera_outer_left_name(), cameraDriver)
        NativeLibrary.addDefaultSetting(SettingKeys.camera_outer_right_name(), cameraDriver)
        if (cameraDriver == "image") {
            val rawUri = prefs.getString("dsi_camera_static_image", "").orEmpty()
            val resolvedImg = if (rawUri.isNotBlank()) {
                runCatching {
                    NativeLibrary.resolveNativeOrContentPath(android.net.Uri.parse(rawUri)).removePrefix("!")
                }.getOrDefault(rawUri)
            } else ""
            if (resolvedImg.isNotBlank()) {
                NativeLibrary.addDefaultSetting(SettingKeys.camera_inner_config(), resolvedImg)
                NativeLibrary.addDefaultSetting(SettingKeys.camera_outer_left_config(), resolvedImg)
                NativeLibrary.addDefaultSetting(SettingKeys.camera_outer_right_config(), resolvedImg)
            }
        }

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
            val logFilter = prefs.getString("3ds_log_filter", "*:Warning") ?: "*:Warning"
            val logRegex = prefs.getString("3ds_log_regex", "") ?: ""
            NativeLibrary.setLogFilterAndRegex(logFilter, logRegex)
        }

        val rawSysLang = (prefs.getString("3ds_system_language", "-1") ?: "-1").toIntOrNull() ?: -1
        val sysLang = if (rawSysLang in 0..11) rawSysLang else resolveDevice3dsSystemLanguage()
        val prefNickname = prefs.getString("firmware_settings_nickname", null)
        runCatching {
            com.storm3ds.emulator.nativebridge.utils.SystemSaveGame.load()
            val currentNandName = com.storm3ds.emulator.nativebridge.utils.SystemSaveGame.getUsername()
            val effectiveName = com.storm3ds.emulator.nativebridge.utils.SystemSaveGame.resolveEffectiveUsername(
                prefNickname,
                currentNandName,
            )
            if (effectiveName != currentNandName) {
                com.storm3ds.emulator.nativebridge.utils.SystemSaveGame.setUsername(effectiveName)
            }
            if (prefNickname.isNullOrBlank() || prefNickname.equals("AZAHAR", ignoreCase = true)) {
                prefs.edit().putString("firmware_settings_nickname", effectiveName).apply()
            }
            com.storm3ds.emulator.nativebridge.utils.SystemSaveGame.setSystemLanguage(sysLang)
            com.storm3ds.emulator.nativebridge.utils.SystemSaveGame.save()
        }
    }

    internal fun resolveDevice3dsSystemLanguage(locale: java.util.Locale? = null): Int {
        val target = locale
            ?: runCatching {
                androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
                    .takeIf { !it.isEmpty }
                    ?.get(0)
            }.getOrNull()
            ?: java.util.Locale.getDefault()
        val lang = target.language.lowercase(java.util.Locale.ROOT)
        val country = target.country.uppercase(java.util.Locale.ROOT)
        val script = target.script
        return when (lang) {
            "ja" -> 0
            "en" -> 1
            "fr" -> 2
            "de" -> 3
            "it" -> 4
            "es" -> 5
            "zh" -> if (script.equals("Hant", ignoreCase = true) || country in setOf("TW", "HK", "MO")) 11 else 6
            "ko" -> 7
            "nl" -> 8
            "pt" -> 9
            "ru", "uk", "be", "kk" -> 10
            else -> 1
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
                for (cheat in cheats) {
                    val gatewayCode = formatDSOrGatewayCheat(cheat.code)
                    if (gatewayCode.isBlank()) continue
                    val nc: NativeCheat = NativeCheat.createGatewayCode(cheat.name, cheat.description.orEmpty(), gatewayCode)
                    nc.setEnabled(cheat.enabled)
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

    internal fun matchInstalledGamePathByTitleId(installedPaths: List<String>, titleId: Long): String? {
        if (titleId == 0L) return null
        val tidHigh = String.format(java.util.Locale.ROOT, "%08x", (titleId ushr 32) and 0xFFFFFFFFL)
        val tidLow = String.format(java.util.Locale.ROOT, "%08x", titleId and 0xFFFFFFFFL)
        val slashNeedle = "$tidHigh/$tidLow"
        val backslashNeedle = "$tidHigh\\$tidLow"
        val compactNeedle = "$tidHigh$tidLow"
        return installedPaths.firstOrNull { path ->
            path.contains(slashNeedle, ignoreCase = true) ||
                path.contains(backslashNeedle, ignoreCase = true) ||
                path.contains(compactNeedle, ignoreCase = true)
        }
    }

    private fun resolveCiaExtension(romUri: Uri, nativePath: String): String {
        val docName = runCatching {
            androidx.documentfile.provider.DocumentFile.fromSingleUri(NativeBridgeHost.appContext, romUri)?.name
        }.getOrNull()
        return listOfNotNull(docName, nativePath, romUri.lastPathSegment, romUri.toString())
            .firstNotNullOfOrNull { candidate ->
                candidate.substringBefore('?').substringAfterLast('.', "").lowercase()
                    .takeIf { it in setOf("cia", "zcia", "bcia") }
            } ?: ""
    }

    private fun resolveInstalledBootPath(
        titleId: Long,
        postGames: Array<NativeLibrary.InstalledGame>? = null,
        prePaths: Set<String> = emptySet(),
    ): String? {
        if (titleId != 0L) {
            val direct = runCatching { NativeLibrary.findInstalledTitlePath(titleId) }.getOrDefault("")
            if (direct.isNotBlank()) return direct

            val tidHigh = (titleId ushr 32) and 0xFFFFFFFFL
            val baseTid = if (tidHigh == 0x0004000EL || tidHigh == 0x0004008CL) {
                (0x00040000L shl 32) or (titleId and 0xFFFFFFFFL)
            } else {
                0L
            }
            if (baseTid != 0L) {
                val baseDirect = runCatching { NativeLibrary.findInstalledTitlePath(baseTid) }.getOrDefault("")
                if (baseDirect.isNotBlank()) return baseDirect
            }

            val games = postGames ?: runCatching { NativeLibrary.getInstalledGamePaths() }.getOrDefault(emptyArray())
            val paths = games.map { it.path }
            matchInstalledGamePathByTitleId(paths, titleId)?.let { return it }
            if (baseTid != 0L) {
                matchInstalledGamePathByTitleId(paths, baseTid)?.let { return it }
            }
            games.firstOrNull { runCatching { NativeLibrary.getTitleId(it.path) == titleId }.getOrDefault(false) }
                ?.let { return it.path }
        }
        if (postGames != null) {
            postGames.firstOrNull { it.path !in prePaths }?.let { return it.path }
        }
        return null
    }

    fun loadRom(romUri: Uri, sramUri: Uri, gbaSlotType: GbaSlotType, gbaRomUri: Uri?, gbaSramUri: Uri?): LoadResult {
        NativeCoreLoader.load()
        lastLoadFailureWasContainerOrFile = false
        var nativePath = resolveUriToNativePath(romUri)
        if (nativePath.isBlank()) {
            lastLoadFailureWasContainerOrFile = true
            return LoadResult.NDS_FAILED
        }
        val lowerCandidate = listOf(nativePath, romUri.lastPathSegment.orEmpty(), romUri.toString())
            .joinToString(" ").lowercase()
        if (lowerCandidate.contains(".artic")) {
            val articUrl = runCatching {
                val rawFile = nativePath.removePrefix("!")
                val txt = if (File(rawFile).isFile) {
                    File(rawFile).readText()
                } else {
                    NativeBridgeHost.appContext.contentResolver.openInputStream(romUri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                }
                txt.lineSequence().map { it.trim() }.firstOrNull {
                    it.startsWith("articbase://") || it.startsWith("articinio://") || it.startsWith("articinin://")
                }
            }.getOrNull()
            if (articUrl.isNullOrBlank()) {
                lastLoadFailureWasContainerOrFile = true
                return LoadResult.NDS_FAILED
            }
            nativePath = articUrl
        }

        var ciaExt = resolveCiaExtension(romUri, nativePath)
        if (ciaExt.isEmpty() && !nativePath.startsWith("artic", ignoreCase = true)) {
            val directTid = runCatching { NativeLibrary.getTitleId(nativePath) }.getOrDefault(0L)
            if (directTid == 0L && runCatching { NativeLibrary.getCiaTitleId(nativePath) }.getOrDefault(0L) != 0L) {
                ciaExt = "cia"
            }
        }
        var titleId = if (ciaExt.isNotEmpty()) {
            runCatching { NativeLibrary.getCiaTitleId(nativePath) }.getOrDefault(0L)
        } else {
            runCatching { NativeLibrary.getTitleId(nativePath) }.getOrDefault(0L)
        }

        if (ciaExt.isNotEmpty()) {
            val tidHigh = (titleId ushr 32) and 0xFFFFFFFFL
            val isUpdateOrDlc = tidHigh == 0x0004000EL || tidHigh == 0x0004008CL
            var installedPath = if (ciaExt != "bcia" && titleId != 0L && !isUpdateOrDlc) {
                resolveInstalledBootPath(titleId)
            } else {
                null
            }

            if (installedPath.isNullOrBlank()) {
                isLongPreparationInProgress = true
                try {
                    val prePaths = runCatching {
                        NativeLibrary.getInstalledGamePaths().map { it.path }.toSet()
                    }.getOrDefault(emptySet())
                    val installStatus = runCatching { NativeLibrary.installCiaSimple(nativePath) }.getOrDefault(-1)
                    if (installStatus != 0) {
                        diagnosticBuf.appendLine("CIA install failed for $nativePath with status=$installStatus")
                        lastLoadFailureWasContainerOrFile = true
                        return LoadResult.NDS_FAILED
                    }
                    val postGames = runCatching { NativeLibrary.getInstalledGamePaths() }.getOrDefault(emptyArray())
                    installedPath = resolveInstalledBootPath(titleId, postGames, prePaths)
                    if (installedPath.isNullOrBlank()) {
                        val tidHex = if (titleId != 0L) String.format(java.util.Locale.ROOT, "%016X", titleId) else "unknown"
                        diagnosticBuf.appendLine("CIA installed (tid=$tidHex) but no bootable title found")
                        lastLoadFailureWasContainerOrFile = true
                        return LoadResult.NDS_FAILED
                    }
                } finally {
                    isLongPreparationInProgress = false
                }
            }
            nativePath = installedPath
            titleId = runCatching { NativeLibrary.getTitleId(nativePath) }.getOrDefault(titleId)
        }

        currentRomPath = nativePath
        currentTitleId = titleId
        if (currentTitleId != 0L) {
            val tidHex = String.format("%016X", currentTitleId)
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(NativeBridgeHost.appContext)
            if (prefs.getBoolean("3ds_auto_profiles_enabled", true)) {
                com.storm3ds.emulator.impl.profiles.GameProfilesRepository.findForTitleId(NativeBridgeHost.appContext, tidHex)?.let { profile ->
                    com.storm3ds.emulator.impl.profiles.GameProfilesRepository.applyProfile(profile)
                    currentConfig?.let { cfg ->
                        val useVulkan = cfg.rendererConfiguration.renderer == com.storm3ds.emulator.domain.model.VideoRenderer.VULKAN
                        val resScale = cfg.rendererConfiguration.internalResolutionScaling.coerceIn(1, 10)
                        NativeLibrary.addDefaultSetting(SettingKeys.graphics_api(), if (useVulkan) "2" else "1")
                        NativeLibrary.addDefaultSetting(SettingKeys.resolution_factor(), resScale.toString())
                    }
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
        return NativeLibrary.resolveNativeOrContentPath(uri)
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
        synchronized(rewindScreenshots) { rewindScreenshots.clear() }
        rewindTask = rewindExecutor.scheduleAtFixedRate({
            if (NativeLibrary.isRunning()) {
                val captured = runCatching { NativeLibrary.captureRewindSnapshot() }.getOrDefault(false)
                if (captured) {
                    val shotBuf = runCatching {
                        bitmapToRewindBuffer(captureFrameBitmapCallback?.invoke())
                    }.getOrNull() ?: ByteBuffer.allocateDirect(256 * 384 * 4)
                    val total = runCatching { NativeLibrary.getRewindTimelineTimestamps().size }.getOrDefault(0)
                    synchronized(rewindScreenshots) {
                        rewindScreenshots.addLast(shotBuf)
                        while (total > 0 && rewindScreenshots.size > total) {
                            rewindScreenshots.removeFirst()
                        }
                    }
                }
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
        return stats.getOrElse(0) { 0.0 }.toFloat()
    }

    fun getEmulationSpeedPercent(): Float {
        val stats = runCatching { NativeLibrary.getPerfStats() }.getOrNull() ?: return 0f
        return (stats.getOrElse(2) { 0.0 } * 100.0).toFloat()
    }

    fun getCurrentRenderer(): Int =
        if (currentConfig?.rendererConfiguration?.renderer == com.storm3ds.emulator.domain.model.VideoRenderer.VULKAN) 1 else 0

    fun pauseEmulation() {
        runCatching { NativeLibrary.pauseEmulation() }
    }

    fun resumeEmulation() {
        runCatching { NativeLibrary.unPauseEmulation() }
    }

    fun debugStepFrame(): Boolean = runCatching { NativeLibrary.stepSingleFrame() }.getOrDefault(false)

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
        val ok = runCatching { NativeLibrary.saveStateSync(slot) }.getOrDefault(false)
        if (!ok) return false
        mirrorCstToSaf(slot, path)
        return true
    }

    fun loadState(path: Uri): Boolean {
        val slot = extractSlotFromUri(path)
        if (NativeLibrary.loadStateIfAvailable(slot)) {
            return true
        }
        if (mirrorSafToCst(slot, path)) {
            return NativeLibrary.loadStateIfAvailable(slot)
        }
        return false
    }

    private fun resolveCstFileForSlot(slot: Int): File? {
        val tid = if (currentTitleId != 0L) currentTitleId else runCatching { NativeLibrary.getRunningTitleId() }.getOrDefault(0L)
        if (tid == 0L) return null
        val userDir = NativeBridgeHost.resolveUserDirectoryPath()
        val statesDir = File(userDir, "states")
        val tidHex = String.format(java.util.Locale.US, "%016X", tid)
        val exact = File(statesDir, "%s.%02d.cst".format(java.util.Locale.US, tidHex, slot))
        if (exact.isFile) return exact
        return statesDir.listFiles()?.firstOrNull {
            it.isFile && it.name.startsWith(tidHex, ignoreCase = true) && it.name.endsWith(".%02d.cst".format(java.util.Locale.US, slot))
        } ?: exact
    }

    private fun mirrorCstToSaf(slot: Int, safUri: Uri) {
        runCatching {
            val cstFile = resolveCstFileForSlot(slot) ?: return
            if (!cstFile.isFile || cstFile.length() == 0L) return
            val cr = NativeBridgeHost.appContext.contentResolver
            val out = runCatching { cr.openOutputStream(safUri, "wt") }.getOrNull()
                ?: runCatching { cr.openOutputStream(safUri, "w") }.getOrNull()
                ?: return
            out.use { o -> cstFile.inputStream().use { i -> i.copyTo(o) } }
        }
    }

    private fun mirrorSafToCst(slot: Int, safUri: Uri): Boolean {
        return runCatching {
            val cstFile = resolveCstFileForSlot(slot) ?: return@runCatching false
            val cr = NativeBridgeHost.appContext.contentResolver
            val input = cr.openInputStream(safUri) ?: return@runCatching false
            input.use { i ->
                val head = ByteArray(16)
                val n = i.read(head)
                if (n <= 0) return@runCatching false
                cstFile.parentFile?.mkdirs()
                cstFile.outputStream().use { o ->
                    o.write(head, 0, n)
                    i.copyTo(o)
                }
            }
            cstFile.length() > 0L
        }.getOrDefault(false)
    }

    private fun extractSlotFromUri(uri: Uri): Int {
        val name = uri.lastPathSegment.orEmpty()
        if (name.contains("periodic", ignoreCase = true)) {
            return 9
        }
        if (name.contains("autosave", ignoreCase = true)) {
            return 10
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

    private fun bitmapToRewindBuffer(bmp: Bitmap?): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(256 * 384 * 4)
        if (bmp == null) return buf
        val scaled = if (bmp.width == 256 && bmp.height == 384) bmp else Bitmap.createScaledBitmap(bmp, 256, 384, true)
        synchronized(rewindPixelScratch) {
            scaled.getPixels(rewindPixelScratch, 0, 256, 0, 0, 256, 384)
            for (px in rewindPixelScratch) {
                buf.put(((px ushr 16) and 0xFF).toByte())
                buf.put(((px ushr 8) and 0xFF).toByte())
                buf.put((px and 0xFF).toByte())
                buf.put(0xFF.toByte())
            }
        }
        buf.rewind()
        if (scaled !== bmp) scaled.recycle()
        return buf
    }

    fun getRewindWindow(): RewindWindow {
        val timestamps = runCatching { NativeLibrary.getRewindTimelineTimestamps() }.getOrElse { LongArray(0) }
        val total = timestamps.size
        val nowMs = System.currentTimeMillis()
        val baseMs = timestamps.firstOrNull() ?: nowMs
        val currentRelMs = (nowMs - baseMs).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        val states = ArrayList<RewindSaveState>(total)
        val emptyBuf = ByteBuffer.allocateDirect(4)
        val fallbackShot = ByteBuffer.allocateDirect(256 * 384 * 4)
        val shots = synchronized(rewindScreenshots) { rewindScreenshots.toList() }
        for (i in timestamps.indices.reversed()) {
            val stepsBack = (total - i).toLong()
            val frameMs = (timestamps[i] - baseMs).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            val shotIdx = shots.size - (total - i)
            val shot = shots.getOrNull(shotIdx) ?: fallbackShot
            shot.rewind()
            states.add(
                RewindSaveState(
                    buffer = emptyBuf,
                    bufferContentSize = stepsBack,
                    screenshotBuffer = shot,
                    frame = frameMs,
                )
            )
        }
        return RewindWindow(currentEmulationFrame = currentRelMs, rewindStates = states, useMilliseconds = true)
    }

    @Volatile
    private var touchIsSecondary: Boolean = false

    fun onScreenTouch(x: Int, y: Int) {
        val primaryRect = com.storm3ds.emulator.ui.emulator.render.Storm3dsSurfaceRenderCoordinator.lastBottomRect
            ?: com.storm3ds.emulator.ui.emulator.render.Storm3dsSurfaceRenderCoordinator.lastHybridBottomRect
        val secondaryRect = com.storm3ds.emulator.ui.emulator.render.Storm3dsSurfaceRenderCoordinator.lastSecondaryBottomRect
        val useSecondary = (primaryRect == null || primaryRect.width <= 0 || primaryRect.height <= 0) &&
            secondaryRect != null && secondaryRect.width > 0 && secondaryRect.height > 0
        val rect = if (useSecondary) secondaryRect else primaryRect
        val rawSx: Float
        val sy: Float
        if (rect != null && rect.width > 0 && rect.height > 0) {
            rawSx = rect.x + (x.coerceIn(0, 319) / 320f) * rect.width
            sy = rect.y + (y.coerceIn(0, 239) / 240f) * rect.height
        } else {
            rawSx = x.coerceIn(0, 319).toFloat()
            sy = y.coerceIn(0, 239).toFloat()
        }
        val sx = if (!useSecondary && (depth3dMode == 1 || depth3dMode == 2 || depth3dMode == 6)) rawSx * 0.5f else rawSx
        touchIsSecondary = useSecondary
        if (!touchDown) {
            touchDown = true
            if (useSecondary) {
                NativeLibrary.onSecondaryTouchEvent(sx, sy, true)
            } else {
                NativeLibrary.onTouchEvent(sx, sy, true)
            }
        } else {
            if (useSecondary) {
                NativeLibrary.onSecondaryTouchMoved(sx, sy)
            } else {
                NativeLibrary.onTouchMoved(sx, sy)
            }
        }
    }

    fun onScreenRelease() {
        if (touchDown) {
            touchDown = false
            if (touchIsSecondary) {
                NativeLibrary.onSecondaryTouchEvent(0f, 0f, false)
            } else {
                NativeLibrary.onTouchEvent(0f, 0f, false)
            }
            touchIsSecondary = false
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

    fun takeScreenshot(): Boolean {
        val captured = runCatching { captureFrameBitmapCallback?.invoke() }.getOrNull()
        if (captured != null) {
            latestScreenshotBitmap = captured
        }
        return true
    }

    fun setFastForwardEnabled(enabled: Boolean) {
        fastForwardActive = enabled
        if (enabled) {
            val limit = if (configuredFastForwardMultiplier < 0f) {
                0.0
            } else {
                (configuredFastForwardMultiplier * 100.0).coerceIn(100.0, 1600.0)
            }
            NativeLibrary.setTemporaryFrameLimit(limit)
        } else if (slowMotionMultiplier < 0.99f) {
            NativeLibrary.setTemporaryFrameLimit((slowMotionMultiplier * 100.0).coerceIn(10.0, 200.0))
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
        slowMotionMultiplier = multiplier.coerceIn(0.1f, 2.0f)
        currentSpeedMultiplier = (slowMotionMultiplier * 100.0).coerceIn(10.0, 500.0)
        if (!fastForwardActive) {
            if (slowMotionMultiplier in 0.99f..1.01f) {
                NativeLibrary.disableTemporaryFrameLimit()
            } else {
                NativeLibrary.setTemporaryFrameLimit(currentSpeedMultiplier)
            }
        }
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
        NativeCoreLoader.load()
        NativeLibrary.initMultiplayer()
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
        NativeCoreLoader.load()
        NativeLibrary.initMultiplayer()
        val trimmed = hostIp.trim()
        val lastColon = trimmed.lastIndexOf(':')
        val parsedHost = if (lastColon > 0 && trimmed.indexOf(':') == lastColon) trimmed.substring(0, lastColon) else trimmed
        val parsedPort = if (lastColon > 0 && trimmed.indexOf(':') == lastColon) trimmed.substring(lastColon + 1).toIntOrNull() ?: 24872 else 24872
        return NetPlayManager.netPlayJoinRoom(parsedHost, parsedPort, playerName.ifBlank { "Player" }, "") == 0
    }

    fun stopLanSession() {
        NativeCoreLoader.load()
        NativeLibrary.initMultiplayer()
        runCatching { NetPlayManager.netPlayLeaveRoom() }
    }

    fun startLanDiscovery(): Boolean {
        NativeCoreLoader.load()
        NativeLibrary.initMultiplayer()
        return true
    }

    fun stopLanDiscovery() {}

    fun getLanDiscoveredSessions(): Array<String> {
        NativeCoreLoader.load()
        NativeLibrary.initMultiplayer()
        return runCatching {
            NetPlayManager.getPublicRooms().map { "${it.name}|${it.ip}:${it.port}|${it.members.size}/${it.maxPlayers}" }.toTypedArray()
        }.getOrDefault(emptyArray())
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
