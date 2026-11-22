package com.storm3ds.emulator.ui.romdetails.ui

import androidx.compose.ui.res.stringResource
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.storm3ds.emulator.MelonEmulator
import com.storm3ds.emulator.R
import com.storm3ds.emulator.domain.model.rom.Rom
import com.storm3ds.emulator.ui.romlist.composables.romDisplayName
import com.storm3ds.emulator.ui.theme.SpaceGrotesk
import com.storm3ds.emulator.ui.theme.WatermelonMono
import com.storm3ds.emulator.ui.theme.watermelon
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicBoolean

private fun isValidHostIp(input: String): Boolean {
    val trimmed = input.trim()
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

internal fun parseDiscoveredLanRooms(rawSessions: Array<String>, fallbackTitle: String, context: android.content.Context? = null): List<NetplayRoom> {
    return rawSessions.mapNotNull { entry ->
        val parts = entry.split("|")
        when {
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
                NetplayRoom(endpoint, roomName, fallbackTitle, "Azahar NetPlay ($endpoint)", currentPlayers, maxPlayers, 4, hostIp)
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
    var selectedTab by remember { mutableStateOf(0) } // 0 = NiFi Local Wireless, 1 = WFC Online
    val joinedSessionRef = remember { AtomicBoolean(false) }
    var isLanConnecting by remember { mutableStateOf(false) }

    val defaultNickname = remember {
        prefs.getString("firmware_settings_nickname", null)?.takeIf { it.isNotBlank() } ?: "STORM_Player"
    }
    var manualHostIp by remember {
        mutableStateOf(prefs.getString("lan_last_host_ip", "") ?: "")
    }
    var localIpAddress by remember { mutableStateOf("...") }
    LaunchedEffect(Unit) {
        localIpAddress = withContext(Dispatchers.IO) { resolveLocalIpv4Address(context) }
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
        val saved = if (prefs.getBoolean("3ds_pretendo_enabled", false)) "pretendo" else "stock_nintendo"
        mutableStateOf(saved)
    }

    DisposableEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            runCatching { MelonEmulator.startLanDiscovery() }
        }
        onDispose {
            val didJoin = joinedSessionRef.get()
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { MelonEmulator.stopLanDiscovery() }
                if (!didJoin) {
                    runCatching { MelonEmulator.stopLanSession() }
                }
            }
        }
    }

    LaunchedEffect(selectedTab, romTitle) {
        if (selectedTab == 0) {
            withContext(Dispatchers.IO) {
                runCatching { MelonEmulator.startLanDiscovery() }
            }
            while (true) {
                val raw = withContext(Dispatchers.IO) {
                    runCatching { MelonEmulator.getLanDiscoveredSessions() }.getOrDefault(emptyArray())
                }
                discoveredRooms = parseDiscoveredLanRooms(raw, romTitle, context)
                delay(1200L)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (colors.isDark) 0.90f else 0.60f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.90f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.bg)
                    .border(1.dp, colors.line, RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                // Header with Back Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colors.surface2),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = colors.text,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.netplay_3ds_header_title),
                                color = colors.text,
                                fontFamily = SpaceGrotesk,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.netplay_3ds_header_subtitle),
                                color = colors.text2,
                                fontFamily = WatermelonMono,
                                fontSize = 9.5.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Mode Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface2)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selectedTab == 0) Color(0xFF00E5FF).copy(alpha = 0.25f) else Color.Transparent)
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Wifi, contentDescription = null, tint = if (selectedTab == 0) Color(0xFF00E5FF) else colors.text2, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.netplay_3ds_tab_lan), color = if (selectedTab == 0) Color(0xFF00E5FF) else colors.text2, fontFamily = WatermelonMono, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selectedTab == 1) Color(0xFF10B981).copy(alpha = 0.25f) else Color.Transparent)
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Language, contentDescription = null, tint = if (selectedTab == 1) Color(0xFF10B981) else colors.text2, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.netplay_3ds_tab_online), color = if (selectedTab == 1) Color(0xFF10B981) else colors.text2, fontFamily = WatermelonMono, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (selectedTab == 0) {
                    // NiFi Local Multiplayer (Host / Discovery / Direct IP)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Status & Host Card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.surface2)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Wifi, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.netplay_3ds_your_ip_fmt, localIpAddress),
                                        color = Color(0xFF00E5FF),
                                        fontFamily = WatermelonMono,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.netplay_3ds_player_fmt, defaultNickname),
                                    color = colors.text2,
                                    fontFamily = WatermelonMono,
                                    fontSize = 10.sp,
                                )
                            }

                            Text(
                                text = stringResource(R.string.netplay_3ds_lan_hint),
                                color = colors.text2,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Brush.horizontalGradient(listOf(Color(0xFF0284C7), Color(0xFF00E5FF))))
                                    .clickable(enabled = !isLanConnecting) {
                                        isLanConnecting = true
                                        coroutineScope.launch {
                                            val ok = withContext(Dispatchers.IO) {
                                                runCatching { MelonEmulator.startLanHost(defaultNickname, 4) }.getOrDefault(false)
                                            }
                                            isLanConnecting = false
                                            if (ok) {
                                                joinedSessionRef.set(true)
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.netplay_3ds_host_created_fmt, localIpAddress),
                                                    Toast.LENGTH_LONG
                                                ).show()
                                                onJoinRoom(
                                                    NetplayRoom(
                                                        id = "local_host",
                                                        hostName = defaultNickname,
                                                        gameTitle = romTitle,
                                                        serverType = "LAN Host ($localIpAddress)",
                                                        currentPlayers = 1,
                                                        maxPlayers = 4,
                                                        pingMs = 1,
                                                        hostIp = localIpAddress,
                                                    )
                                                )
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.netplay_3ds_host_failed),
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    }
                                    .padding(vertical = 11.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.SportsEsports, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.netplay_3ds_create_host_btn),
                                        color = Color.Black,
                                        fontFamily = WatermelonMono,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Direct IP Connect Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surface2)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surface)
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                if (manualHostIp.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.netplay_3ds_ip_hint),
                                        color = colors.text3,
                                        fontFamily = WatermelonMono,
                                        fontSize = 10.5.sp
                                    )
                                }
                                BasicTextField(
                                    value = manualHostIp,
                                    onValueChange = { manualHostIp = it.trim() },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = colors.text,
                                        fontFamily = WatermelonMono,
                                        fontSize = 11.sp
                                    ),
                                    cursorBrush = SolidColor(Color(0xFF00E5FF)),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

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
                                                runCatching { MelonEmulator.startLanClient(defaultNickname, targetIp) }.getOrDefault(false)
                                            }
                                            isLanConnecting = false
                                            if (ok) {
                                                prefs.edit().putString("lan_last_host_ip", targetIp).apply()
                                                joinedSessionRef.set(true)
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
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.netplay_3ds_join_by_ip_btn),
                                    color = Color.Black,
                                    fontFamily = WatermelonMono,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Discovered Rooms Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.netplay_3ds_rooms_found_fmt, discoveredRooms.size),
                                color = colors.text,
                                fontFamily = SpaceGrotesk,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
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
                                verticalAlignment = Alignment.CenterVertically
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
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.netplay_3ds_scanning_hint),
                                    color = colors.text2,
                                    fontFamily = WatermelonMono,
                                    fontSize = 10.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                                        MelonEmulator.startLanClient(defaultNickname, room.hostIp)
                                                    }.getOrDefault(false)
                                                }
                                                isLanConnecting = false
                                                if (ok) {
                                                    joinedSessionRef.set(true)
                                                    Toast.makeText(context, context.getString(R.string.netplay_3ds_joined_host_fmt, room.hostName), Toast.LENGTH_SHORT).show()
                                                    onJoinRoom(room)
                                                } else {
                                                    Toast.makeText(context, context.getString(R.string.netplay_3ds_join_host_failed_fmt, room.hostIp), Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Tab 1: Nintendo WFC Online Server Selection & Launch
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.surface2)
                                    .padding(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Dns, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.netplay_3ds_online_title),
                                        color = colors.text,
                                        fontFamily = SpaceGrotesk,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.netplay_3ds_online_desc),
                                    color = colors.text2,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }

                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(wfcServers) { server ->
                                    val isSelected = activeWfcServer == server.id
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(if (isSelected) Color(0xFF10B981).copy(alpha = 0.16f) else colors.surface2)
                                            .border(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) Color(0xFF10B981) else colors.line,
                                                shape = RoundedCornerShape(14.dp)
                                            )
                                            .clickable {
                                                activeWfcServer = server.id
                                                prefs.edit().putBoolean("3ds_pretendo_enabled", server.id == "pretendo").apply()
                                                Toast.makeText(context, context.getString(R.string.netplay_3ds_profile_selected_fmt, server.title), Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) Color(0xFF10B981).copy(alpha = 0.25f) else colors.line),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.Language,
                                                contentDescription = null,
                                                tint = if (isSelected) Color(0xFF10B981) else colors.text2,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = server.title,
                                                    color = colors.text,
                                                    fontFamily = SpaceGrotesk,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = if (server.dnsIp.isNotEmpty()) "api.pretendo.cc" else "LAN / Off",
                                                    color = Color(0xFF10B981),
                                                    fontFamily = WatermelonMono,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(Modifier.height(3.dp))
                                            Text(
                                                text = server.description,
                                                color = colors.text2,
                                                fontSize = 10.5.sp,
                                                lineHeight = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.horizontalGradient(listOf(Color(0xFF059669), Color(0xFF10B981))))
                                .clickable {
                                    val validServerIds = wfcServers.map { it.id }
                                    val chosenServer = if (activeWfcServer in validServerIds) activeWfcServer else "pretendo"
                                    prefs.edit().putBoolean("3ds_pretendo_enabled", chosenServer == "pretendo").apply()
                                    val serverObj = wfcServers.firstOrNull { it.id == chosenServer } ?: wfcServers.first()
                                    coroutineScope.launch {
                                        withContext(Dispatchers.IO) {
                                            runCatching { MelonEmulator.stopLanSession() }
                                        }
                                        joinedSessionRef.set(true)
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.wfc_lobby_activated_toast, serverObj.title, serverObj.dnsIp),
                                            Toast.LENGTH_LONG
                                        ).show()
                                        onJoinRoom(
                                            NetplayRoom(
                                                id = serverObj.id,
                                                hostName = serverObj.title,
                                                gameTitle = romTitle,
                                                serverType = serverObj.dnsIp,
                                                currentPlayers = 1,
                                                maxPlayers = 8,
                                                pingMs = 24,
                                                hostIp = serverObj.dnsIp,
                                            )
                                        )
                                    }
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Language, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.netplay_3ds_launch_online_btn),
                                    color = Color.White,
                                    fontFamily = WatermelonMono,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0xFF00E5FF).copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Wifi, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = room.hostName,
                color = colors.text,
                fontFamily = SpaceGrotesk,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${room.gameTitle} • ${room.serverType}",
                color = colors.text2,
                fontFamily = WatermelonMono,
                fontSize = 9.5.sp
            )
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.netplay_3ds_room_players_fmt, room.currentPlayers, room.maxPlayers),
                    color = Color(0xFF10B981),
                    fontFamily = WatermelonMono,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF00E5FF))
                .clickable { onJoin() }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(stringResource(R.string.netplay_3ds_join_btn), color = Color.Black, fontFamily = WatermelonMono, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
