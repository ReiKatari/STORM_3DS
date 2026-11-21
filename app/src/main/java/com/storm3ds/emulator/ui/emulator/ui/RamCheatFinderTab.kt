package com.storm3ds.emulator.ui.emulator.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.storm3ds.emulator.MelonEmulator
import com.storm3ds.emulator.R
import com.storm3ds.emulator.domain.model.Game
import com.storm3ds.emulator.domain.model.RomInfo
import com.storm3ds.emulator.domain.repositories.CheatsRepository
import com.storm3ds.emulator.ui.cheats.model.CheatSubmissionForm
import com.storm3ds.emulator.ui.theme.SpaceGrotesk
import com.storm3ds.emulator.ui.theme.WatermelonMono
import com.storm3ds.emulator.ui.theme.watermelon

enum class RamBitness(val bytes: Int, val stringRes: Int) {
    BYTE(1, R.string.v790_ram_bitness_8),
    SHORT(2, R.string.v790_ram_bitness_16),
    WORD(4, R.string.v790_ram_bitness_32),
}

enum class RamSearchMode(val stringRes: Int) {
    EXACT(R.string.v790_ram_mode_exact),
    INCREASED(R.string.v790_ram_mode_increased),
    DECREASED(R.string.v790_ram_mode_decreased),
    CHANGED(R.string.v790_ram_mode_changed),
    UNCHANGED(R.string.v790_ram_mode_unchanged),
}

private fun readRamValue(snapshot: ByteArray, offset: Int, bytes: Int): Long {
    if (offset < 0 || offset + bytes > snapshot.size) return 0L
    return when (bytes) {
        1 -> snapshot[offset].toLong() and 0xFFL
        2 -> {
            val b0 = snapshot[offset].toLong() and 0xFFL
            val b1 = snapshot[offset + 1].toLong() and 0xFFL
            b0 or (b1 shl 8)
        }
        4 -> {
            val b0 = snapshot[offset].toLong() and 0xFFL
            val b1 = snapshot[offset + 1].toLong() and 0xFFL
            val b2 = snapshot[offset + 2].toLong() and 0xFFL
            val b3 = snapshot[offset + 3].toLong() and 0xFFL
            b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        }
        else -> 0L
    }
}

private fun formatRamValue(value: Long, bitness: RamBitness, isHex: Boolean): String {
    return if (isHex) {
        when (bitness) {
            RamBitness.BYTE -> String.format("0x%02X", value and 0xFFL)
            RamBitness.SHORT -> String.format("0x%04X", value and 0xFFFFL)
            RamBitness.WORD -> String.format("0x%08X", value and 0xFFFFFFFFL)
        }
    } else {
        value.toString()
    }
}

private fun formatActionReplayCode(vaddr: Long, bitness: RamBitness, value: Long): String {
    val cleanAddr = vaddr and 0x0FFFFFFFL
    val addressHex = String.format("%07X", cleanAddr)
    return when (bitness) {
        RamBitness.WORD -> {
            val valHex = String.format("%08X", value and 0xFFFFFFFFL)
            "0$addressHex $valHex"
        }
        RamBitness.SHORT -> {
            val valHex = String.format("%04X", value and 0xFFFFL)
            "1$addressHex 0000$valHex"
        }
        RamBitness.BYTE -> {
            val valHex = String.format("%02X", value and 0xFFL)
            "2$addressHex 000000$valHex"
        }
    }
}

@Composable
fun RamCheatFinderTab(
    romInfo: RomInfo,
    cheatsRepository: CheatsRepository,
    onCheatCreated: () -> Unit,
    modifier: Modifier = Modifier,
    isHardcoreActive: Boolean = false,
    onSwitchToSoftcore: (() -> Unit)? = null,
) {
    val colors = watermelon
    val onAccent = if (colors.isDark) Color.Black else Color.White
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var bitness by remember { mutableStateOf(RamBitness.WORD) }
    var searchMode by remember { mutableStateOf(RamSearchMode.EXACT) }
    var isHexInput by remember { mutableStateOf(false) }
    var valueInputText by remember { mutableStateOf("") }

    var isScanning by remember { mutableStateOf(false) }
    var searchInitiated by remember { mutableStateOf(false) }
    var candidateOffsets by remember { mutableStateOf<IntArray?>(null) }
    val curValuesMap = remember { mutableStateMapOf<Int, Long>() }
    val prevValuesMap = remember { mutableStateMapOf<Int, Long>() }

    val testStatusMap = remember { mutableStateMapOf<Int, String>() }
    val testValuesMap = remember { mutableStateMapOf<Int, String>() }

    fun toNativeValueType(b: RamBitness): Int = when (b) {
        RamBitness.WORD -> 0
        RamBitness.SHORT -> 1
        RamBitness.BYTE -> 2
    }

    fun toNativeScanType(m: RamSearchMode): Int = when (m) {
        RamSearchMode.EXACT -> 0
        RamSearchMode.INCREASED -> 2
        RamSearchMode.DECREASED -> 4
        RamSearchMode.CHANGED -> 1
        RamSearchMode.UNCHANGED -> 0
    }

    fun parseInputTarget(): Long? {
        val trimmed = valueInputText.trim()
        if (trimmed.isEmpty()) return null
        return try {
            if (isHexInput || trimmed.startsWith("0x", ignoreCase = true)) {
                val hexStr = trimmed.removePrefix("0x").removePrefix("0X")
                hexStr.toLong(16)
            } else {
                trimmed.toLong()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun startNewSearch() {
        if (isScanning) return
        val targetValue = parseInputTarget()
        if (searchMode == RamSearchMode.EXACT && targetValue == null) {
            Toast.makeText(context, context.getString(R.string.v790_ram_value_required), Toast.LENGTH_SHORT).show()
            return
        }
        isScanning = true
        testStatusMap.clear()
        testValuesMap.clear()

        coroutineScope.launch {
            val raw = withContext(Dispatchers.IO) {
                com.storm3ds.emulator.nativebridge.NativeLibrary.resetSearchResults()
                val searchType = if (searchMode == RamSearchMode.EXACT) 0 else 1
                com.storm3ds.emulator.nativebridge.NativeLibrary.searchMemoryRegion(
                    startAddr = 0x00100000L,
                    stopAddr = 0x30000000L,
                    valueType = toNativeValueType(bitness),
                    searchType = searchType,
                    scanType = toNativeScanType(searchMode),
                    value = targetValue ?: 0L,
                )
            }
            withContext(Dispatchers.Main) {
                val pairCount = raw.size / 2
                val addrs = IntArray(pairCount)
                curValuesMap.clear()
                prevValuesMap.clear()
                for (i in 0 until pairCount) {
                    val addr = raw[i * 2]
                    val v = raw[i * 2 + 1].toLong() and 0xFFFFFFFFL
                    addrs[i] = addr
                    curValuesMap[addr] = v
                    prevValuesMap[addr] = v
                }
                candidateOffsets = addrs
                searchInitiated = true
                isScanning = false
            }
        }
    }

    fun filterSearch() {
        if (isScanning) return
        if (candidateOffsets == null) {
            startNewSearch()
            return
        }
        val targetValue = parseInputTarget()
        if (searchMode == RamSearchMode.EXACT && targetValue == null) {
            Toast.makeText(context, context.getString(R.string.v790_ram_value_required), Toast.LENGTH_SHORT).show()
            return
        }

        isScanning = true
        testStatusMap.clear()

        coroutineScope.launch {
            val raw = withContext(Dispatchers.IO) {
                val searchType = if (searchMode == RamSearchMode.EXACT) 0 else 1
                com.storm3ds.emulator.nativebridge.NativeLibrary.searchMemoryRegion(
                    startAddr = 0x00100000L,
                    stopAddr = 0x30000000L,
                    valueType = toNativeValueType(bitness),
                    searchType = searchType,
                    scanType = toNativeScanType(searchMode),
                    value = targetValue ?: 0L,
                )
            }
            withContext(Dispatchers.Main) {
                val oldSnapshot = curValuesMap.toMap()
                prevValuesMap.clear()
                curValuesMap.clear()
                val pairCount = raw.size / 2
                val addrs = IntArray(pairCount)
                for (i in 0 until pairCount) {
                    val addr = raw[i * 2]
                    val v = raw[i * 2 + 1].toLong() and 0xFFFFFFFFL
                    addrs[i] = addr
                    prevValuesMap[addr] = oldSnapshot[addr] ?: v
                    curValuesMap[addr] = v
                }
                candidateOffsets = addrs
                isScanning = false
            }
        }
    }

    fun parseManualNumber(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return try {
            if (isHexInput || trimmed.startsWith("0x", ignoreCase = true)) {
                val hexStr = trimmed.removePrefix("0x").removePrefix("0X")
                if (hexStr.isEmpty()) null else hexStr.toLong(16)
            } else {
                trimmed.toLong()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun testWriteOffset(offset: Int, customValue: Long) {
        if (isHardcoreActive) {
            onSwitchToSoftcore?.invoke()
        }
        val success = MelonEmulator.writeMainRamValue(offset, bitness.bytes, customValue)
        testStatusMap[offset] = if (success) {
            context.getString(R.string.v790_ram_test_success)
        } else {
            context.getString(R.string.v790_ram_test_fail)
        }
    }

    fun createAndSaveCheat(offset: Int, value: Long) {
        if (isHardcoreActive) {
            onSwitchToSoftcore?.invoke()
        }
        val ramAddress = offset.toLong() and 0xFFFFFFFFL
        val code = formatActionReplayCode(ramAddress, bitness, value)
        val cheatName = String.format(context.getString(R.string.v790_ram_cheat_name), ramAddress)

        coroutineScope.launch {
            val savedToDb = withContext(Dispatchers.IO) {
                val existingGame = cheatsRepository.findGameForRom(romInfo)
                val game = existingGame ?: run {
                    val gameTitle = romInfo.gameTitle.ifBlank { romInfo.gameName }.ifBlank { romInfo.cheatGameCode }
                    cheatsRepository.addGameCheats(
                        Game(
                            id = null,
                            name = gameTitle,
                            gameCode = romInfo.cheatGameCode,
                            gameChecksum = romInfo.headerChecksumString(),
                            cheats = emptyList(),
                        )
                    )
                }
                val folders = cheatsRepository.getAllGameCheats(game).firstOrNull() ?: emptyList()
                val targetFolder = folders.firstOrNull { it.name == "RAM Finder" }
                    ?: run {
                        cheatsRepository.addCheatFolder("RAM Finder", game)
                        cheatsRepository.getAllGameCheats(game).firstOrNull()?.firstOrNull { it.name == "RAM Finder" }
                    }

                if (targetFolder != null) {
                    val form = CheatSubmissionForm(
                        name = cheatName,
                        description = "Action Replay: $code",
                        code = code,
                    )
                    cheatsRepository.addCustomCheat(targetFolder, form)
                    true
                } else {
                    false
                }
            }
            if (savedToDb) {
                onCheatCreated()
            }

            withContext(Dispatchers.Main) {
                clipboardManager.setText(AnnotatedString(code))
                Toast.makeText(context, context.getString(R.string.v790_ram_cheat_created), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        // Controls Row 1: Bitness Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RamBitness.entries.forEach { b ->
                val selected = bitness == b
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) colors.green.copy(alpha = 0.22f) else colors.surface2)
                        .border(
                            1.dp,
                            if (selected) colors.green else colors.line,
                            RoundedCornerShape(8.dp),
                        )
                        .clickable {
                            if (bitness != b) {
                                bitness = b
                                searchInitiated = false
                                candidateOffsets = null
                            }
                        }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(b.stringRes),
                        color = if (selected) colors.green else colors.text2,
                        fontFamily = SpaceGrotesk,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Controls Row 2: Search Mode Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RamSearchMode.entries.forEach { mode ->
                val selected = searchMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) colors.green.copy(alpha = 0.22f) else colors.surface2)
                        .border(
                            1.dp,
                            if (selected) colors.green else colors.line,
                            RoundedCornerShape(6.dp),
                        )
                        .clickable { searchMode = mode }
                        .padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(mode.stringRes),
                        color = if (selected) colors.green else colors.text2,
                        fontFamily = SpaceGrotesk,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Value Input Field + Hex Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface2)
                    .border(1.dp, colors.line, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                if (valueInputText.isEmpty()) {
                    Text(
                        text = stringResource(R.string.v790_ram_enter_value),
                        color = colors.text3,
                        fontFamily = WatermelonMono,
                        fontSize = 12.sp,
                    )
                }
                BasicTextField(
                    value = valueInputText,
                    onValueChange = { valueInputText = it },
                    textStyle = TextStyle(
                        color = colors.text,
                        fontFamily = WatermelonMono,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    cursorBrush = SolidColor(colors.green),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isHexInput) KeyboardType.Ascii else KeyboardType.Number,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            // Hex / Dec Toggle Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isHexInput) colors.green.copy(alpha = 0.2f) else colors.surface2)
                    .border(
                        1.dp,
                        if (isHexInput) colors.green else colors.line,
                        RoundedCornerShape(8.dp),
                    )
                    .clickable { isHexInput = !isHexInput }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(if (isHexInput) R.string.v790_ram_hex_input else R.string.v790_ram_dec_input),
                    color = if (isHexInput) colors.green else colors.text2,
                    fontFamily = WatermelonMono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Action Buttons: New Search + Filter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { startNewSearch() },
                enabled = !isScanning,
                colors = ButtonDefaults.buttonColors(backgroundColor = colors.green),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(36.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = onAccent,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = stringResource(R.string.v790_ram_new_search),
                    color = onAccent,
                    fontFamily = SpaceGrotesk,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Button(
                onClick = { filterSearch() },
                enabled = !isScanning && searchInitiated && (candidateOffsets?.isNotEmpty() == true),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = colors.surface2,
                    disabledBackgroundColor = colors.surface2.copy(alpha = 0.5f),
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(36.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = if (searchInitiated) colors.green else colors.text3,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = stringResource(R.string.v790_ram_filter),
                    color = if (searchInitiated) colors.text else colors.text3,
                    fontFamily = SpaceGrotesk,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Stats Banner
        val candidates = candidateOffsets
        val matchesCount = candidates?.size ?: 0
        if (searchInitiated) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.surface2)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.v790_ram_results_count, matchesCount),
                    color = colors.green,
                    fontFamily = WatermelonMono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (matchesCount > 100) {
                    Text(
                        text = stringResource(R.string.v790_ram_results_showing_top, 100),
                        color = colors.text3,
                        fontFamily = SpaceGrotesk,
                        fontSize = 10.sp,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        // Results List
        if (isScanning) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = colors.green, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.v790_ram_searching),
                        color = colors.text2,
                        fontFamily = SpaceGrotesk,
                        fontSize = 11.5.sp,
                    )
                }
            }
        } else if (candidates != null && candidates.isNotEmpty()) {
            val topMatches = remember(candidates) {
                candidates.take(100)
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(topMatches, key = { it }) { offset ->
                    val ramAddress = offset.toLong() and 0xFFFFFFFFL
                    val addressHex = String.format("0x%08X", ramAddress)
                    val curVal = curValuesMap[offset] ?: 0L
                    val prevVal = prevValuesMap[offset] ?: curVal

                    var itemTestValue by remember(offset, curVal) {
                        mutableStateOf(testValuesMap[offset] ?: curVal.toString())
                    }
                    val statusText = testStatusMap[offset]

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surface2)
                            .border(1.dp, colors.line, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = addressHex,
                                    color = colors.green,
                                    fontFamily = WatermelonMono,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = stringResource(R.string.v790_ram_current_val, formatRamValue(curVal, bitness, isHexInput)),
                                        color = colors.text,
                                        fontFamily = WatermelonMono,
                                        fontSize = 10.5.sp,
                                    )
                                    Text(
                                        text = stringResource(R.string.v790_ram_prev_val, formatRamValue(prevVal, bitness, isHexInput)),
                                        color = colors.text3,
                                        fontFamily = WatermelonMono,
                                        fontSize = 10.5.sp,
                                    )
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            // Test Value Input + Test Button + Create Cheat Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colors.surface)
                                        .border(1.dp, colors.line, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    BasicTextField(
                                        value = itemTestValue,
                                        onValueChange = {
                                            itemTestValue = it
                                            testValuesMap[offset] = it
                                        },
                                        textStyle = TextStyle(
                                            color = colors.text,
                                            fontFamily = WatermelonMono,
                                            fontSize = 11.sp,
                                        ),
                                        cursorBrush = SolidColor(colors.green),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }

                                // "Тест" Button
                                Box(
                                    modifier = Modifier
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colors.surface)
                                        .border(1.dp, colors.green.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                        .clickable {
                                            val parsed = parseManualNumber(itemTestValue)
                                            if (parsed == null) {
                                                Toast.makeText(context, context.getString(R.string.ram_finder_invalid_number), Toast.LENGTH_SHORT).show()
                                                return@clickable
                                            }
                                            testWriteOffset(offset, parsed)
                                        }
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(R.string.v790_ram_btn_test),
                                        color = colors.green,
                                        fontFamily = SpaceGrotesk,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }

                                // "Создать чит" Button
                                Box(
                                    modifier = Modifier
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colors.green)
                                        .clickable {
                                            val parsed = parseManualNumber(itemTestValue)
                                            if (parsed == null) {
                                                Toast.makeText(context, context.getString(R.string.ram_finder_invalid_number), Toast.LENGTH_SHORT).show()
                                                return@clickable
                                            }
                                            createAndSaveCheat(offset, parsed)
                                        }
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = null,
                                            tint = onAccent,
                                            modifier = Modifier.size(13.dp),
                                        )
                                        Spacer(Modifier.width(3.dp))
                                        Text(
                                            text = stringResource(R.string.v790_ram_btn_create_cheat),
                                            color = onAccent,
                                            fontFamily = SpaceGrotesk,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }

                            if (statusText != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = statusText,
                                    color = if (statusText == context.getString(R.string.v790_ram_test_success)) colors.green else colors.red,
                                    fontFamily = WatermelonMono,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        } else if (searchInitiated) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.v790_ram_no_results),
                    color = colors.text3,
                    fontFamily = SpaceGrotesk,
                    fontSize = 13.sp,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.v790_ram_finder_title),
                    color = colors.text3,
                    fontFamily = SpaceGrotesk,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
