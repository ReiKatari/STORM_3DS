package com.storm3ds.emulator.ui.settings

import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.storm3ds.emulator.NativeCoreLoader
import com.storm3ds.emulator.R
import com.storm3ds.emulator.nativebridge.NativeBridgeHost
import com.storm3ds.emulator.nativebridge.NativeLibrary
import com.storm3ds.emulator.nativebridge.features.settings.SettingKeys
import com.storm3ds.emulator.ui.common.WatermelonSwitch
import com.storm3ds.emulator.ui.settings.dialogs.SettingsDialogScaffold
import com.storm3ds.emulator.ui.settings.dialogs.showSettingsComposeDialog
import com.storm3ds.emulator.ui.theme.LocalWatermelonColors
import com.storm3ds.emulator.ui.theme.SpaceGrotesk
import com.storm3ds.emulator.ui.theme.WatermelonMono
import java.io.File

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}

fun showSystem3dsSetupDialog(context: Context): Dialog {
    NativeCoreLoader.load()
    return showSettingsComposeDialog(context) { dismiss ->
        System3dsSetupContent(context = context, onDismiss = dismiss)
    }
}

private fun resolveDisplayName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) return c.getString(idx).orEmpty()
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/').orEmpty()
}

@Composable
private fun System3dsSetupContent(context: Context, onDismiss: () -> Unit) {
    val colors = LocalWatermelonColors.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var keyMask by remember { mutableIntStateOf(runCatching { NativeLibrary.getAesKeyStatusMask() }.getOrDefault(0)) }
    val sysTitlesStatus = remember(keyMask) {
        runCatching { NativeLibrary.areSystemTitlesInstalled() }.getOrElse { booleanArrayOf(false, false) }
    }

    var fmvHack by remember { mutableStateOf(prefs.getBoolean("3ds_fmv_hack", true)) }
    var skipSlowDraw by remember { mutableStateOf(prefs.getBoolean("3ds_skip_slow_draw", false)) }
    var skipTexCopy by remember { mutableStateOf(prefs.getBoolean("3ds_skip_tex_copy", false)) }
    var accurateMul by remember { mutableStateOf(prefs.getBoolean("3ds_accurate_mul", false)) }
    var audioStretch by remember { mutableStateOf(prefs.getBoolean("3ds_audio_stretch", false)) }
    var spirvOpt by remember { mutableStateOf(prefs.getBoolean("3ds_spirv_opt", false)) }
    var disableRightEye by remember { mutableStateOf(prefs.getBoolean("3ds_disable_right_eye", false)) }
    val initialVideoMode = when (prefs.getString("video_vulkan_3d_depth_mode", "off")?.lowercase()) {
        "sbs_xr" -> 1
        "sbs_full" -> 2
        "anaglyph" -> 3
        "interlaced" -> 4
        "reverse_interlaced" -> 5
        "cardboard_vr" -> 6
        "gyro_parallax" -> 7
        else -> 0
    }
    val stereoUiOrder = remember { listOf(0, 7, 3, 1, 2, 4, 5, 6) }
    var render3dMode by remember {
        mutableIntStateOf(prefs.getInt("3ds_render_mode", initialVideoMode).coerceIn(0, 7))
    }
    val initialVideoDepth = prefs.getString("video_vulkan_3d_depth_strength", "50")?.toIntOrNull()?.coerceIn(0, 100) ?: 50
    var depth3d by remember { mutableIntStateOf(prefs.getInt("3ds_depth_factor", initialVideoDepth).coerceIn(0, 100)) }
    var pretendoEnabled by remember { mutableStateOf(prefs.getBoolean("3ds_pretendo_enabled", false)) }
    var articIp by remember { mutableStateOf(prefs.getString("3ds_artic_ip", "192.168.1.100") ?: "192.168.1.100") }
    var articController by remember { mutableStateOf(prefs.getBoolean("3ds_artic_controller", false)) }
    var logFilter by remember { mutableStateOf(prefs.getString("3ds_log_filter", "*:Warning") ?: "*:Warning") }
    var logRegex by remember { mutableStateOf(prefs.getString("3ds_log_regex", "") ?: "") }

    val modes3d = stringArrayResource(R.array.video_vulkan_3d_depth_mode_options)

    fun launchKeyPicker() {
        val activity = context.findComponentActivity()
        if (activity == null) {
            Toast.makeText(context, context.getString(R.string.setup_3ds_place_keys_toast), Toast.LENGTH_LONG).show()
            return
        }
        val key = "storm3ds_key_import_${System.currentTimeMillis()}"
        var regRef: androidx.activity.result.ActivityResultLauncher<Array<String>>? = null
        regRef = activity.activityResultRegistry.register(key, ActivityResultContracts.OpenDocument()) { uri ->
            regRef?.unregister()
            if (uri != null) {
                val rawName = resolveDisplayName(context, uri)
                val targetName = when {
                    rawName.contains("boot9", ignoreCase = true) -> "boot9.bin"
                    rawName.contains("aes_keys", ignoreCase = true) -> "aes_keys.txt"
                    rawName.contains("keys", ignoreCase = true) -> "keys.txt"
                    rawName.contains("seeddb", ignoreCase = true) -> "seeddb.bin"
                    rawName.isNotBlank() -> rawName
                    else -> "aes_keys.txt"
                }
                val sysDir = File(NativeBridgeHost.resolveUserDirectoryPath(), "sysdata").apply { mkdirs() }
                val dest = File(sysDir, targetName)
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                    keyMask = NativeLibrary.getAesKeyStatusMask()
                    Toast.makeText(context, context.getString(R.string.setup_3ds_imported_toast, targetName), Toast.LENGTH_SHORT).show()
                }.onFailure { ex ->
                    Toast.makeText(context, context.getString(R.string.setup_3ds_import_failed_toast, ex.message ?: ""), Toast.LENGTH_SHORT).show()
                }
            }
        }
        regRef.launch(arrayOf("*/*"))
    }

    SettingsDialogScaffold(
        title = stringResource(R.string.setup_3ds_dialog_title),
        subtitle = stringResource(R.string.setup_3ds_dialog_subtitle),
        icon = Icons.Filled.Tune,
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            // 1. Decryption Keys
            SetupSectionHeader(stringResource(R.string.setup_3ds_keys_header))
            SetupSectionCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        KeyBadge("boot9.bin", (keyMask and 1) != 0, Modifier.weight(1f))
                        KeyBadge("aes_keys", (keyMask and 4) != 0 || (keyMask and 2) != 0, Modifier.weight(1f))
                        KeyBadge("seeddb", (keyMask and 8) != 0, Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        KeyBadge("Old 3DS NAND", sysTitlesStatus.getOrElse(0) { false }, Modifier.weight(1f))
                        KeyBadge("New 3DS NAND", sysTitlesStatus.getOrElse(1) { false }, Modifier.weight(1f))
                    }
                    SetupActionButton(
                        text = stringResource(R.string.setup_3ds_import_key_btn),
                        icon = Icons.Filled.FileOpen,
                        onClick = { launchKeyPicker() },
                    )
                }
            }

            // 2. Speedhacks & SPIR-V Optimizer
            SetupSectionHeader(stringResource(R.string.setup_3ds_speedhacks_header))
            SetupSectionCard {
                SetupToggleRow(
                    title = stringResource(R.string.setup_3ds_fmv_hack),
                    summary = stringResource(R.string.setup_3ds_fmv_hack_summary),
                    checked = fmvHack,
                    onCheckedChange = { fmvHack = it },
                )
                SetupCardDivider()
                SetupToggleRow(
                    title = stringResource(R.string.setup_3ds_skip_slow_draw),
                    summary = stringResource(R.string.setup_3ds_skip_slow_draw_summary),
                    checked = skipSlowDraw,
                    onCheckedChange = { skipSlowDraw = it },
                )
                SetupCardDivider()
                SetupToggleRow(
                    title = stringResource(R.string.setup_3ds_skip_tex_copy),
                    summary = stringResource(R.string.setup_3ds_skip_tex_copy_summary),
                    checked = skipTexCopy,
                    onCheckedChange = { skipTexCopy = it },
                )
                SetupCardDivider()
                SetupToggleRow(
                    title = stringResource(R.string.setup_3ds_accurate_mul),
                    summary = stringResource(R.string.setup_3ds_accurate_mul_summary),
                    checked = accurateMul,
                    onCheckedChange = { accurateMul = it },
                )
                SetupCardDivider()
                SetupToggleRow(
                    title = stringResource(R.string.setup_3ds_audio_stretch),
                    summary = stringResource(R.string.setup_3ds_audio_stretch_summary),
                    checked = audioStretch,
                    onCheckedChange = { audioStretch = it },
                )
                SetupCardDivider()
                SetupToggleRow(
                    title = stringResource(R.string.setup_3ds_spirv_opt),
                    summary = stringResource(R.string.setup_3ds_spirv_opt_summary),
                    checked = spirvOpt,
                    onCheckedChange = { spirvOpt = it },
                )
            }

            // 3. Stereoscopic 3D Engine
            val currentUiIdx = stereoUiOrder.indexOf(render3dMode).let { if (it < 0) 0 else it }
            val currentModeLabel = modes3d.getOrElse(currentUiIdx) { stringResource(R.string.video_vulkan_3d_depth_mode_off) }
            SetupSectionHeader(stringResource(R.string.setup_3ds_3d_section_header))
            SetupSectionCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 58.dp)
                        .clickable {
                            val nextUiIdx = (currentUiIdx + 1) % stereoUiOrder.size
                            render3dMode = stereoUiOrder[nextUiIdx]
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Layers,
                        contentDescription = null,
                        tint = colors.green,
                        modifier = Modifier.size(18.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.setup_3ds_3d_mode_label),
                            color = colors.text,
                            fontFamily = SpaceGrotesk,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = currentModeLabel,
                            color = colors.green,
                            fontFamily = WatermelonMono,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SetupIconStepperButton(icon = Icons.Filled.ChevronLeft) {
                            val prevUiIdx = (currentUiIdx - 1 + stereoUiOrder.size) % stereoUiOrder.size
                            render3dMode = stereoUiOrder[prevUiIdx]
                        }
                        SetupIconStepperButton(icon = Icons.Filled.ChevronRight) {
                            val nextUiIdx = (currentUiIdx + 1) % stereoUiOrder.size
                            render3dMode = stereoUiOrder[nextUiIdx]
                        }
                    }
                }
                SetupCardDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.setup_3ds_3d_depth_label),
                            color = colors.text,
                            fontFamily = SpaceGrotesk,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.greenDim)
                                .border(1.dp, colors.green.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = "$depth3d%",
                                color = colors.green,
                                fontFamily = WatermelonMono,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Slider(
                        value = depth3d.toFloat(),
                        onValueChange = { depth3d = it.toInt().coerceIn(0, 100) },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.green,
                            activeTrackColor = colors.green,
                            inactiveTrackColor = colors.line,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SetupCardDivider()
                SetupToggleRow(
                    title = stringResource(R.string.setup_3ds_disable_right_eye),
                    summary = stringResource(R.string.setup_3ds_disable_right_eye_summary),
                    checked = disableRightEye,
                    onCheckedChange = { disableRightEye = it },
                )
            }

            // 4. Network: Pretendo / Nimbus & Artic Base
            SetupSectionHeader(stringResource(R.string.setup_3ds_network_header))
            SetupSectionCard {
                SetupToggleRow(
                    title = stringResource(R.string.setup_3ds_pretendo_toggle),
                    summary = stringResource(R.string.setup_3ds_pretendo_summary),
                    checked = pretendoEnabled,
                    onCheckedChange = { pretendoEnabled = it },
                )
                SetupCardDivider()
                SetupToggleRow(
                    title = stringResource(R.string.setup_3ds_artic_controller),
                    summary = stringResource(R.string.setup_3ds_artic_controller_summary),
                    checked = articController,
                    onCheckedChange = { articController = it },
                )
                SetupCardDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SetupTextField(
                        label = stringResource(R.string.setup_3ds_artic_ip_label),
                        value = articIp,
                        onValueChange = { articIp = it },
                        icon = Icons.Filled.Wifi,
                    )
                    SetupActionButton(
                        text = stringResource(R.string.setup_3ds_generate_artic_btn),
                        icon = Icons.Filled.Save,
                        onClick = {
                            val trimmed = articIp.trim()
                            if (trimmed.isNotEmpty()) {
                                val userDir = File(NativeBridgeHost.resolveUserDirectoryPath())
                                val outFile = File(userDir, "ArticBase.artic")
                                runCatching {
                                    outFile.writeText("articbase://$trimmed")
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.setup_3ds_artic_created_toast, outFile.absolutePath),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        },
                    )
                }
            }

            // 5. Diagnostics & Regex Logs
            SetupSectionHeader(stringResource(R.string.setup_3ds_logs_header))
            SetupSectionCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SetupTextField(
                        label = stringResource(R.string.setup_3ds_log_filter_label),
                        value = logFilter,
                        onValueChange = { logFilter = it },
                        icon = Icons.Filled.Code,
                    )
                    SetupTextField(
                        label = stringResource(R.string.setup_3ds_log_regex_label),
                        value = logRegex,
                        onValueChange = { logRegex = it },
                        icon = Icons.Filled.Code,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // Sticky Footer CTA matching SettingsComposeDialogs.kt
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.line),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.green)
                    .clickable {
                        prefs.edit()
                            .putBoolean("3ds_fmv_hack", fmvHack)
                            .putBoolean("3ds_skip_slow_draw", skipSlowDraw)
                            .putBoolean("3ds_skip_tex_copy", skipTexCopy)
                            .putBoolean("3ds_accurate_mul", accurateMul)
                            .putBoolean("3ds_audio_stretch", audioStretch)
                            .putBoolean("3ds_spirv_opt", spirvOpt)
                            .putBoolean("3ds_disable_right_eye", disableRightEye)
                            .putInt("3ds_render_mode", render3dMode)
                            .putInt("3ds_depth_factor", depth3d)
                            .putString(
                                "video_vulkan_3d_depth_mode",
                                when (render3dMode) {
                                    1 -> "sbs_xr"
                                    2 -> "sbs_full"
                                    3 -> "anaglyph"
                                    4 -> "interlaced"
                                    5 -> "reverse_interlaced"
                                    6 -> "cardboard_vr"
                                    7 -> "gyro_parallax"
                                    else -> "off"
                                },
                            )
                            .putString("video_vulkan_3d_depth_strength", depth3d.toString())
                            .putBoolean("3ds_pretendo_enabled", pretendoEnabled)
                            .putString("3ds_artic_ip", articIp.trim())
                            .putBoolean("3ds_artic_controller", articController)
                            .putString("3ds_log_filter", logFilter)
                            .putString("3ds_log_regex", logRegex)
                            .apply()

                        runCatching {
                            NativeLibrary.setSpeedhacks(fmvHack, skipSlowDraw, skipTexCopy, accurateMul, audioStretch, false)
                            NativeLibrary.setSpirvOptimization(if (spirvOpt) 1 else 0, false, false)
                            NativeLibrary.setRender3dMode(render3dMode)
                            NativeLibrary.setFactor3d(depth3d)
                            NativeLibrary.setDisableRightEyeRender(disableRightEye)
                            NativeLibrary.setLogFilterAndRegex(logFilter, logRegex)
                            NativeLibrary.addDefaultSetting(SettingKeys.last_artic_base_addr(), articIp.trim())
                            NativeLibrary.addDefaultSetting(SettingKeys.use_artic_base_controller(), if (articController) "1" else "0")
                            NativeLibrary.addDefaultSetting(SettingKeys.web_api_url(), if (pretendoEnabled) "https://api.pretendo.cc" else "")
                            NativeLibrary.addDefaultSetting(SettingKeys.enable_required_online_lle_modules(), if (pretendoEnabled) "1" else "0")
                            if (NativeLibrary.isRunning()) {
                                NativeLibrary.reloadSettings()
                            }
                        }
                        Toast.makeText(context, context.getString(R.string.setup_3ds_saved_toast), Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.setup_3ds_save_apply_btn),
                    color = colors.bg,
                    fontFamily = SpaceGrotesk,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun SetupSectionHeader(title: String) {
    val colors = LocalWatermelonColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 12.dp, bottom = 7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(colors.green),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = title.uppercase(),
            color = colors.text3,
            fontFamily = WatermelonMono,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
        )
    }
}

@Composable
private fun SetupSectionCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalWatermelonColors.current
    val shape = RoundedCornerShape(15.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface2)
            .border(1.dp, colors.line, shape),
        content = content,
    )
}

@Composable
private fun SetupCardDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalWatermelonColors.current.line),
    )
}

@Composable
private fun KeyBadge(label: String, present: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalWatermelonColors.current
    val bg = if (present) colors.greenDim else colors.red.copy(alpha = 0.12f)
    val border = if (present) colors.green.copy(alpha = 0.65f) else colors.red.copy(alpha = 0.45f)
    val accent = if (present) colors.green else colors.red
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (present) Icons.Filled.CheckCircle else Icons.Filled.Error,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            color = colors.text,
            fontFamily = WatermelonMono,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SetupToggleRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalWatermelonColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.text,
                fontFamily = SpaceGrotesk,
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = summary,
                color = colors.text2,
                fontFamily = SpaceGrotesk,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
            )
        }
        WatermelonSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun SetupActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val colors = LocalWatermelonColors.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.green,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = colors.text,
            fontFamily = SpaceGrotesk,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SetupIconStepperButton(
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val colors = LocalWatermelonColors.current
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.line, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.text,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SetupTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector,
) {
    val colors = LocalWatermelonColors.current
    val shape = RoundedCornerShape(12.dp)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = label.uppercase(),
            color = colors.text3,
            fontFamily = WatermelonMono,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(start = 2.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surface)
                .border(1.dp, colors.line, shape)
                .padding(horizontal = 12.dp, vertical = 11.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.green,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(9.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    color = colors.text,
                    fontFamily = SpaceGrotesk,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
                cursorBrush = SolidColor(colors.green),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
