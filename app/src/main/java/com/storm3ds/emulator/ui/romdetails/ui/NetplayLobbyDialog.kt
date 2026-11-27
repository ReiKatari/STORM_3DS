package com.storm3ds.emulator.ui.romdetails.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.preference.PreferenceManager
import com.storm3ds.emulator.MelonEmulator
import com.storm3ds.emulator.R
import com.storm3ds.emulator.domain.model.rom.Rom
import com.storm3ds.emulator.nativebridge.NativeBridgeHost
import com.storm3ds.emulator.nativebridge.NativeLibrary
import com.storm3ds.emulator.nativebridge.features.settings.SettingKeys
import com.storm3ds.emulator.nativebridge.utils.NetPlayManager
import com.storm3ds.emulator.ui.romlist.composables.romDisplayName
import com.storm3ds.emulator.ui.theme.SpaceGrotesk
import com.storm3ds.emulator.ui.theme.WatermelonMono
import com.storm3ds.emulator.ui.theme.watermelon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicBoolean

private fun isValidHostIp(input: String): Boolean {
    val trimmed = input.trim().substringBefore(':')
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return false
    val ipv4Regex = Regex("""^((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)$""")
    if (ipv4Regex.matches(trimmed)) return true
    val hostnameRegex = Regex("""^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$""")
    return hostnameRegex.matches(trimmed)
}

data class NetplayRoom(
    val id: String,
    val hostName: String,
    val gameTitle: String,
    val serverType: String,
    val currentPlayers: Int,
    val maxPlayers: Int,
    val pingMs: Int,
    val hostIp: String = "",
    val hasPassword: Boolean = false,
)

private data class WfcServerOption(
    val id: String,
    val title: String,
    val dnsIp: String,
    val description: String,
)

private fun resolveLocalIpv4Address(context: android.content.Context? = null): String {
    return runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching "127.0.0.1"
        for (intf in interfaces) {
            if (!intf.isUp || intf.isLoopback) continue
            for (addr in intf.inetAddresses) {
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    val ip = addr.hostAddress ?: continue
                    if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                        return@runCatching ip
                    }
                }
            }
        }
        context?.getString(R.string.netplay_3ds_no_wifi) ?: "Not connected to Wi-Fi"
    }.getOrDefault(context?.getString(R.string.netplay_3ds_unknown) ?: "Unknown")
}

internal fun parseDiscoveredLanRooms(
    rawSessions: Array<String>,
    fallbackTitle: String,
    context: android.content.Context? = null,
): List<NetplayRoom> {
    return rawSessions.mapNotNull { entry ->
        val parts = entry.split("|")
        when {
            parts.size >= 3 && parts[1].contains(':') && parts[2].contains('/') -> {
                val roomName = parts[0].trim().ifEmpty { "3DS Room" }
                val endpoint = parts[1].trim()
                val hostIp = endpoint.substringBefore(':').trim()
                val portStr = endpoint.substringAfter(':', "")
                val connectTarget = if (portStr.isNotEmpty() && portStr != "24872") endpoint else hostIp
                val counts = parts[2].split('/')
                val currentPlayers = counts.getOrNull(0)?.trim()?.toIntOrNull() ?: 1
                val maxPlayers = counts.getOrNull(1)?.trim()?.toIntOrNull() ?: 4
                val gameTitle = parts.getOrNull(3)?.trim()?.ifEmpty { fallbackTitle } ?: fallbackTitle
                val sourceTag = parts.getOrNull(4)?.trim().orEmpty()
                val hasPassword = sourceTag.equals("LOCKED", ignoreCase = true)
                val serverLabel = if (sourceTag.isNotEmpty() && !hasPassword) {
                    "$sourceTag ($endpoint)"
                } else {
                    "Azahar NetPlay ($endpoint)"
                }
                if (hostIp.isEmpty()) return@mapNotNull null
                NetplayRoom(
                    id = endpoint,
                    hostName = roomName,
                    gameTitle = gameTitle,
                    serverType = serverLabel,
                    currentPlayers = currentPlayers,
                    maxPlayers = maxPlayers,
                    pingMs = if (sourceTag == "LAN") 2 else 20,
                    hostIp = connectTarget,
                    hasPassword = hasPassword,
                )
            }
            parts.size >= 4 -> {
                val ip = parts[0].trim()
                val hostName = parts[1].trim().ifEmpty { context?.getString(R.string.netplay_3ds_host_default) ?: "3DS Host" }
                val currentPlayers = parts[2].toIntOrNull() ?: 1
                val maxPlayers = parts[3].toIntOrNull() ?: 4
                val status = parts.getOrNull(4)?.toIntOrNull() ?: 1
                val statusLabel = if (status == 2) {
                    context?.getString(R.string.netplay_3ds_status_playing_fmt, ip) ?: "Playing ($ip)"
                } else {
                    context?.getString(R.string.netplay_3ds_status_waiting_fmt, ip) ?: "Waiting ($ip)"
                }
                NetplayRoom(ip, hostName, fallbackTitle, statusLabel, currentPlayers, maxPlayers, 4, ip)
            }
            parts.size == 3 -> {
                val roomName = parts[0].trim().ifEmpty { "3DS Room" }
                val endpoint = parts[1].trim()
                val hostIp = endpoint.substringBefore(':').trim()
                val counts = parts[2].split('/')
                val currentPlayers = counts.getOrNull(0)?.trim()?.toIntOrNull() ?: 1
                val maxPlayers = counts.getOrNull(1)?.trim()?.toIntOrNull() ?: 4
                if (hostIp.isEmpty()) return@mapNotNull null
                NetplayRoom(endpoint, roomName, fallbackTitle, "Azahar NetPlay ($endpoint)", currentPlayers, maxPlayers, 4, endpoint)
            }
            else -> null
        }
    }
}

@Composable
fun NetplayLobbyDialog(
    rom: Rom,
    onDismiss: () -> Unit,
    onJoinRoom: (NetplayRoom) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val colors = watermelon
    var selectedTab by remember { mutableIntStateOf(0) } // 0=LAN/Lobby, 1=Host, 2=Room/Chat, 3=Online/Wireless
    val joinedSessionRef = remember { AtomicBoolean(MelonEmulator.isRoomJoined()) }
    var isLanConnecting by remember { mutableStateOf(false) }

    val defaultNickname = remember {
        prefs.getString("firmware_settings_nickname", null)?.takeIf { it.isNotBlank() }
            ?: com.storm3ds.emulator.nativebridge.utils.SystemSaveGame.DEFAULT_3DS_USERNAME
    }
    var nicknameInput by remember { mutableStateOf(defaultNickname) }
    var hostRoomName by remember { mutableStateOf(MelonEmulator.normalizeLanRoomName("", defaultNickname)) }
    var hostPortInput by remember { mutableStateOf("24872") }
    var hostMaxPlayers by remember { mutableIntStateOf(4) }
    var hostPassword by remember { mutableStateOf("") }
    var joinPassword by remember { mutableStateOf("") }
    var manualHostIp by remember { mutableStateOf(prefs.getString("lan_last_host_ip", "") ?: "") }
    var localIpAddress by remember { mutableStateOf("...") }

    var isJoined by remember { mutableStateOf(false) }
    var isModerator by remember { mutableStateOf(false) }
    var activeRoomInfo by remember { mutableStateOf<Array<String>>(emptyArray()) }
    var banList by remember { mutableStateOf<Array<String>>(emptyArray()) }
    var chatInput by remember { mutableStateOf("") }
    val chatFeed = remember { mutableStateListOf<Pair<Int, String>>() }

    var customLobbyUrl by remember { mutableStateOf(prefs.getString("3ds_web_api_url", "") ?: "") }
    var articIp by remember { mutableStateOf(prefs.getString("3ds_artic_ip", "") ?: "") }
    val streetPassStats = remember { MelonEmulator.getStreetPassBoxStats() }

    LaunchedEffect(Unit) {
        localIpAddress = withContext(Dispatchers.IO) { resolveLocalIpv4Address(context) }
        chatFeed.clear()
        chatFeed.addAll(NetPlayManager.chatMessages)
    }

    DisposableEffect(Unit) {
        NetPlayManager.setOnMessageReceivedListener { type, msg ->
            chatFeed.add(type to msg)
            if (chatFeed.size > 200) {
                chatFeed.removeAt(0)
            }
        }
        coroutineScope.launch(Dispatchers.IO) {
            runCatching { MelonEmulator.startLanDiscovery() }
        }
        onDispose {
            NetPlayManager.setOnMessageReceivedListener(null)
            val didJoin = joinedSessionRef.get()
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { MelonEmulator.stopLanDiscovery() }
                if (!didJoin) {
                    runCatching { MelonEmulator.stopLanSession() }
                }
            }
        }
    }

    val romTitle = remember(rom) { romDisplayName(rom) }
    val wfcServers = remember {
        listOf(
            WfcServerOption(
                id = "pretendo",
                title = context.getString(R.string.netplay_3ds_pretendo_title),
                dnsIp = "https://api.pretendo.cc",
                description = context.getString(R.string.netplay_3ds_pretendo_desc),
            ),
            WfcServerOption(
                id = "stock_nintendo",
                title = context.getString(R.string.netplay_3ds_stock_title),
                dnsIp = "",
                description = context.getString(R.string.netplay_3ds_stock_desc),
            ),
        )
    }

    var discoveredRooms by remember { mutableStateOf<List<NetplayRoom>>(emptyList()) }
    var activeWfcServer by remember {
        val saved = when {
            (prefs.getString("3ds_web_api_url", "") ?: "").isNotBlank() -> "custom"
            prefs.getBoolean("3ds_pretendo_enabled", false) -> "pretendo"
            else -> "stock_nintendo"
        }
        mutableStateOf(saved)
    }

    LaunchedEffect(selectedTab, romTitle) {
        while (true) {
            val joinedNow = withContext(Dispatchers.IO) { runCatching { MelonEmulator.isRoomJoined() }.getOrDefault(false) }
            isJoined = joinedNow
            joinedSessionRef.set(joinedNow)
            if (joinedNow) {
                isModerator = withContext(Dispatchers.IO) { runCatching { MelonEmulator.isRoomModerator() }.getOrDefault(false) }
                activeRoomInfo = withContext(Dispatchers.IO) { runCatching { MelonEmulator.getActiveRoomInfo() }.getOrDefault(emptyArray()) }
                if (isModerator) {
                    banList = withContext(Dispatchers.IO) { runCatching { MelonEmulator.getRoomBanList() }.getOrDefault(emptyArray()) }
                }
            }
            if (selectedTab == 0) {
                val raw = withContext(Dispatchers.IO) {
                    runCatching { MelonEmulator.startLanDiscovery() }
                    runCatching { MelonEmulator.getLanDiscoveredSessions() }.getOrDefault(emptyArray())
                }
                discoveredRooms = parseDiscoveredLanRooms(raw, romTitle, context)
            }
            delay(1200L)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (colors.isDark) 0.90f else 0.60f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, top = 20.dp, bottom = 12.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.line, RoundedCornerShape(22.dp))
                        .padding(16.dp),
                ) {
                    // Unified Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.surface2)
                                .border(1.dp, colors.line, RoundedCornerShape(10.dp)),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Wifi,
                                contentDescription = null,
                                tint = colors.green,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.netplay_3ds_header_title),
                                color = colors.text,
                                fontFamily = SpaceGrotesk,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(R.string.netplay_3ds_header_subtitle),
                                color = colors.text2,
                                fontFamily = WatermelonMono,
                                fontSize = 10.sp,
                            )
                        }

                        if (isJoined) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF10B981).copy(alpha = 0.2f))
                                    .clickable { selectedTab = 2 }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.netplay_3ds_connected_badge),
                                    color = Color(0xFF10B981),
                                    fontFamily = WatermelonMono,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.line),
                    )
                    Spacer(Modifier.height(10.dp))

                    // Horizontally scrollable full-word 4-Tab Bar (zero truncation)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surface2)
                            .horizontalScroll(rememberScrollState())
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        NetplayHubTab(
                            selected = selectedTab == 0,
                            accent = Color(0xFF00E5FF),
                            icon = Icons.Filled.Wifi,
                            label = stringResource(R.string.netplay_3ds_tab_lan),
                            onClick = { selectedTab = 0 },
                        )
                        NetplayHubTab(
                            selected = selectedTab == 1,
                            accent = Color(0xFFA855F7),
                            icon = Icons.Filled.SportsEsports,
                            label = stringResource(R.string.netplay_3ds_tab_host),
                            onClick = { selectedTab = 1 },
                        )
                        NetplayHubTab(
                            selected = selectedTab == 2,
                            accent = Color(0xFFF59E0B),
                            icon = Icons.Filled.Person,
                            label = stringResource(R.string.netplay_3ds_tab_room),
                            onClick = { selectedTab = 2 },
                        )
                        NetplayHubTab(
                            selected = selectedTab == 3,
                            accent = Color(0xFF10B981),
                            icon = Icons.Filled.Language,
                            label = stringResource(R.string.netplay_3ds_tab_online),
                            onClick = { selectedTab = 3 },
                        )
                    }

                Spacer(Modifier.height(12.dp))

                when (selectedTab) {
                    // TAB 0: LAN & Public Lobby Discovery + Direct IP
                    0 -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.surface2)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.netplay_3ds_your_ip_fmt, localIpAddress),
                                    color = Color(0xFF00E5FF),
                                    fontFamily = WatermelonMono,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = stringResource(R.string.netplay_3ds_player_fmt, nicknameInput.ifBlank { defaultNickname }),
                                    color = colors.text2,
                                    fontFamily = WatermelonMono,
                                    fontSize = 10.sp,
                                )
                            }

                            // Direct IP:Port + Optional Password Row
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.surface2)
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    NetplayInputBox(
                                        value = manualHostIp,
                                        onValueChange = { manualHostIp = it.trim() },
                                        hint = stringResource(R.string.netplay_3ds_ip_hint),
                                        accent = Color(0xFF00E5FF),
                                        modifier = Modifier.weight(1f),
                                    )
                                    NetplayInputBox(
                                        value = joinPassword,
                                        onValueChange = { joinPassword = it },
                                        hint = stringResource(R.string.netplay_3ds_password_hint),
                                        accent = Color(0xFF00E5FF),
                                        modifier = Modifier.weight(0.55f),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF00E5FF))
                                            .clickable(enabled = !isLanConnecting) {
                                                val targetIp = manualHostIp.trim()
                                                if (!isValidHostIp(targetIp)) {
                                                    Toast.makeText(context, context.getString(R.string.netplay_3ds_enter_valid_ip), Toast.LENGTH_SHORT).show()
                                                    return@clickable
                                                }
                                                isLanConnecting = true
                                                coroutineScope.launch {
                                                    val ok = withContext(Dispatchers.IO) {
                                                        runCatching {
                                                            MelonEmulator.startLanClient(
                                                                playerName = nicknameInput.ifBlank { defaultNickname },
                                                                hostIp = targetIp,
                                                                password = joinPassword,
                                                            )
                                                        }.getOrDefault(false)
                                                    }
                                                    isLanConnecting = false
                                                    if (ok) {
                                                        prefs.edit().putString("lan_last_host_ip", targetIp).apply()
                                                        joinedSessionRef.set(true)
                                                        isJoined = true
                                                        Toast.makeText(context, context.getString(R.string.netplay_3ds_connected_ip_fmt, targetIp), Toast.LENGTH_SHORT).show()
                                                        onJoinRoom(
                                                            NetplayRoom(
                                                                id = targetIp,
                                                                hostName = targetIp,
                                                                gameTitle = romTitle,
                                                                serverType = "LAN Direct IP",
                                                                currentPlayers = 2,
                                                                maxPlayers = 4,
                                                                pingMs = 4,
                                                                hostIp = targetIp,
                                                            )
                                                        )
                                                    } else {
                                                        Toast.makeText(context, context.getString(R.string.netplay_3ds_connect_failed_fmt, targetIp), Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 9.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.netplay_3ds_join_by_ip_btn),
                                            color = Color.Black,
                                            fontFamily = WatermelonMono,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }

                            // Discovered Rooms Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.netplay_3ds_rooms_found_fmt, discoveredRooms.size),
                                    color = colors.text,
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.surface2)
                                        .clickable {
                                            coroutineScope.launch {
                                                val raw = withContext(Dispatchers.IO) {
                                                    runCatching { MelonEmulator.getLanDiscoveredSessions() }.getOrDefault(emptyArray())
                                                }
                                                discoveredRooms = parseDiscoveredLanRooms(raw, romTitle, context)
                                                Toast.makeText(context, context.getString(R.string.netplay_3ds_rooms_refreshed), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.netplay_3ds_refresh_btn), tint = colors.text, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.netplay_3ds_refresh_btn), color = colors.text, fontFamily = WatermelonMono, fontSize = 9.5.sp)
                                }
                            }

                            if (discoveredRooms.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(colors.surface2.copy(alpha = 0.6f))
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(R.string.netplay_3ds_scanning_hint),
                                        color = colors.text2,
                                        fontFamily = WatermelonMono,
                                        fontSize = 10.sp,
                                        lineHeight = 15.sp,
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(discoveredRooms) { room ->
                                        RoomCard(
                                            room = room,
                                            onJoin = {
                                                if (isLanConnecting) return@RoomCard
                                                isLanConnecting = true
                                                coroutineScope.launch {
                                                    val ok = withContext(Dispatchers.IO) {
                                                        runCatching {
                                                            MelonEmulator.startLanClient(
                                                                playerName = nicknameInput.ifBlank { defaultNickname },
                                                                hostIp = room.hostIp,
                                                                password = joinPassword,
                                                            )
                                                        }.getOrDefault(false)
                                                    }
                                                    isLanConnecting = false
                                                    if (ok) {
                                                        joinedSessionRef.set(true)
                                                        isJoined = true
                                                        Toast.makeText(context, context.getString(R.string.netplay_3ds_joined_host_fmt, room.hostName), Toast.LENGTH_SHORT).show()
                                                        onJoinRoom(room)
                                                    } else {
                                                        Toast.makeText(context, context.getString(R.string.netplay_3ds_join_host_failed_fmt, room.hostIp), Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // TAB 1: Host Room Configuration
                    1 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.surface2)
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.netplay_3ds_host_form_title),
                                    color = colors.text,
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = stringResource(R.string.netplay_3ds_your_ip_fmt, localIpAddress),
                                    color = Color(0xFF00E5FF),
                                    fontFamily = WatermelonMono,
                                    fontSize = 10.5.sp,
                                )

                                NetplayLabeledField(
                                    label = stringResource(R.string.netplay_3ds_room_name_label),
                                    value = hostRoomName,
                                    onValueChange = { hostRoomName = it.take(20) },
                                    hint = "STORM 3DS Room",
                                    accent = Color(0xFFA855F7),
                                )
                                NetplayLabeledField(
                                    label = stringResource(R.string.netplay_3ds_nickname_label),
                                    value = nicknameInput,
                                    onValueChange = { nicknameInput = it.take(24) },
                                    hint = defaultNickname,
                                    accent = Color(0xFFA855F7),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    NetplayLabeledField(
                                        label = stringResource(R.string.netplay_3ds_port_label),
                                        value = hostPortInput,
                                        onValueChange = { hostPortInput = it.filter(Char::isDigit).take(5) },
                                        hint = "24872",
                                        accent = Color(0xFFA855F7),
                                        modifier = Modifier.weight(0.45f),
                                    )
                                    NetplayLabeledField(
                                        label = stringResource(R.string.netplay_3ds_password_hint),
                                        value = hostPassword,
                                        onValueChange = { hostPassword = it },
                                        hint = stringResource(R.string.netplay_3ds_optional_hint),
                                        accent = Color(0xFFA855F7),
                                        modifier = Modifier.weight(0.55f),
                                    )
                                }

                                Text(
                                    text = stringResource(R.string.netplay_3ds_max_players_label),
                                    color = colors.text2,
                                    fontFamily = WatermelonMono,
                                    fontSize = 9.5.sp,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(2, 3, 4, 6, 8).forEach { count ->
                                        val selected = hostMaxPlayers == count
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (selected) Color(0xFFA855F7) else colors.surface)
                                                .clickable { hostMaxPlayers = count }
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                        ) {
                                            Text(
                                                text = count.toString(),
                                                color = if (selected) Color.White else colors.text2,
                                                fontFamily = WatermelonMono,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Brush.horizontalGradient(listOf(Color(0xFF7E22CE), Color(0xFFA855F7))))
                                    .clickable(enabled = !isLanConnecting) {
                                        isLanConnecting = true
                                        val portNum = hostPortInput.toIntOrNull()?.coerceIn(1024, 65535) ?: 24872
                                        val safeNick = nicknameInput.ifBlank { defaultNickname }
                                        val safeRoom = MelonEmulator.normalizeLanRoomName(hostRoomName, safeNick)
                                        coroutineScope.launch {
                                            val ok = withContext(Dispatchers.IO) {
                                                runCatching {
                                                    MelonEmulator.startLanHost(
                                                        playerName = safeNick,
                                                        maxPlayers = hostMaxPlayers,
                                                        roomName = safeRoom,
                                                        password = hostPassword,
                                                        port = portNum,
                                                    )
                                                }.getOrDefault(false)
                                            }
                                            isLanConnecting = false
                                            if (ok) {
                                                joinedSessionRef.set(true)
                                                isJoined = true
                                                selectedTab = 2
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.netplay_3ds_host_created_fmt, "$localIpAddress:$portNum"),
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                                onJoinRoom(
                                                    NetplayRoom(
                                                        id = "local_host",
                                                        hostName = safeRoom,
                                                        gameTitle = romTitle,
                                                        serverType = "LAN Host ($localIpAddress:$portNum)",
                                                        currentPlayers = 1,
                                                        maxPlayers = hostMaxPlayers,
                                                        pingMs = 1,
                                                        hostIp = localIpAddress,
                                                        hasPassword = hostPassword.isNotEmpty(),
                                                    )
                                                )
                                            } else {
                                                Toast.makeText(context, context.getString(R.string.netplay_3ds_host_failed), Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.SportsEsports, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.netplay_3ds_create_host_btn),
                                        color = Color.White,
                                        fontFamily = WatermelonMono,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }

                    // TAB 2: Active Room (Members, Chat & Moderation)
                    2 -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (!isJoined) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(colors.surface2.copy(alpha = 0.6f))
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(R.string.netplay_3ds_not_in_room),
                                        color = colors.text2,
                                        fontFamily = WatermelonMono,
                                        fontSize = 10.5.sp,
                                    )
                                }
                            } else {
                                val roomHeader = activeRoomInfo.firstOrNull().orEmpty().ifBlank { hostRoomName }
                                val members = activeRoomInfo.drop(1)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.surface2)
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = roomHeader,
                                            color = colors.text,
                                            fontFamily = SpaceGrotesk,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            text = stringResource(
                                                if (isModerator) R.string.netplay_3ds_mod_role else R.string.netplay_3ds_member_role,
                                                members.size,
                                            ),
                                            color = Color(0xFFF59E0B),
                                            fontFamily = WatermelonMono,
                                            fontSize = 9.5.sp,
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFEF4444).copy(alpha = 0.2f))
                                            .clickable {
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    MelonEmulator.stopLanSession()
                                                    withContext(Dispatchers.Main) {
                                                        joinedSessionRef.set(false)
                                                        isJoined = false
                                                        activeRoomInfo = emptyArray()
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.netplay_3ds_leave_room_btn),
                                            color = Color(0xFFEF4444),
                                            fontFamily = WatermelonMono,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }

                                // Members & Moderation
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.surface2)
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.netplay_3ds_members_title),
                                        color = colors.text,
                                        fontFamily = SpaceGrotesk,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    members.forEach { member ->
                                        val isSelf = member.equals(nicknameInput.ifBlank { defaultNickname }, ignoreCase = true)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(
                                                text = member,
                                                color = colors.text2,
                                                fontFamily = WatermelonMono,
                                                fontSize = 10.sp,
                                                modifier = Modifier.weight(1f),
                                            )
                                            if (isModerator && !isSelf) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Close,
                                                        contentDescription = stringResource(R.string.netplay_3ds_kick_btn),
                                                        tint = Color(0xFFF59E0B),
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .clickable {
                                                                coroutineScope.launch(Dispatchers.IO) {
                                                                    MelonEmulator.kickRoomMember(member)
                                                                }
                                                            },
                                                    )
                                                    Icon(
                                                        imageVector = Icons.Filled.Block,
                                                        contentDescription = stringResource(R.string.netplay_3ds_ban_btn),
                                                        tint = Color(0xFFEF4444),
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .clickable {
                                                                coroutineScope.launch(Dispatchers.IO) {
                                                                    MelonEmulator.banRoomMember(member)
                                                                }
                                                            },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (isModerator && banList.isNotEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(R.string.netplay_3ds_ban_list_title),
                                            color = colors.text3,
                                            fontFamily = WatermelonMono,
                                            fontSize = 9.sp,
                                        )
                                        banList.forEach { banned ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Text(banned, color = colors.text3, fontFamily = WatermelonMono, fontSize = 9.5.sp)
                                                Text(
                                                    text = stringResource(R.string.netplay_3ds_unban_btn),
                                                    color = Color(0xFF10B981),
                                                    fontFamily = WatermelonMono,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.clickable {
                                                        coroutineScope.launch(Dispatchers.IO) {
                                                            MelonEmulator.unbanRoomMember(banned)
                                                        }
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }

                                // Chat Feed
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.surface2)
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    items(chatFeed) { (_, msg) ->
                                        Text(
                                            text = msg,
                                            color = colors.text,
                                            fontFamily = WatermelonMono,
                                            fontSize = 10.sp,
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    NetplayInputBox(
                                        value = chatInput,
                                        onValueChange = { chatInput = it },
                                        hint = stringResource(R.string.netplay_3ds_chat_hint),
                                        accent = Color(0xFFF59E0B),
                                        modifier = Modifier.weight(1f),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFF59E0B))
                                            .clickable {
                                                val msg = chatInput.trim()
                                                if (msg.isNotEmpty()) {
                                                    chatInput = ""
                                                    coroutineScope.launch(Dispatchers.IO) {
                                                        MelonEmulator.sendRoomChat(msg)
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 9.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = stringResource(R.string.netplay_3ds_send_btn),
                                            tint = Color.Black,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // TAB 3: Online Services (Pretendo / Custom Lobby / Artic Base / StreetPass)
                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            wfcServers.forEach { server ->
                                val isSelected = activeWfcServer == server.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isSelected) Color(0xFF10B981).copy(alpha = 0.16f) else colors.surface2)
                                        .border(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) Color(0xFF10B981) else colors.line,
                                            shape = RoundedCornerShape(14.dp),
                                        )
                                        .clickable {
                                            activeWfcServer = server.id
                                            customLobbyUrl = ""
                                            prefs.edit()
                                                .putBoolean("3ds_pretendo_enabled", server.id == "pretendo")
                                                .putString("3ds_web_api_url", "")
                                                .apply()
                                            MelonEmulator.setLobbyApiUrl(server.dnsIp)
                                            Toast.makeText(context, context.getString(R.string.netplay_3ds_profile_selected_fmt, server.title), Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) Color(0xFF10B981).copy(alpha = 0.25f) else colors.line),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.Language,
                                            contentDescription = null,
                                            tint = if (isSelected) Color(0xFF10B981) else colors.text2,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = server.title,
                                            color = colors.text,
                                            fontFamily = SpaceGrotesk,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            text = server.description,
                                            color = colors.text2,
                                            fontSize = 10.5.sp,
                                            lineHeight = 14.sp,
                                        )
                                    }
                                }
                            }

                            // Custom Lobby API URL
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.surface2)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Public, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.netplay_3ds_custom_lobby_label),
                                        color = colors.text,
                                        fontFamily = SpaceGrotesk,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    NetplayInputBox(
                                        value = customLobbyUrl,
                                        onValueChange = { customLobbyUrl = it.trim() },
                                        hint = "https://api.citra-emu.org",
                                        accent = Color(0xFF10B981),
                                        modifier = Modifier.weight(1f),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF10B981))
                                            .clickable {
                                                activeWfcServer = "custom"
                                                prefs.edit().putString("3ds_web_api_url", customLobbyUrl).apply()
                                                MelonEmulator.setLobbyApiUrl(customLobbyUrl)
                                                Toast.makeText(context, context.getString(R.string.netplay_3ds_custom_lobby_saved), Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(horizontal = 12.dp, vertical = 9.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.save),
                                            color = Color.White,
                                            fontFamily = WatermelonMono,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }

                            // Artic Base Wireless Shortcut
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.surface2)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Dns, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.setup_3ds_artic_ip_label),
                                        color = colors.text,
                                        fontFamily = SpaceGrotesk,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    NetplayInputBox(
                                        value = articIp,
                                        onValueChange = { articIp = it.trim() },
                                        hint = "192.168.1.50",
                                        accent = Color(0xFF38BDF8),
                                        modifier = Modifier.weight(1f),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF0284C7))
                                            .clickable {
                                                val trimmed = articIp.trim()
                                                if (trimmed.isEmpty()) return@clickable
                                                prefs.edit().putString("3ds_artic_ip", trimmed).apply()
                                                NativeLibrary.addDefaultSetting(SettingKeys.last_artic_base_addr(), trimmed)
                                                runCatching {
                                                    val articFile = File(NativeBridgeHost.resolveUserDirectoryPath(), "ArticBase.artic")
                                                    articFile.parentFile?.mkdirs()
                                                    articFile.writeText("articbase://$trimmed")
                                                    Toast.makeText(context, context.getString(R.string.setup_3ds_artic_saved_fmt, articFile.name), Toast.LENGTH_LONG).show()
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 9.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.setup_3ds_generate_artic_btn),
                                            color = Color.White,
                                            fontFamily = WatermelonMono,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }

                            // StreetPass CECD Status
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.surface2)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.netplay_3ds_streetpass_title),
                                    color = colors.text,
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = stringResource(R.string.netplay_3ds_streetpass_stats_fmt, streetPassStats.boxCount),
                                    color = colors.text2,
                                    fontFamily = WatermelonMono,
                                    fontSize = 10.sp,
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Brush.horizontalGradient(listOf(Color(0xFF059669), Color(0xFF10B981))))
                                    .clickable {
                                        val validServerIds = wfcServers.map { it.id }
                                        val chosenServer = if (activeWfcServer in validServerIds) activeWfcServer else "pretendo"
                                        val effectiveUrl = if (activeWfcServer == "custom" && customLobbyUrl.isNotBlank()) {
                                            customLobbyUrl
                                        } else {
                                            wfcServers.firstOrNull { it.id == chosenServer }?.dnsIp ?: "https://api.pretendo.cc"
                                        }
                                        prefs.edit()
                                            .putBoolean("3ds_pretendo_enabled", chosenServer == "pretendo")
                                            .putString("3ds_web_api_url", if (activeWfcServer == "custom") customLobbyUrl else "")
                                            .apply()
                                        coroutineScope.launch {
                                            withContext(Dispatchers.IO) {
                                                runCatching {
                                                    MelonEmulator.stopLanSession()
                                                    MelonEmulator.setLobbyApiUrl(effectiveUrl)
                                                }
                                            }
                                            joinedSessionRef.set(true)
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.wfc_lobby_activated_toast, activeWfcServer, effectiveUrl),
                                                Toast.LENGTH_LONG,
                                            ).show()
                                            onJoinRoom(
                                                NetplayRoom(
                                                    id = chosenServer,
                                                    hostName = chosenServer,
                                                    gameTitle = romTitle,
                                                    serverType = effectiveUrl,
                                                    currentPlayers = 1,
                                                    maxPlayers = 8,
                                                    pingMs = 24,
                                                    hostIp = effectiveUrl,
                                                )
                                            )
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Language, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.netplay_3ds_launch_online_btn),
                                        color = Color.White,
                                        fontFamily = WatermelonMono,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
                }

                // Unified Bottom Circular Back Arrow
                com.storm3ds.emulator.ui.common.UnifiedBackButton(
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun NetplayHubTab(
    selected: Boolean,
    accent: Color,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = watermelon
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) accent.copy(alpha = 0.25f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (selected) accent else colors.text2, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = if (selected) accent else colors.text2,
                fontFamily = SpaceGrotesk,
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun NetplayInputBox(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = watermelon
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                text = hint,
                color = colors.text3,
                fontFamily = WatermelonMono,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = colors.text,
                fontFamily = WatermelonMono,
                fontSize = 10.5.sp,
            ),
            cursorBrush = SolidColor(accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NetplayLabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = watermelon
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            color = colors.text2,
            fontFamily = WatermelonMono,
            fontSize = 9.5.sp,
        )
        NetplayInputBox(
            value = value,
            onValueChange = onValueChange,
            hint = hint,
            accent = accent,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RoomCard(room: NetplayRoom, onJoin: () -> Unit) {
    val colors = watermelon
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface2)
            .clickable { onJoin() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0xFF00E5FF).copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Wifi, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = room.hostName,
                    color = colors.text,
                    fontFamily = SpaceGrotesk,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (room.hasPassword) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(13.dp))
                }
            }
            Text(
                text = "${room.gameTitle} - ${room.serverType}",
                color = colors.text2,
                fontFamily = WatermelonMono,
                fontSize = 9.5.sp,
            )
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.netplay_3ds_room_players_fmt, room.currentPlayers, room.maxPlayers),
                    color = Color(0xFF10B981),
                    fontFamily = WatermelonMono,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF00E5FF))
                .clickable { onJoin() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(stringResource(R.string.netplay_3ds_join_btn), color = Color.Black, fontFamily = WatermelonMono, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
