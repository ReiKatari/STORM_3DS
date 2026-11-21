package com.storm3ds.emulator.ui.settings

import android.app.Dialog
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.storm3ds.emulator.NativeCoreLoader
import com.storm3ds.emulator.nativebridge.NativeLibrary
import com.storm3ds.emulator.nativebridge.features.settings.SettingKeys
import com.storm3ds.emulator.ui.settings.dialogs.SettingsDialogScaffold
import com.storm3ds.emulator.ui.settings.dialogs.showSettingsComposeDialog
import com.storm3ds.emulator.ui.theme.LocalWatermelonColors
import com.storm3ds.emulator.ui.theme.SpaceGrotesk

fun showSystem3dsSetupDialog(context: Context): Dialog {
    NativeCoreLoader.load()
    return showSettingsComposeDialog(context) { dismiss ->
        System3dsSetupContent(context = context, onDismiss = dismiss)
    }
}

@Composable
private fun System3dsSetupContent(context: Context, onDismiss: () -> Unit) {
    val colors = LocalWatermelonColors.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val keyMask = remember { runCatching { NativeLibrary.getAesKeyStatusMask() }.getOrDefault(0) }

    var fmvHack by remember { mutableStateOf(prefs.getBoolean("3ds_fmv_hack", true)) }
    var skipSlowDraw by remember { mutableStateOf(prefs.getBoolean("3ds_skip_slow_draw", false)) }
    var skipTexCopy by remember { mutableStateOf(prefs.getBoolean("3ds_skip_tex_copy", false)) }
    var accurateMul by remember { mutableStateOf(prefs.getBoolean("3ds_accurate_mul", false)) }
    var audioStretch by remember { mutableStateOf(prefs.getBoolean("3ds_audio_stretch", true)) }
    var spirvOpt by remember { mutableStateOf(prefs.getBoolean("3ds_spirv_opt", true)) }
    var disableRightEye by remember { mutableStateOf(prefs.getBoolean("3ds_disable_right_eye", false)) }
    var render3dMode by remember { mutableStateOf(prefs.getInt("3ds_render_mode", 0)) }
    var depth3d by remember { mutableStateOf(prefs.getInt("3ds_depth_factor", 0)) }
    var pretendoEnabled by remember { mutableStateOf(prefs.getBoolean("3ds_pretendo_enabled", false)) }
    var articIp by remember { mutableStateOf(prefs.getString("3ds_artic_ip", "192.168.1.100") ?: "192.168.1.100") }
    var logFilter by remember { mutableStateOf(prefs.getString("3ds_log_filter", "*:Info") ?: "*:Info") }
    var logRegex by remember { mutableStateOf(prefs.getString("3ds_log_regex", "") ?: "") }

    val modes3d = listOf(
        "Off", "Side-by-Side", "Anaglyph (Dubois)", "Interlaced",
        "Reverse Interlaced", "Cardboard VR", "SBS Full", "Gyro Parallax"
    )

    SettingsDialogScaffold(
        title = "STORM 3DS System & Hardware Setup",
        subtitle = "Keys, MMJ Speedhacks, SPIR-V, 3D Stereoscopy, Artic Base, Pretendo & Logs",
        icon = Icons.Filled.Tune,
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 540.dp)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Decryption Keys (STORM 3DS/sysdata)", fontWeight = FontWeight.Bold, color = colors.green, fontFamily = SpaceGrotesk)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyBadge("boot9.bin", (keyMask and 1) != 0, Modifier.weight(1f))
                KeyBadge("aes_keys", (keyMask and 4) != 0 || (keyMask and 2) != 0, Modifier.weight(1f))
                KeyBadge("seeddb", (keyMask and 8) != 0, Modifier.weight(1f))
            }

            Divider(color = colors.line)
            Text("Citra MMJ Speedhacks & Borked3DS SPIR-V", fontWeight = FontWeight.Bold, color = colors.green, fontFamily = SpaceGrotesk)
            SetupToggleRow("FMV Core Downcount Hack", fmvHack) { fmvHack = it }
            SetupToggleRow("Skip Slow Draw", skipSlowDraw) { skipSlowDraw = it }
            SetupToggleRow("Skip CPU Texture Copy", skipTexCopy) { skipTexCopy = it }
            SetupToggleRow("Accurate Shader Multiplication", accurateMul) { accurateMul = it }
            SetupToggleRow("Audio Time Stretching", audioStretch) { audioStretch = it }
            SetupToggleRow("SPIR-V Optimizer (SPIRV-Tools)", spirvOpt) { spirvOpt = it }

            Divider(color = colors.line)
            Text("3D Stereoscopic Engine (${modes3d.getOrElse(render3dMode) { "Off" }} - ${depth3d}%)", fontWeight = FontWeight.Bold, color = colors.green, fontFamily = SpaceGrotesk)
            Slider(value = depth3d.toFloat(), onValueChange = { depth3d = it.toInt() }, valueRange = 0f..100f)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { render3dMode = (render3dMode + 1) % modes3d.size }, modifier = Modifier.weight(1f)) {
                    Text("Mode: ${modes3d[render3dMode]}", maxLines = 1, fontSize = 11.sp)
                }
            }
            SetupToggleRow("Disable Right Eye Render (+Performance)", disableRightEye) { disableRightEye = it }

            Divider(color = colors.line)
            Text("Network: Pretendo / Nimbus & Artic Base", fontWeight = FontWeight.Bold, color = colors.green, fontFamily = SpaceGrotesk)
            SetupToggleRow("Use Pretendo / Nimbus Network Endpoint", pretendoEnabled) { pretendoEnabled = it }
            OutlinedTextField(
                value = articIp,
                onValueChange = { articIp = it },
                label = { Text("Artic Base Server IP") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Divider(color = colors.line)
            Text("Regex Diagnostic Log Filter", fontWeight = FontWeight.Bold, color = colors.green, fontFamily = SpaceGrotesk)
            OutlinedTextField(
                value = logFilter,
                onValueChange = { logFilter = it },
                label = { Text("Log Filter (*:Info, *:Debug)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = logRegex,
                onValueChange = { logRegex = it },
                label = { Text("Regex Pattern") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
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
                        .putBoolean("3ds_pretendo_enabled", pretendoEnabled)
                        .putString("3ds_artic_ip", articIp)
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
                        NativeLibrary.addDefaultSetting(SettingKeys.last_artic_base_addr(), articIp)
                    }
                    Toast.makeText(context, "STORM 3DS settings applied", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
            ) {
                Text("Save & Apply 3DS Core Configuration")
            }
        }
    }
}

@Composable
private fun KeyBadge(label: String, present: Boolean, modifier: Modifier = Modifier) {
    val bg = if (present) Color(0xFF123B26) else Color(0xFF3A1D22)
    val border = if (present) Color(0xFF22C55E) else Color(0xFFEF4444)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (present) "$label: OK" else "$label: --",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SetupToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
