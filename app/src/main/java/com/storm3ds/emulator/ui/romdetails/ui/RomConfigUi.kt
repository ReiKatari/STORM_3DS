package com.storm3ds.emulator.ui.romdetails.ui

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.draw.clip
import com.storm3ds.emulator.ui.theme.SpaceGrotesk
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import com.storm3ds.emulator.ui.theme.watermelon

import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.storm3ds.emulator.R
import com.storm3ds.emulator.nativebridge.NativeLibrary
import com.storm3ds.emulator.common.Permission
import com.storm3ds.emulator.common.contracts.FilePickerContract
import com.storm3ds.emulator.domain.model.VideoFiltering
import com.storm3ds.emulator.domain.model.VideoRenderer
import com.storm3ds.emulator.domain.model.rom.Rom
import com.storm3ds.emulator.domain.model.rom.config.RomConfig
import com.storm3ds.emulator.domain.model.rom.config.RomInputMode
import com.storm3ds.emulator.domain.model.rom.config.RuntimeMicSource
import com.storm3ds.emulator.ui.common.MelonPreviewSet
import com.storm3ds.emulator.ui.common.component.dialog.SingleChoiceDialog
import com.storm3ds.emulator.ui.common.component.dialog.TextInputDialog
import com.storm3ds.emulator.ui.common.component.dialog.rememberSingleChoiceDialogState
import com.storm3ds.emulator.ui.common.component.dialog.rememberTextInputDialogState
import com.storm3ds.emulator.ui.inputsetup.InputSetupActivity
import com.storm3ds.emulator.ui.layouts.LayoutSelectorActivity
import com.storm3ds.emulator.ui.romdetails.model.RomConfigUiModel
import com.storm3ds.emulator.ui.romdetails.model.RomConfigUiState
import com.storm3ds.emulator.ui.romdetails.model.RomConfigUpdateEvent
import com.storm3ds.emulator.ui.romdetails.model.RomGbaSlotConfigUiModel
import com.storm3ds.emulator.ui.theme.MelonTheme
import java.util.Date
import java.util.UUID

private const val GLES_3_2 = 0x30002

@Composable
fun RomConfigUi(
    modifier: Modifier,
    contentPadding: PaddingValues,
    rom: Rom,
    romConfigUiState: RomConfigUiState,
    onConfigUpdate: (RomConfigUpdateEvent) -> Unit,
    onCustomInputConfigEdited: () -> Unit,
    onSettingFocused: (String, String?) -> Unit = { _, _ -> },
) {
    androidx.compose.runtime.CompositionLocalProvider(LocalConfigFocusReporter provides onSettingFocused) {
        when (romConfigUiState) {
            is RomConfigUiState.Loading -> Loading(modifier.padding(contentPadding))
            is RomConfigUiState.Ready -> Content(
                modifier = modifier,
                contentPadding = contentPadding,
                rom = rom,
                romConfig = romConfigUiState.romConfigUiModel,
                onConfigUpdate = onConfigUpdate,
                onCustomInputConfigEdited = onCustomInputConfigEdited,
            )
        }
    }
}

@Composable
private fun Loading(modifier: Modifier) {
    Box(modifier) {
        CircularProgressIndicator(
            modifier = Modifier.align(Alignment.Center),
            color = MaterialTheme.colors.secondary,
        )
    }
}


enum class RomConfigCategory(@androidx.annotation.StringRes val titleRes: Int) {
    ALL(R.string.ui_rom_cfg_cat_all),
    GRAPHICS_VULKAN(R.string.ui_rom_cfg_cat_gfx),
    SCREENS_LAYOUT(R.string.ui_rom_cfg_cat_screens),
    AUDIO_CONTROLS(R.string.ui_rom_cfg_cat_audio),
    SYSTEM_CPU(R.string.ui_rom_cfg_cat_cpu);
}

@Composable
private fun Content(
    modifier: Modifier,
    contentPadding: PaddingValues,
    rom: Rom,
    romConfig: RomConfigUiModel,
    onConfigUpdate: (RomConfigUpdateEvent) -> Unit,
    onCustomInputConfigEdited: () -> Unit,
) {
    val context = LocalContext.current
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    val renameDialogState = rememberTextInputDialogState()
    val runtimeConsoleDialogState = rememberSingleChoiceDialogState<com.storm3ds.emulator.domain.model.rom.config.RuntimeConsoleType>()
    val micDialogState = rememberSingleChoiceDialogState<RuntimeMicSource>()
    val inputModeDialogState = rememberSingleChoiceDialogState<RomInputMode>()
    val gbaSlotDialogState = rememberSingleChoiceDialogState<RomGbaSlotConfigUiModel.Type>()
    val videoRendererDialogState = rememberSingleChoiceDialogState<VideoRenderer?>()
    val threadedRenderingDialogState = rememberSingleChoiceDialogState<Boolean?>()
    val internalResolutionDialogState = rememberSingleChoiceDialogState<Int?>()
    val videoFilteringDialogState = rememberSingleChoiceDialogState<VideoFiltering?>()
    val retroAchievementsDialogState = rememberSingleChoiceDialogState<Boolean?>()
    val dualScreenPresetDialogState = rememberSingleChoiceDialogState<com.storm3ds.emulator.domain.model.DualScreenPreset?>()
    val translatorSourceLangDialogState = rememberSingleChoiceDialogState<String?>()
    val arm9OverclockDialogState = rememberSingleChoiceDialogState<Int?>()
    val fastForwardSpeedDialogState = rememberSingleChoiceDialogState<Float?>()
    val customVulkanDriverDialogState = rememberSingleChoiceDialogState<String?>()
    val isNew3dsDialogState = rememberSingleChoiceDialogState<Boolean?>()
    val cpuClockDialogState = rememberSingleChoiceDialogState<Int?>()
    val render3dModeDialogState = rememberSingleChoiceDialogState<Int?>()
    val render3dFactorDialogState = rememberSingleChoiceDialogState<Int?>()
    val limitFramesAheadDialogState = rememberSingleChoiceDialogState<Boolean?>()
    val skipDupFramesDialogState = rememberSingleChoiceDialogState<Boolean?>()
    val disableRightEyeDialogState = rememberSingleChoiceDialogState<Boolean?>()
    val textureFilterDialogState = rememberSingleChoiceDialogState<Int?>()
    val simulateGpuTimingsDialogState = rememberSingleChoiceDialogState<Boolean?>()
    val simulateHeadphonesDialogState = rememberSingleChoiceDialogState<Boolean?>()
    val realtimeAudioDialogState = rememberSingleChoiceDialogState<Boolean?>()
    val retroArchPresetPathDialogState = rememberTextInputDialogState()
    val retroArchParametersDialogState = rememberTextInputDialogState()

    val customInputSetupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onCustomInputConfigEdited()
    }
    val layoutSelectorLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val layoutId = result.data?.getStringExtra(LayoutSelectorActivity.KEY_SELECTED_LAYOUT_ID)?.let { UUID.fromString(it) }
            onConfigUpdate(RomConfigUpdateEvent.LayoutUpdate(layoutId))
        }
    }
    val gbaRomSelectorLauncher = rememberLauncherForActivityResult(FilePickerContract(Permission.READ)) { result ->
        if (result != null) onConfigUpdate(RomConfigUpdateEvent.GbaRomPathUpdate(result))
    }
    val gbaSaveSelectorLauncher = rememberLauncherForActivityResult(FilePickerContract(Permission.READ_WRITE)) { result ->
        if (result != null) onConfigUpdate(RomConfigUpdateEvent.GbaSavePathUpdate(result))
    }

    val runtimeConsoleOptions = stringArrayResource(id = R.array.game_runtime_console_type_options)
    val micOptions = stringArrayResource(id = R.array.game_runtime_mic_source_options)
    val inputModeOptions = stringArrayResource(id = R.array.rom_input_mode_options)
    val gbaSlotOptions = stringArrayResource(id = R.array.gba_slot_options)
    val rendererOptions = stringArrayResource(id = R.array.video_renderer_options)
    val internalResolutionOptions = stringArrayResource(id = R.array.video_internal_resolution_options)
    val internalResolutionValues = stringArrayResource(id = R.array.video_internal_resolution_values).mapNotNull { it.toIntOrNull() }
    val videoFilteringOptions = stringArrayResource(id = R.array.video_filtering_options)
    val translatorLangEntries = stringArrayResource(id = R.array.translator_source_lang_options)
    val translatorLangValues = stringArrayResource(id = R.array.translator_source_lang_values)
    val depthModeOptions = stringArrayResource(id = R.array.video_vulkan_3d_depth_mode_options)
    val depthModeIntValues = listOf(0, 7, 3, 1, 2, 4, 5, 6)
    val depthStrengthValues = listOf(0, 25, 50, 75, 100)
    val cpuClockValues = listOf(50, 75, 100, 125, 150, 200)
    fun depthModeLabelForInt(modeInt: Int): String {
        val idx = when (modeInt) {
            NativeLibrary.StereoMode.OFF.nativeValue -> 0
            NativeLibrary.StereoMode.GYRO_PARALLAX.nativeValue -> 1
            NativeLibrary.StereoMode.ANAGLYPH.nativeValue -> 2
            NativeLibrary.StereoMode.SIDE_BY_SIDE.nativeValue -> 3
            NativeLibrary.StereoMode.SIDE_BY_SIDE_FULL.nativeValue -> 4
            NativeLibrary.StereoMode.INTERLACED.nativeValue -> 5
            NativeLibrary.StereoMode.REVERSE_INTERLACED.nativeValue -> 6
            NativeLibrary.StereoMode.CARDBOARD_VR.nativeValue -> 7
            else -> 0
        }
        return depthModeOptions.getOrNull(idx) ?: depthModeOptions.firstOrNull().orEmpty()
    }
    val useGlobal = stringResource(R.string.use_global_preference)
    val activityManager = context.getSystemService(ActivityManager::class.java)
    val supportsOpenGlRenderer = (activityManager?.deviceConfigurationInfo?.reqGlEsVersion ?: 0) >= GLES_3_2
    val supportsComputeRenderer = supportsOpenGlRenderer && Build.HARDWARE.equals("qcom", ignoreCase = true)
    fun supportsRenderer(renderer: VideoRenderer): Boolean {
        return when (renderer) {
            VideoRenderer.OPENGL -> supportsOpenGlRenderer
            VideoRenderer.COMPUTE -> supportsComputeRenderer
            else -> true
        }
    }
    val selectedRenderer = romConfig.videoRenderer?.takeIf { supportsRenderer(it) }
    val effectiveRenderer = selectedRenderer ?: romConfig.globalVideoRenderer
    fun supportsFiltering(renderer: VideoRenderer, filtering: VideoFiltering): Boolean {
        return filtering.isSupportedByRenderer(renderer)
    }
    val selectedFiltering = romConfig.videoFiltering?.takeIf { supportsFiltering(effectiveRenderer, it) }
    val effectiveGlobalFiltering = romConfig.globalVideoFiltering.takeIf { supportsFiltering(effectiveRenderer, it) }
        ?: VideoFiltering.NONE
    val effectiveFiltering = selectedFiltering ?: effectiveGlobalFiltering
    fun useGlobalWithValue(value: String): String {
        return context.getString(R.string.use_global_preference_with_value, value)
    }
    val globalConsoleLabel = runtimeConsoleOptions.getOrNull(romConfig.globalRuntimeConsoleType.ordinal + 1) ?: romConfig.globalRuntimeConsoleType.name
    val globalMicLabel = micOptions.getOrNull(romConfig.globalRuntimeMicSource.ordinal + 1) ?: micOptions.firstOrNull().orEmpty()
    val globalInputModeLabel = context.getString(R.string.global_controller_mapping)
    val globalLayoutLabel = romConfig.globalLayoutName ?: context.getString(R.string.not_set)
    val globalRendererLabel = rendererOptions.getOrNull(romConfig.globalVideoRenderer.ordinal) ?: romConfig.globalVideoRenderer.name
    val globalThreadedRenderingLabel = if (romConfig.globalThreadedRendering) {
        context.getString(R.string.on)
    } else {
        context.getString(R.string.off)
    }
    val globalResolutionIndex = internalResolutionValues.indexOf(romConfig.globalInternalResolutionScaling).takeIf { it >= 0 }
    val globalInternalResolutionLabel = globalResolutionIndex?.let { internalResolutionOptions.getOrNull(it) } ?: "1x"
    val globalVideoFilteringLabel = videoFilteringOptions.getOrNull(effectiveGlobalFiltering.ordinal) ?: effectiveGlobalFiltering.name
    val globalRetroArchPresetPathLabel = romConfig.globalRetroArchShaderPresetPath ?: context.getString(R.string.not_set)
    val globalRetroArchParametersLabel = romConfig.globalRetroArchShaderParameters ?: context.getString(R.string.not_set)
    fun rendererLabel(r: VideoRenderer): String = when (r) {
        VideoRenderer.VULKAN -> "Vulkan"
        VideoRenderer.OPENGL -> "OpenGL ES 3.2"
        else -> r.name
    }
    val rendererItems = buildList<VideoRenderer?> {
        add(null)
        add(VideoRenderer.VULKAN)
        if (supportsOpenGlRenderer) {
            add(VideoRenderer.OPENGL)
        }
    }
    val filteringItems = listOf(null) + VideoFiltering.entries.filter { filtering ->
        supportsFiltering(effectiveRenderer, filtering)
    }

    val colors = watermelon
    val isRu = remember { java.util.Locale.getDefault().language.startsWith("ru") }
    var selectedFilter by remember { mutableStateOf(RomConfigCategory.ALL) }

    val activeOverridesCount = remember(romConfig) {
        var count = 0
        if (romConfig.customName != null) count++
        if (romConfig.runtimeConsoleType != com.storm3ds.emulator.domain.model.rom.config.RuntimeConsoleType.DEFAULT) count++
        if (romConfig.arm9Overclock != null) count++
        if (romConfig.fastForwardSpeedMultiplier != null) count++
        if (romConfig.runtimeMicSource != RuntimeMicSource.DEFAULT) count++
        if (romConfig.videoRenderer != null) count++
        if (romConfig.threadedRendering != null) count++
        if (romConfig.internalResolutionScaling != null) count++
        if (romConfig.videoFiltering != null) count++
        if (romConfig.retroArchShaderPresetPath != null) count++
        if (romConfig.retroArchShaderParameters != null) count++
        if (romConfig.customVulkanDriverName != null) count++
        if (romConfig.inputMode != RomInputMode.GLOBAL) count++
        if (romConfig.retroAchievementsEnabled != null) count++
        if (romConfig.dualScreenPreset != null) count++
        if (romConfig.layoutId != null) count++
        if (romConfig.translatorSourceLang != null) count++
        if (romConfig.isNew3ds != null) count++
        if (romConfig.cpuClockPercentage != null) count++
        if (romConfig.render3dMode != null) count++
        if (romConfig.render3dFactor != null) count++
        if (romConfig.useLimitFramesAheadOfGpu != null) count++
        if (romConfig.skipDuplicateFrames != null) count++
        if (romConfig.disableRightEyeRender != null) count++
        if (romConfig.textureFilter != null) count++
        if (romConfig.simulate3dsGpuTimings != null) count++
        if (romConfig.simulateHeadphonesPlugged != null) count++
        if (romConfig.enableRealtimeAudio != null) count++
        count
    }

    val showSystemCpu = selectedFilter == RomConfigCategory.ALL || selectedFilter == RomConfigCategory.SYSTEM_CPU
    val showGraphics = selectedFilter == RomConfigCategory.ALL || selectedFilter == RomConfigCategory.GRAPHICS_VULKAN
    val showScreens = selectedFilter == RomConfigCategory.ALL || selectedFilter == RomConfigCategory.SCREENS_LAYOUT
    val showAudioControls = selectedFilter == RomConfigCategory.ALL || selectedFilter == RomConfigCategory.AUDIO_CONTROLS
    val showGbaSlot = false

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(
                start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
            ),
    ) {
        // Summary Banner Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (activeOverridesCount > 0) colors.green.copy(alpha = 0.12f) else colors.surface2)
                .border(
                    width = 1.dp,
                    color = if (activeOverridesCount > 0) colors.green.copy(alpha = 0.45f) else colors.line,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (activeOverridesCount > 0) colors.green.copy(alpha = 0.22f) else colors.surface3),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        tint = if (activeOverridesCount > 0) colors.green else colors.text3,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column {
                    Text(
                        text = if (activeOverridesCount > 0) {
                            stringResource(R.string.ui_rom_cfg_active_fmt, activeOverridesCount)
                        } else {
                            stringResource(R.string.ui_rom_cfg_using_defaults)
                        },
                        color = colors.text,
                        fontFamily = SpaceGrotesk,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (activeOverridesCount > 0) {
                            stringResource(R.string.ui_rom_cfg_overridden_desc)
                        } else {
                            stringResource(R.string.ui_rom_cfg_inherited_desc)
                        },
                        color = colors.text3,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                    )
                }
            }
        }

        // Quick Category Filter / Jump Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RomConfigCategory.values().forEach { cat ->
                val isSelected = (selectedFilter == cat)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) colors.green.copy(alpha = 0.22f) else colors.surface2)
                        .border(
                            width = if (isSelected) 1.dp else 0.5.dp,
                            color = if (isSelected) colors.green else colors.line,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .clickable { selectedFilter = cat }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(cat.titleRes),
                        color = if (isSelected) colors.green else colors.text2,
                        fontSize = 11.5.sp,
                        fontFamily = SpaceGrotesk,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        if (showSystemCpu) {
        ConfigSection(title = stringResource(R.string.rom_details_configuration_tab)) {
            ConfigRow(
                title = stringResource(R.string.label_rom_config_custom_name),
                value = romConfig.customName ?: rom.name,
                showDivider = false,
                onClick = {
                    renameDialogState.show(
                        initialText = romConfig.customName ?: rom.name,
                        onConfirm = { newName -> onConfigUpdate(RomConfigUpdateEvent.CustomNameUpdate(newName.ifBlank { null })) },
                    )
                },
            )
        }
        }

        if (showSystemCpu) {
        ConfigSection(title = stringResource(R.string.pref_3ds_hardware_cat)) {
            val globalNew3dsLabel = stringResource(if (romConfig.globalIsNew3ds) R.string.on else R.string.off)
            ConfigRow(
                title = stringResource(R.string.pref_3ds_new3ds_title),
                value = when (romConfig.isNew3ds) {
                    null -> useGlobalWithValue(globalNew3dsLabel)
                    true -> stringResource(R.string.on)
                    false -> stringResource(R.string.off)
                },
                showDivider = false,
                onClick = {
                    isNew3dsDialogState.show(
                        title = context.getString(R.string.pref_3ds_new3ds_title),
                        items = listOf(null, true, false),
                        labelOf = {
                            when (it) {
                                null -> useGlobalWithValue(globalNew3dsLabel)
                                true -> context.getString(R.string.on)
                                false -> context.getString(R.string.off)
                            }
                        },
                        selected = romConfig.isNew3ds,
                        onSelect = { onConfigUpdate(RomConfigUpdateEvent.IsNew3dsUpdate(it)) },
                    )
                },
            )
            ConfigRow(
                title = stringResource(R.string.pref_3ds_cpu_clock_title),
                value = romConfig.cpuClockPercentage?.let { "$it%" }
                    ?: useGlobalWithValue("${romConfig.globalCpuClockPercentage}%"),
                showDivider = true,
                onClick = {
                    cpuClockDialogState.show(
                        title = context.getString(R.string.pref_3ds_cpu_clock_title),
                        items = listOf(null) + cpuClockValues,
                        labelOf = {
                            it?.let { "$it%" } ?: useGlobalWithValue("${romConfig.globalCpuClockPercentage}%")
                        },
                        selected = romConfig.cpuClockPercentage,
                        onSelect = { onConfigUpdate(RomConfigUpdateEvent.CpuClockPercentageUpdate(it)) },
                    )
                },
            )
        }
        }

        if (showSystemCpu) {
        ConfigSection(title = stringResource(R.string.rom_config_speed_multiplier)) {
            val speedLabel = when (romConfig.fastForwardSpeedMultiplier) {
                null -> useGlobalWithValue(
                    if (romConfig.globalFastForwardSpeedMultiplier <= 0f) {
                        stringResource(R.string.rom_config_speed_unlimited)
                    } else {
                        "${romConfig.globalFastForwardSpeedMultiplier}x"
                    }
                )
                -1f -> stringResource(R.string.rom_config_speed_unlimited)
                else -> "${romConfig.fastForwardSpeedMultiplier}x"
            }
            ConfigRow(
                title = stringResource(R.string.rom_config_speed_multiplier),
                value = speedLabel,
                showDivider = true,
                onClick = {
                    fastForwardSpeedDialogState.show(
                        title = context.getString(R.string.rom_config_speed_multiplier),
                        items = listOf(null, -1f, 1.5f, 2f, 3f, 4f, 8f),
                        labelOf = {
                            when (it) {
                                null -> useGlobalWithValue(
                                    if (romConfig.globalFastForwardSpeedMultiplier <= 0f) {
                                        context.getString(R.string.rom_config_speed_unlimited)
                                    } else {
                                        "${romConfig.globalFastForwardSpeedMultiplier}x"
                                    }
                                )
                                -1f -> context.getString(R.string.rom_config_speed_unlimited)
                                else -> "${it}x"
                            }
                        },
                        selected = romConfig.fastForwardSpeedMultiplier,
                        onSelect = { onConfigUpdate(RomConfigUpdateEvent.FastForwardSpeedMultiplierUpdate(it)) },
                    )
                },
            )
        }
        }

        if (showAudioControls) {
        ConfigSection(title = stringResource(R.string.audio)) {
            ConfigRow(
                title = stringResource(R.string.microphone_source),
                value = if (romConfig.runtimeMicSource == RuntimeMicSource.DEFAULT) {
                    useGlobalWithValue(globalMicLabel)
                } else {
                    micOptions[romConfig.runtimeMicSource.ordinal]
                },
                showDivider = false,
                onClick = {
                    micDialogState.show(
                        title = context.getString(R.string.microphone_source),
                        items = RuntimeMicSource.entries.toList(),
                        labelOf = {
                            if (it == RuntimeMicSource.DEFAULT) {
                                useGlobalWithValue(globalMicLabel)
                            } else {
                                micOptions[it.ordinal]
                            }
                        },
                        selected = romConfig.runtimeMicSource,
                        onSelect = { onConfigUpdate(RomConfigUpdateEvent.RuntimeMicSourceUpdate(it)) },
                    )
                },
            )

        }
        }

        if (showGraphics) {
        ConfigSection(title = stringResource(R.string.label_rom_config_video)) {
            val vulkanDriverLabel = if (!romConfig.isCustomVulkanDriverSupported) {
                stringResource(R.string.rom_config_vulkan_driver_blocked_value)
            } else {
                when (romConfig.customVulkanDriverName) {
                    null -> useGlobalWithValue(romConfig.globalVulkanDriverDisplayName)
                    "__SYSTEM__" -> stringResource(R.string.rom_config_vulkan_driver_system)
                    else -> romConfig.availableVulkanDrivers.firstOrNull { it.first == romConfig.customVulkanDriverName }?.second
                        ?: romConfig.customVulkanDriverName.orEmpty()
                }
            }
            ConfigRow(
                title = stringResource(R.string.rom_config_vulkan_driver),
                value = vulkanDriverLabel,
                enabled = romConfig.isCustomVulkanDriverSupported,
                showDivider = false,
                onClick = {
                    if (!romConfig.isCustomVulkanDriverSupported) return@ConfigRow
                    customVulkanDriverDialogState.show(
                        title = context.getString(R.string.rom_config_vulkan_driver),
                        items = romConfig.availableVulkanDrivers.map { it.first },
                        labelOf = { driverId ->
                            when (driverId) {
                                null -> useGlobalWithValue(romConfig.globalVulkanDriverDisplayName)
                                "__SYSTEM__" -> context.getString(R.string.rom_config_vulkan_driver_system)
                                else -> romConfig.availableVulkanDrivers.firstOrNull { it.first == driverId }?.second ?: (driverId ?: "")
                            }
                        },
                        selected = romConfig.customVulkanDriverName,
                        onSelect = { onConfigUpdate(RomConfigUpdateEvent.CustomVulkanDriverUpdate(it)) },
                    )
                },
            )
            ConfigRow(
                title = stringResource(R.string.renderer),
                value = selectedRenderer?.let { rendererLabel(it) } ?: useGlobalWithValue(rendererLabel(romConfig.globalVideoRenderer)),
                showDivider = true,
                onClick = {
                    videoRendererDialogState.show(
                        title = context.getString(R.string.renderer),
                        items = rendererItems,
                        labelOf = { renderer -> renderer?.let { rendererLabel(it) } ?: useGlobalWithValue(rendererLabel(romConfig.globalVideoRenderer)) },
                        selected = selectedRenderer,
                        onSelect = { onConfigUpdate(RomConfigUpdateEvent.VideoRendererUpdate(it)) },
                    )
                },
            )
            AnimatedVisibility(visible = effectiveRenderer == VideoRenderer.SOFTWARE) {
                ConfigRow(
                    title = stringResource(R.string.threaded_rendering),
                    value = when (romConfig.threadedRendering) {
                        true -> stringResource(R.string.on)
                        false -> stringResource(R.string.off)
                        null -> useGlobalWithValue(globalThreadedRenderingLabel)
                    },
                    showDivider = true,
                    onClick = {
                        threadedRenderingDialogState.show(
                            title = context.getString(R.string.threaded_rendering),
                            items = listOf(null, true, false),
                            labelOf = {
                                when (it) {
                                    true -> context.getString(R.string.on)
                                    false -> context.getString(R.string.off)
                                    null -> useGlobalWithValue(globalThreadedRenderingLabel)
                                }
                            },
                            selected = romConfig.threadedRendering,
                            onSelect = { onConfigUpdate(RomConfigUpdateEvent.ThreadedRenderingUpdate(it)) },
                        )
                    },
                )
            }
            AnimatedVisibility(visible = effectiveRenderer != VideoRenderer.SOFTWARE) {
                val currentResolutionIndex = romConfig.internalResolutionScaling?.let { internalResolutionValues.indexOf(it).takeIf { idx -> idx >= 0 } }
                val currentResolutionLabel = currentResolutionIndex?.let { internalResolutionOptions.getOrNull(it) } ?: useGlobalWithValue(globalInternalResolutionLabel)
                ConfigRow(
                    title = stringResource(R.string.internal_resolution),
                    value = currentResolutionLabel,
                    showDivider = true,
                    onClick = {
                        internalResolutionDialogState.show(
                            title = context.getString(R.string.internal_resolution),
                            items = listOf(null) + internalResolutionValues,
                            labelOf = { scaling ->
                                val idx = scaling?.let { internalResolutionValues.indexOf(it).takeIf { i -> i >= 0 } }
                                idx?.let { internalResolutionOptions.getOrNull(it) } ?: useGlobalWithValue(globalInternalResolutionLabel)
                            },
                            selected = romConfig.internalResolutionScaling,
                            onSelect = { onConfigUpdate(RomConfigUpdateEvent.InternalResolutionScalingUpdate(it)) },
                        )
                    },
                )
            }
            ConfigRow(
                title = stringResource(R.string.filter),
                value = selectedFiltering?.let { videoFilteringOptions.getOrNull(it.ordinal) ?: it.name } ?: useGlobalWithValue(globalVideoFilteringLabel),
                showDivider = true,
                onClick = {
                    videoFilteringDialogState.show(
                        title = context.getString(R.string.filter),
                        items = filteringItems,
                        labelOf = { filtering -> filtering?.let { videoFilteringOptions.getOrNull(it.ordinal) ?: it.name } ?: useGlobalWithValue(globalVideoFilteringLabel) },
                        selected = selectedFiltering,
                        onSelect = { onConfigUpdate(RomConfigUpdateEvent.VideoFilteringUpdate(it)) },
                    )
                },
            )
            ConfigRow(
                title = stringResource(R.string.video_vulkan_3d_depth_mode_title),
                value = romConfig.render3dMode?.let { depthModeLabelForInt(it) }
                    ?: useGlobalWithValue(depthModeLabelForInt(romConfig.globalRender3dMode)),
                showDivider = true,
                onClick = {
                    val orderedModes = listOf(
                        null,
                        NativeLibrary.StereoMode.OFF.nativeValue,
                        NativeLibrary.StereoMode.GYRO_PARALLAX.nativeValue,
                        NativeLibrary.StereoMode.ANAGLYPH.nativeValue,
                        NativeLibrary.StereoMode.SIDE_BY_SIDE.nativeValue,
                        NativeLibrary.StereoMode.SIDE_BY_SIDE_FULL.nativeValue,
                        NativeLibrary.StereoMode.INTERLACED.nativeValue,
                        NativeLibrary.StereoMode.REVERSE_INTERLACED.nativeValue,
                        NativeLibrary.StereoMode.CARDBOARD_VR.nativeValue,
                    )
                    render3dModeDialogState.show(
                        title = context.getString(R.string.video_vulkan_3d_depth_mode_title),
                        items = orderedModes,
                        labelOf = { m ->
                            m?.let { depthModeLabelForInt(it) }
                                ?: useGlobalWithValue(depthModeLabelForInt(romConfig.globalRender3dMode))
                        },
                        selected = romConfig.render3dMode,
                        onSelect = { onConfigUpdate(RomConfigUpdateEvent.Render3dModeUpdate(it)) },
                    )
                },
            )
            ConfigRow(
                title = stringResource(R.string.video_vulkan_3d_depth_strength_title),
                value = romConfig.render3dFactor?.let { "$it%" }
                    ?: useGlobalWithValue("${romConfig.globalRender3dFactor}%"),
                showDivider = true,
                onClick = {
                    render3dFactorDialogState.show(
                        title = context.getString(R.string.video_vulkan_3d_depth_strength_title),
                        items = listOf(null) + depthStrengthValues,
                        labelOf = { f ->
                            f?.let { "$it%" } ?: useGlobalWithValue("${romConfig.globalRender3dFactor}%")
                        },
                        selected = romConfig.render3dFactor,
                        onSelect = { onConfigUpdate(RomConfigUpdateEvent.Render3dFactorUpdate(it)) },
                    )
                },
            )
            val texFilterLabels = stringArrayResource(R.array.texture_filter_options)
            fun texFilterName(idx: Int): String = texFilterLabels.getOrNull(idx) ?: idx.toString()
            ConfigRow(
                title = stringResource(R.string.pref_3ds_tex_filter_title),
                value = romConfig.textureFilter?.let { texFilterName(it) }
                    ?: useGlobalWithValue(texFilterName(romConfig.globalTextureFilter)),
                showDivider = true,
                onClick = {
                    val items = if (effectiveRenderer == VideoRenderer.VULKAN) {
                        listOf(null, 0, 2, 3, 4, 5)
                    } else {
                        listOf(null, 0, 1, 2, 3, 4, 5)
                    }
                    textureFilterDialogState.show(
                        title = context.getString(R.string.pref_3ds_tex_filter_title),
                        items = items,
                        labelOf = { it?.let(::texFilterName) ?: useGlobalWithValue(texFilterName(romConfig.globalTextureFilter)) },
                        selected = romConfig.textureFilter,
                        onSelect = { onConfigUpdate(RomConfigUpdateEvent.TextureFilterUpdate(it)) },
                    )
                },
            )
            fun boolOverrideLabel(value: Boolean?, globalVal: Boolean): String = when (value) {
                true -> context.getString(R.string.on)
                false -> context.getString(R.string.off)
                null -> useGlobalWithValue(context.getString(if (globalVal) R.string.on else R.string.off))
            }
            ConfigRow(
                title = stringResource(R.string.pref_3ds_limit_frames_ahead_title),
                value = boolOverrideLabel(romConfig.useLimitFramesAheadOfGpu, romConfig.globalUseLimitFramesAheadOfGpu),
                showDivider = true,
                onClick = {
                    limitFramesAheadDialogState.show(
                        title = context.getString(R.string.pref_3ds_limit_frames_ahead_title),
                        items = listOf(null, true, false),
                        labelOf = { boolOverrideLabel(it, romConfig.globalUseLimitFramesAheadOfGpu) },
                        selected = romConfig.useLimitFramesAheadOfGpu,
                        onSelect = { onConfigUpdate(RomConfigUpdateEvent.UseLimitFramesAheadOfGpuUpdate(it)) },
                    )
                },
            )
            ConfigRow(
                title = stringResource(R.string.pref_3ds_skip_dup_title),
                value = boolOverrideLabel(romConfig.skipDuplicateFrames, romConfig.globalSkipDuplicateFrames),
                showDivider = true,
                onClick = {
                    skipDupFramesDialogState.show(
                        title = context.getString(R.string.pref_3ds_skip_dup_title),
                        items = listOf(null, true, false),
                        labelOf = { boolOverrideLabel(it, romConfig.globalSkipDuplicateFrames) },
                        selected = romConfig.skipDuplicateFrames,
                        onSelect = { onConfigUpdate(RomConfigUpdateEvent.SkipDuplicateFramesUpdate(it)) },
                    )
                },
            )
            ConfigRow(
                title = stringResource(R.string.pref_3ds_disable_right_eye_title),
                value = boolOverrideLabel(romConfig.disableRightEyeRender, romConfig.globalDisableRightEyeRender),
                showDivider = true,
                onClick = {
                    disableRightEyeDialogState.show(
                        title = context.getString(R.string.pref_3ds_disable_right_eye_title),
                        items = listOf(null, true, false),
                        labelOf = { boolOverrideLabel(it, romConfig.globalDisableRightEyeRender) },
                        selected = romConfig.disableRightEyeRender,
                        onSelect = { onConfigUpdate(RomConfigUpdateEvent.DisableRightEyeRenderUpdate(it)) },
                    )
                },
            )
            ConfigRow(
                title = stringResource(R.string.pref_3ds_simulate_gpu_timings_title),
                value = boolOverrideLabel(romConfig.simulate3dsGpuTimings, romConfig.globalSimulate3dsGpuTimings),
                showDivider = true,
                onClick = {
                    simulateGpuTimingsDialogState.show(
                        title = context.getString(R.string.pref_3ds_simulate_gpu_timings_title),
                        items = listOf(null, true, false),
                        labelOf = { boolOverrideLabel(it, romConfig.globalSimulate3dsGpuTimings) },
                        selected = romConfig.simulate3dsGpuTimings,
                        onSelect = { onConfigUpdate(RomConfigUpdateEvent.Simulate3dsGpuTimingsUpdate(it)) },
                    )
                },
            )
            AnimatedVisibility(visible = effectiveRenderer != VideoRenderer.SOFTWARE && effectiveFiltering == VideoFiltering.RETROARCH) {
                Column {
                    ConfigRow(
                        title = stringResource(R.string.video_retroarch_shader_preset_title),
                        value = romConfig.retroArchShaderPresetPath ?: useGlobalWithValue(globalRetroArchPresetPathLabel),
                        showDivider = true,
                        onClick = {
                            if (romConfig.hasValidRetroArchShaderRoot) {
                                retroArchPresetPathDialogState.show(
                                    initialText = romConfig.retroArchShaderPresetPath.orEmpty(),
                                    onConfirm = { onConfigUpdate(RomConfigUpdateEvent.RetroArchShaderPresetPathUpdate(it.ifBlank { null })) },
                                )
                            } else {
                                Toast.makeText(context, R.string.retroarch_shader_root_not_valid, Toast.LENGTH_LONG).show()
                            }
                        },
                    )
                    ConfigRow(
                        title = stringResource(R.string.video_retroarch_shader_parameters_title),
                        value = romConfig.retroArchShaderParameters ?: useGlobalWithValue(globalRetroArchParametersLabel),
                        showDivider = true,
                        onClick = {
                            if (romConfig.hasValidRetroArchShaderRoot) {
                                retroArchParametersDialogState.show(
                                    initialText = romConfig.retroArchShaderParameters.orEmpty(),
                                    onConfirm = { onConfigUpdate(RomConfigUpdateEvent.RetroArchShaderParametersUpdate(it.ifBlank { null })) },
                                )
                            } else {
                                Toast.makeText(context, R.string.retroarch_shader_root_not_valid, Toast.LENGTH_LONG).show()
                            }
                        },
                    )
                }
            }
        }
        }

        if (showAudioControls) {
        ConfigSection(title = stringResource(R.string.pref_cat_audio_output)) {
            fun boolAudioLabel(value: Boolean?, globalVal: Boolean): String = when (value) {
                true -> context.getString(R.string.on)
                false -> context.getString(R.string.off)
                null -> useGlobalWithValue(context.getString(if (globalVal) R.string.on else R.string.off))
            }
            ConfigRow(
                title = stringResource(R.string.pref_3ds_realtime_audio_title),
                value = boolAudioLabel(romConfig.enableRealtimeAudio, romConfig.globalEnableRealtimeAudio),
                showDivider = false,
                onClick = {
                    realtimeAudioDialogState.show(
                        title = context.getString(R.string.pref_3ds_realtime_audio_title),
                        items = listOf(null, true, false),
                        labelOf = { boolAudioLabel(it, romConfig.globalEnableRealtimeAudio) },
                        selected = romConfig.enableRealtimeAudio,
                        onSelect = { onConfigUpdate(RomConfigUpdateEvent.EnableRealtimeAudioUpdate(it)) },
                    )
                },
            )
            ConfigRow(
                title = stringResource(R.string.pref_3ds_simulate_headphones_title),
                value = boolAudioLabel(romConfig.simulateHeadphonesPlugged, romConfig.globalSimulateHeadphonesPlugged),
                showDivider = true,
                onClick = {
                    simulateHeadphonesDialogState.show(
                        title = context.getString(R.string.pref_3ds_simulate_headphones_title),
                        items = listOf(null, true, false),
                        labelOf = { boolAudioLabel(it, romConfig.globalSimulateHeadphonesPlugged) },
                        selected = romConfig.simulateHeadphonesPlugged,
                        onSelect = { onConfigUpdate(RomConfigUpdateEvent.SimulateHeadphonesPluggedUpdate(it)) },
                    )
                },
            )
        }
        ConfigSection(title = stringResource(R.string.label_rom_config_input_mode)) {
            ConfigRow(
                title = stringResource(R.string.label_rom_config_input_mode),
                value = if (romConfig.inputMode == RomInputMode.GLOBAL) {
                    useGlobalWithValue(globalInputModeLabel)
                } else {
                    inputModeOptions[romConfig.inputMode.ordinal]
                },
                showDivider = false,
                onClick = {
                    inputModeDialogState.show(
                        title = context.getString(R.string.label_rom_config_input_mode),
                        items = RomInputMode.entries.toList(),
                        labelOf = {
                            if (it == RomInputMode.GLOBAL) {
                                useGlobalWithValue(globalInputModeLabel)
                            } else {
                                inputModeOptions[it.ordinal]
                            }
                        },
                        selected = romConfig.inputMode,
                        onSelect = { onConfigUpdate(RomConfigUpdateEvent.InputModeUpdate(it)) },
                    )
                },
            )
            AnimatedVisibility(visible = romConfig.inputMode == RomInputMode.CUSTOM) {
                ConfigRow(
                    title = stringResource(R.string.label_rom_config_custom_input_mapping),
                    value = stringResource(R.string.edit),
                    showDivider = true,
                    onClick = {
                        customInputSetupLauncher.launch(InputSetupActivity.getRomCustomIntent(context, rom))
                    },
                )
            }
        }
        }

        if (false) {
        ConfigSection(title = stringResource(R.string.label_rom_config_retroachievements)) {
            ConfigRow(
                title = stringResource(R.string.label_rom_config_retroachievements_for_rom),
                value = retroAchievementsModeLabel(
                    context = context,
                    value = romConfig.retroAchievementsEnabled,
                    globalEnabled = romConfig.globalRetroAchievementsEnabled,
                ),
                onClick = {
                    retroAchievementsDialogState.show(
                        title = context.getString(R.string.label_rom_config_retroachievements_for_rom),
                        items = listOf(null, true, false),
                        labelOf = { value ->
                            retroAchievementsModeLabel(
                                context = context,
                                value = value,
                                globalEnabled = romConfig.globalRetroAchievementsEnabled,
                            )
                        },
                        selected = romConfig.retroAchievementsEnabled,
                        onSelect = { onConfigUpdate(RomConfigUpdateEvent.RetroAchievementsEnabledUpdate(it)) },
                    )
                },
            )
        }
        }

        if (showScreens) {
        ConfigSection(title = stringResource(R.string.controller_layout)) {
            ConfigRow(
                title = stringResource(R.string.controller_layout),
                value = romConfig.layoutName ?: useGlobalWithValue(globalLayoutLabel),
                showDivider = false,
                onClick = {
                    val intent = Intent(context, LayoutSelectorActivity::class.java).apply {
                        putExtra(LayoutSelectorActivity.KEY_SELECTED_LAYOUT_ID, romConfig.layoutId?.toString())
                    }
                    layoutSelectorLauncher.launch(intent)
                },
            )
            fun dualScreenPresetLabel(preset: com.storm3ds.emulator.domain.model.DualScreenPreset?): String {
                return when (preset) {
                    null -> useGlobal
                    com.storm3ds.emulator.domain.model.DualScreenPreset.OFF -> context.getString(R.string.dual_screen_preset_off)
                    com.storm3ds.emulator.domain.model.DualScreenPreset.INTERNAL_TOP_EXTERNAL_BOTTOM -> context.getString(R.string.dual_screen_preset_internal_top_external_bottom)
                    com.storm3ds.emulator.domain.model.DualScreenPreset.INTERNAL_BOTTOM_EXTERNAL_TOP -> context.getString(R.string.dual_screen_preset_internal_bottom_external_top)
                }
            }
            ConfigRow(
                title = stringResource(R.string.label_rom_config_dual_screen_preset),
                value = dualScreenPresetLabel(romConfig.dualScreenPreset),
                showDivider = true,
                onClick = {
                    dualScreenPresetDialogState.show(
                        title = context.getString(R.string.label_rom_config_dual_screen_preset),
                        items = listOf(null) + com.storm3ds.emulator.domain.model.DualScreenPreset.entries,
                        labelOf = { dualScreenPresetLabel(it) },
                        selected = romConfig.dualScreenPreset,
                        onSelect = { onConfigUpdate(RomConfigUpdateEvent.DualScreenPresetUpdate(it)) },
                    )
                },
            )
        }
        }

        if (showScreens) {
        ConfigSection(title = stringResource(R.string.label_rom_config_translator_section)) {
            fun translatorLangLabel(langCode: String?): String {
                if (langCode.isNullOrBlank()) return useGlobal
                val idx = translatorLangValues.indexOf(langCode)
                return if (idx in translatorLangEntries.indices) translatorLangEntries[idx] else langCode
            }
            ConfigRow(
                title = stringResource(R.string.label_rom_config_translator_source_lang),
                value = translatorLangLabel(romConfig.translatorSourceLang),
                onClick = {
                    translatorSourceLangDialogState.show(
                        title = context.getString(R.string.label_rom_config_translator_source_lang),
                        items = listOf(null) + translatorLangValues.toList(),
                        labelOf = { translatorLangLabel(it) },
                        selected = romConfig.translatorSourceLang,
                        onSelect = { onConfigUpdate(RomConfigUpdateEvent.TranslatorSourceLangUpdate(it)) },
                    )
                },
            )
        }
        }

        if (selectedFilter == RomConfigCategory.ALL || selectedFilter == RomConfigCategory.SYSTEM_CPU || activeOverridesCount > 0) {
        ConfigSection(title = stringResource(R.string.reset_rom_config_section)) {
            ConfigRow(
                title = stringResource(R.string.reset_rom_config_title),
                value = stringResource(R.string.action_reset),
                onClick = { showResetConfirmDialog = true },
            )
        }
        }

        Spacer(Modifier.height(contentPadding.calculateBottomPadding() + 96.dp))
    }

    if (showResetConfirmDialog) {
        androidx.compose.material.AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            backgroundColor = com.storm3ds.emulator.ui.theme.watermelon.surface,
            title = {
                Text(
                    text = stringResource(R.string.reset_rom_config_title),
                    color = com.storm3ds.emulator.ui.theme.watermelon.text,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.reset_rom_config_confirm_message),
                    color = com.storm3ds.emulator.ui.theme.watermelon.text2,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                androidx.compose.material.TextButton(
                    onClick = {
                        showResetConfirmDialog = false
                        onConfigUpdate(RomConfigUpdateEvent.ResetToDefaults(preserveCustomName = true))
                    }
                ) {
                    Text(
                        text = stringResource(R.string.action_reset),
                        color = com.storm3ds.emulator.ui.theme.watermelon.red,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                androidx.compose.material.TextButton(
                    onClick = { showResetConfirmDialog = false }
                ) {
                    Text(
                        text = stringResource(android.R.string.cancel),
                        color = com.storm3ds.emulator.ui.theme.watermelon.text3,
                    )
                }
            }
        )
    }

    TextInputDialog(
        title = stringResource(R.string.label_rom_config_custom_name),
        dialogState = renameDialogState,
        textValidator = { true },
        onDelete = { onConfigUpdate(RomConfigUpdateEvent.CustomNameUpdate(null)) },
    )
    SingleChoiceDialog(runtimeConsoleDialogState)
    SingleChoiceDialog(micDialogState)
    SingleChoiceDialog(inputModeDialogState)
    SingleChoiceDialog(gbaSlotDialogState)
    SingleChoiceDialog(videoRendererDialogState)
    SingleChoiceDialog(threadedRenderingDialogState)
    SingleChoiceDialog(internalResolutionDialogState)
    SingleChoiceDialog(videoFilteringDialogState)
    SingleChoiceDialog(retroAchievementsDialogState)
    SingleChoiceDialog(dualScreenPresetDialogState)
    SingleChoiceDialog(translatorSourceLangDialogState)
    SingleChoiceDialog(arm9OverclockDialogState)
    SingleChoiceDialog(fastForwardSpeedDialogState)
    SingleChoiceDialog(customVulkanDriverDialogState)
    SingleChoiceDialog(isNew3dsDialogState)
    SingleChoiceDialog(cpuClockDialogState)
    SingleChoiceDialog(render3dModeDialogState)
    SingleChoiceDialog(render3dFactorDialogState)
    SingleChoiceDialog(limitFramesAheadDialogState)
    SingleChoiceDialog(skipDupFramesDialogState)
    SingleChoiceDialog(disableRightEyeDialogState)
    SingleChoiceDialog(textureFilterDialogState)
    SingleChoiceDialog(simulateGpuTimingsDialogState)
    SingleChoiceDialog(simulateHeadphonesDialogState)
    SingleChoiceDialog(realtimeAudioDialogState)
    TextInputDialog(
        title = stringResource(R.string.video_retroarch_shader_preset_title),
        dialogState = retroArchPresetPathDialogState,
        textValidator = { true },
        onDelete = { onConfigUpdate(RomConfigUpdateEvent.RetroArchShaderPresetPathUpdate(null)) },
    )
    TextInputDialog(
        title = stringResource(R.string.video_retroarch_shader_parameters_title),
        dialogState = retroArchParametersDialogState,
        textValidator = { true },
        onDelete = { onConfigUpdate(RomConfigUpdateEvent.RetroArchShaderParametersUpdate(null)) },
    )
}

private fun retroAchievementsModeLabel(context: Context, value: Boolean?, globalEnabled: Boolean): String {
    return when (value) {
        null -> context.getString(
            if (globalEnabled) {
                R.string.retro_achievements_global_enabled
            } else {
                R.string.retro_achievements_global_disabled
            }
        )
        true -> context.getString(
            if (globalEnabled) {
                R.string.retro_achievements_enabled
            } else {
                R.string.retro_achievements_enabled_global_disabled
            }
        )
        false -> context.getString(R.string.retro_achievements_disabled)
    }
}

@MelonPreviewSet
@Composable
private fun PreviewRomConfigUi() {
    MelonTheme {
        RomConfigUi(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(0.dp),
            rom = Rom(
                name = "Super Mario 3D Land",
                developerName = "Nintendo",
                fileName = "mario_3d_land.3ds",
                uri = Uri.EMPTY,
                parentTreeUri = Uri.EMPTY,
                config = RomConfig(),
                lastPlayed = Date(),
                isDsiWareTitle = false,
                retroAchievementsHash = "",
            ),
            romConfigUiState = RomConfigUiState.Ready(
                RomConfigUiModel(
                    layoutName = "Default",
                    gbaSlotConfig = RomGbaSlotConfigUiModel(type = RomGbaSlotConfigUiModel.Type.GbaRom)
                ),
            ),
            onConfigUpdate = { },
            onCustomInputConfigEdited = { },
        )
    }
}
