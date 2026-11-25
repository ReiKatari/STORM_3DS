package com.storm3ds.emulator.ui.romdetails.ui

import com.storm3ds.emulator.R
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.storm3ds.emulator.common.cheats.BundledMmjCheatsLoader
import com.storm3ds.emulator.domain.model.rom.Rom
import com.storm3ds.emulator.impl.profiles.GameProfilesRepository
import com.storm3ds.emulator.nativebridge.NativeBridgeHost
import com.storm3ds.emulator.nativebridge.NativeLibrary
import com.storm3ds.emulator.nativebridge.utils.CiaInstallWorker
import com.storm3ds.emulator.ui.settings.showSystem3dsSetupDialog
import com.storm3ds.emulator.ui.theme.watermelon
import java.io.File

@Composable
fun Rom3dsToolsCard(
    rom: Rom,
    context: android.content.Context,
) {
    val colors = watermelon
    val titleIdHex = rom.gameCode.takeIf { it.isNotBlank() }
        ?: rom.installedDsiWareTitleId?.let { String.format("%016X", it) }
        ?: "0000000000000000"
    val profile = remember(titleIdHex) { GameProfilesRepository.findForTitleId(context, titleIdHex) }
    val mmjCheats = remember(titleIdHex) { BundledMmjCheatsLoader.loadForTitleId(context, titleIdHex) }

    val ciaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val path = NativeLibrary.getNativePath(uri).ifBlank { uri.toString() }
            CiaInstallWorker.enqueueInstall(context, arrayOf(path))
            Toast.makeText(context, context.getString(R.string.rom_tools_3ds_cia_queued), Toast.LENGTH_SHORT).show()
        }
    }

    val amiiboLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val amiiboDir = File(NativeBridgeHost.resolveUserDirectoryPath(), "amiibo").apply { mkdirs() }
            val dest = File(amiiboDir, "${titleIdHex}_amiibo.bin")
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                val loadedLive = if (NativeLibrary.isRunning()) NativeLibrary.loadAmiibo(dest.absolutePath) else false
                val msg = if (loadedLive) {
                    context.getString(R.string.rom_tools_3ds_amiibo_loaded)
                } else {
                    context.getString(R.string.rom_tools_3ds_amiibo_staged)
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.rom_tools_3ds_title_fmt, titleIdHex), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colors.text)
        if (profile != null) {
            Text(
                stringResource(R.string.rom_tools_3ds_rec_profile_fmt, profile.renderer, profile.resolutionScale, profile.notes),
                fontSize = 12.sp,
                color = colors.green,
            )
            Button(
                onClick = {
                    GameProfilesRepository.applyProfile(profile)
                    Toast.makeText(context, context.getString(R.string.rom_tools_3ds_profile_applied_fmt, profile.title), Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.rom_tools_3ds_apply_profile_btn))
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { ciaLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.rom_tools_3ds_install_cia_btn), fontSize = 11.sp)
            }
            OutlinedButton(onClick = { amiiboLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.rom_tools_3ds_load_amiibo_btn), fontSize = 11.sp)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val userRoot = NativeBridgeHost.resolveUserDirectoryPath()
                    File(userRoot, "load/mods/$titleIdHex").mkdirs()
                    File(userRoot, "load/textures/$titleIdHex").mkdirs()
                    Toast.makeText(context, context.getString(R.string.rom_tools_3ds_mods_ready_fmt, titleIdHex), Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.rom_tools_3ds_prepare_mods_btn), fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = { showSystem3dsSetupDialog(context) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.rom_tools_3ds_setup_btn), fontSize = 11.sp)
            }
        }
        val tidLong = titleIdHex.toULongOrNull(16)?.toLong() ?: 0L
        val extLower = rom.fileName.substringAfterLast('.', "").lowercase()
        val isCart = extLower in setOf("3ds", "cci", "zcci", "z3ds", "cxi", "zcxi", "bcci", "bcxi")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tidLong != 0L) {
                OutlinedButton(
                    onClick = {
                        runCatching {
                            com.storm3ds.emulator.NativeCoreLoader.load()
                            NativeLibrary.deleteVulkanShaderCache(tidLong)
                            NativeLibrary.deleteOpenGLShaderCache(tidLong)
                        }
                        Toast.makeText(context, context.getString(R.string.rom_shader_cache_cleared_fmt, titleIdHex), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.pref_3ds_delete_shader_cache), fontSize = 11.sp)
                }
            }
            if (isCart) {
                OutlinedButton(
                    onClick = {
                        val path = NativeLibrary.resolveNativeOrContentPath(rom.uri)
                        if (path.isNotBlank()) {
                            runCatching {
                                com.storm3ds.emulator.NativeCoreLoader.load()
                                NativeLibrary.setInsertedCartridge(path)
                            }
                            Toast.makeText(context, context.getString(R.string.rom_set_inserted_cart_success_fmt, rom.name), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.rom_action_set_inserted_cart), fontSize = 11.sp)
                }
            }
        }
        if (mmjCheats.isNotEmpty()) {
            Button(
                onClick = {
                    val userRoot = NativeBridgeHost.resolveUserDirectoryPath()
                    val cheatsDir = File(userRoot, "cheats").apply { mkdirs() }
                    val outFile = File(cheatsDir, "$titleIdHex.txt")
                    val content = mmjCheats.joinToString("\n\n") { entry ->
                        buildString {
                            appendLine("[${entry.name}]")
                            if (entry.notes.isNotBlank()) {
                                entry.notes.lines().forEach { appendLine("* $it") }
                            }
                            append(entry.code)
                        }
                    }
                    runCatching {
                        outFile.writeText(content + "\n")
                        Toast.makeText(context, context.getString(R.string.rom_tools_3ds_cheats_exported_fmt, mmjCheats.size, titleIdHex), Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.rom_tools_3ds_install_cheats_btn_fmt, mmjCheats.size, titleIdHex))
            }
        }
    }
}
