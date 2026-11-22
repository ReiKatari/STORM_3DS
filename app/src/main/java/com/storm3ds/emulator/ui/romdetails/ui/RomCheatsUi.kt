package com.storm3ds.emulator.ui.romdetails.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.storm3ds.emulator.R
import com.storm3ds.emulator.common.cheats.BundledMmjCheatsLoader
import com.storm3ds.emulator.common.cheats.EmbeddedActionReplayCheats
import com.storm3ds.emulator.nativebridge.NativeLibrary
import com.storm3ds.emulator.nativebridge.model.GameInfo
import com.storm3ds.emulator.di.entrypoint.CheatsEntryPoint
import com.storm3ds.emulator.domain.model.Cheat
import com.storm3ds.emulator.domain.model.CheatFolder
import com.storm3ds.emulator.domain.model.Game
import com.storm3ds.emulator.domain.model.RomInfo
import com.storm3ds.emulator.domain.model.rom.Rom
import com.storm3ds.emulator.ui.emulator.ui.JusrcheatAllGamesTab
import com.storm3ds.emulator.ui.romlist.composables.romDisplayName
import com.storm3ds.emulator.ui.theme.SpaceGrotesk
import com.storm3ds.emulator.ui.theme.WatermelonMono
import com.storm3ds.emulator.ui.theme.watermelon
import com.storm3ds.emulator.utils.RomProcessor
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

@Composable
fun RomCheatsUi(
    rom: Rom,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val colors = watermelon

    val entryPoint = remember { CheatsEntryPoint.resolve(context) }
    val cheatsRepository = remember { entryPoint.cheatsRepository() }
    val database = remember { entryPoint.database() }

    var game by remember(rom) { mutableStateOf<Game?>(null) }
    var romInfo by remember(rom) { mutableStateOf<RomInfo?>(null) }
    var cheatFolders by remember(rom) { mutableStateOf<List<CheatFolder>>(emptyList()) }
    var isLoading by remember(rom) { mutableStateOf(true) }
    var isDownloadingDb by remember { mutableStateOf(false) }
    var showJusrcheatBrowser by remember { mutableStateOf(false) }

    val expandedFolders = remember(rom) { mutableStateMapOf<Long, Boolean>() }
    var refreshJob by remember(rom) { mutableStateOf<Job?>(null) }

    fun refreshCheats() {
        refreshJob?.cancel()
        refreshJob = coroutineScope.launch(Dispatchers.IO) {
            isLoading = true
            val parsedInfo = parseRomInfoDeep(context, rom)
            romInfo = parsedInfo

            val checksum = parsedInfo?.headerChecksumString() ?: ""
            val effectiveCode = parsedInfo?.gameCode?.takeIf { it.isNotBlank() }
                ?: extractDsiCode(rom)
            val effectiveTitle = parsedInfo?.gameTitle?.takeIf { it.isNotBlank() }
                ?: romDisplayName(rom)

            if (effectiveCode.length == 16) {
                BundledMmjCheatsLoader.populate3dsCheatsIfEmpty(context, database, effectiveCode, effectiveTitle)
            } else {
                EmbeddedActionReplayCheats.populateIfEmpty(database, effectiveCode, effectiveTitle, checksum)
            }

            var foundGame = if (parsedInfo != null) {
                cheatsRepository.findGameForRom(parsedInfo)
            } else {
                null
            }

            if (foundGame == null) {
                val resolvedEntity = (if (checksum.isNotBlank()) database.gameDao().findGameByChecksum(checksum) else null)
                    ?: database.gameDao().findGameByCode(effectiveCode)
                    ?: if (effectiveCode.length >= 3) database.gameDao().findGameByPrefix(effectiveCode.take(3)) else null
                    ?: database.gameDao().findGameByTitle(effectiveTitle)

                foundGame = resolvedEntity?.let {
                    Game(it.id, it.name, it.gameCode, it.gameChecksum, emptyList())
                }
            }
            game = foundGame

            if (foundGame != null) {
                cheatsRepository.getAllGameCheats(foundGame).collect { folders ->
                    if (folders.isEmpty()) {
                        EmbeddedActionReplayCheats.populateIfEmpty(database, effectiveCode, effectiveTitle, checksum)
                    }
                    cheatFolders = folders
                    folders.forEach { f ->
                        f.id?.let { id ->
                            if (!expandedFolders.containsKey(id)) {
                                expandedFolders[id] = true
                            }
                        }
                    }
                    isLoading = false
                }
            } else {
                cheatFolders = emptyList()
                isLoading = false
            }
        }
    }

    LaunchedEffect(rom) {
        // Auto-check for cheat files in /storage/emulated/0/STORM 3DS/cheats/
        withContext(Dispatchers.IO) {
            val rootBase = File(android.os.Environment.getExternalStorageDirectory(), "STORM 3DS")
            val cheatsDir = File(rootBase, "cheats").apply { mkdirs() }
            val usrCheat = File(cheatsDir, "usrcheat.dat")
            if (usrCheat.exists() && usrCheat.length() > 0) {
                val prefs = context.getSharedPreferences("storm_cheats_auto_import", android.content.Context.MODE_PRIVATE)
                val currentSig = "${usrCheat.length()}_${usrCheat.lastModified()}"
                val lastSig = prefs.getString("usrcheat_auto_import_sig", null)
                if (currentSig != lastSig) {
                    runCatching {
                        cheatsRepository.importCheats(Uri.fromFile(usrCheat))
                        prefs.edit().putString("usrcheat_auto_import_sig", currentSig).apply()
                    }
                }
            }
        }
        refreshCheats()
    }

    val importCheatsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                val fileName = runCatching {
                    androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)?.name
                }.getOrNull() ?: "cheats.txt"
                val rootBase = File(android.os.Environment.getExternalStorageDirectory(), "STORM 3DS")
                val cheatsDir = File(rootBase, "cheats").apply { mkdirs() }
                if (fileName.endsWith(".txt", ignoreCase = true)) {
                    val textContent = runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull().orEmpty()
                    val parsed = BundledMmjCheatsLoader.parseGatewayTxt(textContent)
                    val activeCode = (romInfo?.gameCode?.takeIf { it.isNotBlank() } ?: extractDsiCode(rom)).uppercase()
                    val saveName = if (fileName.substringBefore('.').length >= 16) fileName
                        else if (activeCode.length == 16) "${activeCode}.txt"
                        else fileName
                    if (textContent.isNotBlank()) {
                        runCatching { File(cheatsDir, saveName).writeText(textContent) }
                    }
                    if (parsed.isNotEmpty()) {
                        val existing = database.gameDao().findGameByCode(activeCode)
                        BundledMmjCheatsLoader.insertParsedCheats(
                            database = database,
                            titleIdHex = activeCode.ifBlank { fileName.substringBefore('.').uppercase() },
                            gameTitle = romDisplayName(rom),
                            entries = parsed.map { it.copy(folder = "Gateway 3DS ($saveName)") },
                            existingGameId = existing?.id,
                        )
                    }
                } else {
                    cheatsRepository.importCheats(uri)
                    runCatching {
                        val targetName = when {
                            fileName.endsWith(".dat", ignoreCase = true) -> "usrcheat.dat"
                            fileName.endsWith(".xml", ignoreCase = true) -> "cheat.xml"
                            fileName.endsWith(".zip", ignoreCase = true) -> "cheats.zip"
                            else -> fileName
                        }
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            val target = File(cheatsDir, targetName)
                            FileOutputStream(target).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.ui_rom_cheats_saved_toast), Toast.LENGTH_SHORT).show()
                }
                refreshCheats()
            }
        }
    }

    fun downloadOfficialDatabase() {
        if (isDownloadingDb) return
        isDownloadingDb = true
        coroutineScope.launch(Dispatchers.IO) {
            val effectiveCode = romInfo?.gameCode?.takeIf { it.isNotBlank() } ?: extractDsiCode(rom)
            val effectiveTitle = romInfo?.gameTitle?.takeIf { it.isNotBlank() } ?: romDisplayName(rom)
            val populated3ds = BundledMmjCheatsLoader.populate3dsCheatsIfEmpty(context, database, effectiveCode, effectiveTitle)
            var totalSynced = 0
            runCatching {
                context.assets.list("cheats").orEmpty()
                    .filter { it.endsWith(".txt", ignoreCase = true) }
                    .forEach { fileName ->
                        val tid = fileName.substringBefore('.').uppercase()
                        if (tid.length >= 16) {
                            val entries = context.assets.open("cheats/$fileName").bufferedReader().use {
                                BundledMmjCheatsLoader.parseGatewayTxt(it.readText())
                            }
                            if (entries.isNotEmpty()) {
                                val existing = database.gameDao().findGameByCode(tid)
                                val inserted = BundledMmjCheatsLoader.insertParsedCheats(
                                    database = database,
                                    titleIdHex = tid,
                                    gameTitle = if (tid == effectiveCode) effectiveTitle else "3DS ($tid)",
                                    entries = entries.map { it.copy(folder = "Gateway 3DS ($tid)") },
                                    existingGameId = existing?.id,
                                )
                                if (inserted > 0) totalSynced += inserted
                            }
                        }
                    }
            }
            withContext(Dispatchers.Main) {
                isDownloadingDb = false
                val msg = when {
                    totalSynced > 0 -> context.getString(R.string.ui_rom_cheats_synced_fmt, totalSynced)
                    populated3ds -> context.getString(R.string.ui_rom_cheats_bundled_ok)
                    else -> context.getString(R.string.ui_rom_cheats_db_checked)
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                refreshCheats()
            }
        }
    }

    fun toggleCheat(cheat: Cheat, isEnabled: Boolean) {
        coroutineScope.launch(Dispatchers.IO) {
            cheatsRepository.updateCheatsStatus(listOf(cheat.copy(enabled = isEnabled)))
        }
    }

    val displayGameCode = romInfo?.gameCode?.takeIf { it.isNotBlank() } ?: extractDsiCode(rom)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF831843), Color(0xFF4C0519))))
                    .padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Bolt,
                            contentDescription = null,
                            tint = Color(0xFFFF0055),
                            modifier = Modifier.size(26.dp).padding(end = 8.dp)
                        )
                        Text(
                            text = if (displayGameCode.isNotBlank()) stringResource(R.string.ui_rom_cheats_title_code_fmt, displayGameCode) else stringResource(R.string.ui_rom_cheats_title_plain),
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.ui_rom_cheats_desc),
                        fontFamily = WatermelonMono,
                        fontSize = 9.5.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )

                    Spacer(Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                                .clickable { importCheatsLauncher.launch(arrayOf("application/octet-stream", "text/xml", "application/xml", "application/zip", "*/*")) }
                                .padding(vertical = 8.dp, horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.UploadFile, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(
                                    text = stringResource(R.string.ui_rom_cheats_import_btn),
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFF0055).copy(alpha = 0.35f))
                                .clickable { downloadOfficialDatabase() }
                                .padding(vertical = 8.dp, horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isDownloadingDb) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.size(6.dp))
                                Text(
                                    text = stringResource(if (isDownloadingDb) R.string.ui_rom_cheats_updating else R.string.ui_rom_cheats_update_db_btn),
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .clickable { showJusrcheatBrowser = true }
                            .padding(vertical = 9.dp, horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(
                                text = stringResource(R.string.v791_jusrcheat_open_browser_btn),
                                fontFamily = SpaceGrotesk,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFFF0055))
                }
            }
        } else if (cheatFolders.isEmpty() || cheatFolders.all { it.cheats.isEmpty() }) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface.copy(alpha = 0.5f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Bolt,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.ui_rom_cheats_empty_title),
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.ui_rom_cheats_empty_hint_fmt, "${romDisplayName(rom)}${if (displayGameCode.isNotBlank()) " [$displayGameCode]" else ""}"),
                            fontFamily = WatermelonMono,
                            fontSize = 9.5.sp,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.green)
                                .clickable { showJusrcheatBrowser = true }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Bolt,
                                    contentDescription = null,
                                    tint = if (colors.isDark) Color.Black else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.v791_jusrcheat_open_browser_btn),
                                    color = if (colors.isDark) Color.Black else Color.White,
                                    fontFamily = SpaceGrotesk,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }
            }
        } else {
            cheatFolders.forEach { folder ->
                if (folder.cheats.isNotEmpty()) {
                    val folderId = folder.id ?: 0L
                    val isExpanded = expandedFolders[folderId] ?: true

                    item(key = "folder_$folderId") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.surface)
                                .clickable { expandedFolders[folderId] = !isExpanded }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (isExpanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                                        contentDescription = null,
                                        tint = Color(0xFFFF0055),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    Text(
                                        text = folder.name,
                                        fontFamily = SpaceGrotesk,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = colors.text
                                    )
                                    Spacer(Modifier.size(6.dp))
                                    Text(
                                        text = "(${folder.cheats.size})",
                                        fontFamily = WatermelonMono,
                                        fontSize = 11.sp,
                                        color = colors.text.copy(alpha = 0.5f)
                                    )
                                }
                                Icon(
                                    if (isExpanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = colors.text.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    if (isExpanded) {
                        items(folder.cheats, key = { "cheat_${it.id}" }) { cheat ->
                            RealCheatCard(
                                cheat = cheat,
                                onToggle = { isEnabled -> toggleCheat(cheat, isEnabled) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showJusrcheatBrowser) {
        Dialog(
            onDismissRequest = { showJusrcheatBrowser = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(horizontal = 12.dp, vertical = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.line, RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Bolt, contentDescription = null, tint = colors.green, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.v791_jusrcheat_browser_title),
                                color = colors.text,
                                fontFamily = SpaceGrotesk,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = colors.text2,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable { showJusrcheatBrowser = false }
                        )
                    }

                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))

                    JusrcheatAllGamesTab(
                        romInfo = romInfo,
                        cheatsRepository = cheatsRepository,
                        onCheatAttached = {
                            refreshCheats()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

private fun extractDsiCode(rom: Rom): String {
    val titleId = rom.installedDsiWareTitleId
    if (titleId != null) {
        val low32 = (titleId and 0xFFFFFFFFL)
        val b0 = ((low32 shr 24) and 0xFF).toInt().toChar()
        val b1 = ((low32 shr 16) and 0xFF).toInt().toChar()
        val b2 = ((low32 shr 8) and 0xFF).toInt().toChar()
        val b3 = (low32 and 0xFF).toInt().toChar()
        val forward = "$b0$b1$b2$b3"
        if (forward.all { it.isLetterOrDigit() }) return forward.uppercase()
        val reverse = "$b3$b2$b1$b0"
        if (reverse.all { it.isLetterOrDigit() }) return reverse.uppercase()
        return (low32 and 0xFFFFFFFFL).toString(16).padStart(8, '0').take(4).uppercase()
    }
    return extractCodeFromName(rom.name, rom.retroAchievementsHash)
}

private fun extractCodeFromName(name: String, hashFallback: String = ""): String {
    val regex = Regex("([A-Za-z0-9]{4})")
    val match = regex.find(name)
    if (match != null) return match.value.uppercase()
    if (hashFallback.isNotBlank()) return hashFallback.take(4).uppercase()
    val clean = name.filter { it.isLetterOrDigit() }.uppercase()
    if (clean.length >= 4) return clean.take(4)
    val hash = kotlin.math.abs(name.hashCode()).toString(16).padStart(4, '0').take(4).uppercase()
    return "DS$hash".take(4)
}

private fun parseRomInfoDeep(context: android.content.Context, rom: Rom): RomInfo? {
    // 1. Prefer Nintendo 3DS 16-hex Title ID via native resolver / GameInfo / CIA header
    val cleanCode = rom.gameCode.trim().uppercase().removePrefix("0X")
    if (cleanCode.length == 16 && cleanCode.all { it in '0'..'9' || it in 'A'..'F' }) {
        val sum = cleanCode.takeLast(8).toUIntOrNull(16) ?: 0u
        return RomInfo(cleanCode, sum, rom.name, rom.name, isDsiWareTitle = false)
    }
    runCatching {
        val path = NativeLibrary.resolveNativeOrContentPath(rom.uri)
        if (path.isNotBlank()) {
            val tid = if (rom.fileName.endsWith(".cia", ignoreCase = true) || rom.fileName.endsWith(".zcia", ignoreCase = true)) {
                NativeLibrary.getCiaTitleId(path)
            } else {
                GameInfo(path).getTitleID()
            }
            if (tid != 0L) {
                val tidHex = String.format(java.util.Locale.US, "%016X", tid)
                return RomInfo(tidHex, (tid and 0xFFFFFFFFL).toUInt(), rom.name, rom.name, isDsiWareTitle = false)
            }
        }
    }
    if (rom.isInstalledDsiWareShortcut) {
        val code = extractDsiCode(rom)
        val checksum = (rom.installedDsiWareTitleId ?: 0L).toUInt()
        return RomInfo(code, checksum, rom.name, rom.name, isDsiWareTitle = true)
    }

    return try {
        val rawStream = context.contentResolver.openInputStream(rom.uri) ?: return null
        rawStream.buffered().use { buffered ->
            buffered.mark(4)
            val magic = ByteArray(4)
            val read = buffered.read(magic)
            buffered.reset()

            val isZip = read == 4 && magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()
            if (isZip) {
                ZipInputStream(buffered).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name.lowercase()
                        if (name.endsWith(".nds") || name.endsWith(".dsi") || name.endsWith(".ids")) {
                            return RomProcessor.getRomInfo(rom, zip)
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                    null
                }
            } else {
                RomProcessor.getRomInfo(rom, buffered)
            }
        }
    } catch (_: Throwable) {
        val code = extractDsiCode(rom)
        val checksum = kotlin.math.abs(rom.name.hashCode()).toUInt()
        RomInfo(code, checksum, rom.name, rom.name, isDsiWareTitle = rom.isDsiWareTitle)
    }
}

@Composable
private fun RealCheatCard(
    cheat: Cheat,
    onToggle: (Boolean) -> Unit,
) {
    val colors = watermelon
    var showDetails by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, if (cheat.enabled) Color(0xFFFF0055).copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable { showDetails = !showDetails }
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = cheat.name,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        color = if (cheat.enabled) Color(0xFFFF0055) else colors.text
                    )
                    val desc = cheat.description
                    if (!desc.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = desc,
                            fontFamily = WatermelonMono,
                            fontSize = 9.sp,
                            color = colors.text.copy(alpha = 0.65f),
                            maxLines = if (showDetails) 10 else 2
                        )
                    }
                }
                Switch(
                    checked = cheat.enabled,
                    onCheckedChange = { onToggle(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFFF0055),
                        uncheckedThumbColor = colors.text.copy(alpha = 0.6f),
                        uncheckedTrackColor = colors.bg
                    )
                )
            }

            AnimatedVisibility(visible = showDetails) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = cheat.code,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp,
                            color = Color(0xFF4ADE80),
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}
