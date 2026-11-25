package com.storm3ds.emulator.ui.romlist.composables

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.storm3ds.emulator.R
import com.storm3ds.emulator.domain.model.rom.Rom
import com.storm3ds.emulator.ui.common.WatermelonRowSeparator
import com.storm3ds.emulator.ui.theme.SpaceGrotesk
import com.storm3ds.emulator.ui.theme.WatermelonMono
import com.storm3ds.emulator.ui.theme.watermelon
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun RomTechnicalInfoDialog(
    rom: Rom,
    onDismiss: () -> Unit,
) {
    val colors = watermelon
    val context = LocalContext.current
    var fileSizeBytes by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(rom.uri) {
        val size = withContext(Dispatchers.IO) {
            runCatching {
                if (rom.uri.scheme == "file") {
                    rom.uri.path?.let { File(it).length() } ?: 0L
                } else {
                    val queriedSize = runCatching {
                        context.contentResolver.query(
                            rom.uri,
                            arrayOf(OpenableColumns.SIZE),
                            null,
                            null,
                            null,
                        )?.use { cursor ->
                            val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (idx >= 0 && cursor.moveToFirst() && !cursor.isNull(idx)) cursor.getLong(idx) else -1L
                        } ?: -1L
                    }.getOrDefault(-1L)
                    when {
                        queriedSize > 0L -> queriedSize
                        else -> {
                            val afdSize = runCatching {
                                context.contentResolver.openAssetFileDescriptor(rom.uri, "r")?.use { it.length } ?: -1L
                            }.getOrDefault(-1L)
                            if (afdSize > 0L) afdSize
                            else {
                                val docSize = runCatching {
                                    DocumentFile.fromSingleUri(context, rom.uri)?.length() ?: -1L
                                }.getOrDefault(-1L)
                                if (docSize > 0L) docSize
                                else runCatching {
                                    context.contentResolver.openFileDescriptor(rom.uri, "r")?.use { it.statSize } ?: 0L
                                }.getOrDefault(0L)
                            }
                        }
                    }
                }
            }.getOrDefault(0L)
        }
        fileSizeBytes = size
    }

    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    val lastPlayedStr = remember(rom.lastPlayed) {
        rom.lastPlayed?.let { dateFormat.format(it) } ?: context.getString(R.string.rom_info_never_played)
    }

    val extLower = remember(rom.fileName) {
        rom.fileName.substringAfterLast('.', "").lowercase()
    }
    val platformStr = "Nintendo 3DS"

    val isRu = remember { java.util.Locale.getDefault().language.startsWith("ru") }
    val playTimeStr = remember(rom.totalPlayTime) {
        val hours = rom.totalPlayTime.inWholeHours
        val minutes = (rom.totalPlayTime.inWholeMinutes % 60)
        val seconds = (rom.totalPlayTime.inWholeSeconds % 60)
        if (hours > 0) {
            context.getString(R.string.ui_tech_hms_hm_fmt, hours, minutes)
        } else if (minutes > 0) {
            context.getString(R.string.ui_tech_hms_ms_fmt, minutes, seconds)
        } else {
            context.getString(R.string.ui_tech_hms_s_fmt, seconds)
        }
    }

    val unitCodeStr = remember(extLower) {
        when (extLower) {
            "3ds", "cci" -> "NCSD (.${extLower.uppercase()})"
            "cxi", "app" -> "NCCH Exe (.${extLower.uppercase()})"
            "cia" -> "CTR Archive (.CIA)"
            "3dsx", "elf", "axf" -> "3DS Homebrew (.${extLower.uppercase()})"
            "z3ds", "zcci", "zcxi", "zcia", "z3dsx" -> "Z3DS Compressed (.${extLower.uppercase()})"
            "bcci", "bcxi", "bcia" -> "3DS Bundle (.${extLower.uppercase()})"
            "artic" -> "Artic Base Stream (.ARTIC)"
            else -> "CTR (.${extLower.uppercase().ifEmpty { "3DS" }})"
        }
    }

    val formattedSize = remember(fileSizeBytes) {
        fileSizeBytes?.let { bytes ->
            if (bytes <= 0) "-"
            else {
                val mb = bytes / (1024.0 * 1024.0)
                if (mb >= 1.0) {
                    String.format(Locale.US, "%.2f MB (%d B)", mb, bytes)
                } else {
                    val kb = bytes / 1024.0
                    String.format(Locale.US, "%.1f KB (%d B)", kb, bytes)
                }
            }
        } ?: "..."
    }

    val copyToClipboard = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val textBuilder = StringBuilder()
        textBuilder.appendLine(context.getString(R.string.ui_tech_clipboard_header))
        textBuilder.appendLine("========================================")
        textBuilder.appendLine("${context.getString(R.string.rom_info_title_label)}: ${romDisplayName(rom)}")
        if (rom.name != romDisplayName(rom)) {
            textBuilder.appendLine(context.getString(R.string.ui_tech_internal_title_fmt, rom.name))
        }
        textBuilder.appendLine("${context.getString(R.string.rom_info_file_name_label)}: ${rom.fileName}")
        textBuilder.appendLine("URI: ${rom.uri}")
        textBuilder.appendLine("${context.getString(R.string.rom_info_file_size_label)}: $formattedSize")
        textBuilder.appendLine("${context.getString(R.string.rom_info_platform_label)}: $platformStr")
        textBuilder.appendLine("UnitCode: $unitCodeStr")
        if (rom.gameCode.isNotBlank()) {
            textBuilder.appendLine("${context.getString(R.string.rom_info_game_code_label)}: ${rom.gameCode}")
        }
        textBuilder.appendLine("Format: ${extLower.uppercase().ifBlank { "3DS" }}")
        textBuilder.appendLine("${context.getString(R.string.rom_info_play_time_label)}: $playTimeStr")
        textBuilder.appendLine("${context.getString(R.string.rom_info_last_played_label)}: $lastPlayedStr")
        textBuilder.appendLine("========================================")

        val clip = ClipData.newPlainText("ROM Technical Info", textBuilder.toString())
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, context.getString(R.string.rom_technical_info_copied), Toast.LENGTH_SHORT).show()
    }

    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material.LocalElevationOverlay provides null) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = colors.surface,
                elevation = 22.dp,
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(16.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(colors.green),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.rom_technical_info_title),
                            color = colors.text,
                            fontFamily = SpaceGrotesk,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // 2-Column Hardware Badges Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            HardwareBadge(
                                label = stringResource(R.string.ui_tech_title_id_caps),
                                value = rom.gameCode.ifBlank { "3DS-HOMEBREW" },
                                isAccent = true,
                            )
                            HardwareBadge(
                                label = stringResource(R.string.ui_tech_rom_size_caps),
                                value = formattedSize.split(" (").first(),
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            HardwareBadge(
                                label = stringResource(R.string.ui_tech_unit_code_caps),
                                value = unitCodeStr,
                            )
                            HardwareBadge(
                                label = stringResource(R.string.ui_tech_format_caps),
                                value = extLower.uppercase().ifBlank { "3DS" },
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    WatermelonRowSeparator()
                    Spacer(Modifier.height(10.dp))

                    TechInfoRow(stringResource(R.string.rom_info_title_label), romDisplayName(rom))
                    if (rom.name != romDisplayName(rom)) {
                        TechInfoRow("Internal Name", rom.name)
                    }
                    TechInfoRow(stringResource(R.string.rom_info_file_name_label), rom.fileName)
                    TechInfoRow(stringResource(R.string.rom_info_platform_label), platformStr)
                    TechInfoRow(stringResource(R.string.rom_info_play_time_label), playTimeStr)
                    TechInfoRow(stringResource(R.string.rom_info_last_played_label), lastPlayedStr)
                    TechInfoRow("URI", rom.uri.toString(), isMono = true)

                    Spacer(Modifier.height(16.dp))
                    WatermelonRowSeparator()
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = copyToClipboard,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                backgroundColor = colors.surface2,
                                contentColor = colors.text,
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.green.copy(alpha = 0.5f)),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = null,
                                tint = colors.green,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                            Text(
                                text = stringResource(R.string.rom_technical_info_copy),
                                fontSize = 12.5.sp,
                                fontFamily = SpaceGrotesk,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.text,
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        TextButton(onClick = onDismiss) {
                            Text(
                                text = stringResource(android.R.string.ok),
                                color = colors.text2,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HardwareBadge(
    label: String,
    value: String,
    isAccent: Boolean = false,
) {
    val colors = watermelon
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isAccent) colors.green.copy(alpha = 0.12f) else colors.surface2)
            .border(
                width = 1.dp,
                color = if (isAccent) colors.green.copy(alpha = 0.5f) else colors.line,
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            color = if (isAccent) colors.green else colors.text3,
            fontFamily = WatermelonMono,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = colors.text,
            fontFamily = WatermelonMono,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TechInfoRow(label: String, value: String, isMono: Boolean = false) {
    val colors = watermelon
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            color = colors.text3,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            color = colors.text,
            fontFamily = if (isMono) WatermelonMono else null,
            fontSize = 12.5.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}
