package com.storm3ds.emulator.ui.emulator.composables

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.storm3ds.emulator.domain.model.ConsoleType

@Composable
fun Console3DBootScreen(
    consoleType: ConsoleType = ConsoleType.DS,
    statusText: String = "Launching Nintendo 3DS Firmware...",
    contentAlpha: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val isEnhancedMode = false

    val lidOpenAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        lidOpenAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "console_3ds_anim")
    val floatAnim by infiniteTransition.animateFloat(
        initialValue = -2.0f,
        targetValue = 2.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "float",
    )
    val ledGlow by infiniteTransition.animateFloat(
        initialValue = 0.48f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "led_glow",
    )
    val wifiBlink by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wifi_blink",
    )
    val holoSweep by infiniteTransition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "holo_sweep",
    )

    val lidAngleX = -72f * (1f - lidOpenAnim.value)
    val screenWakeAlpha = ((lidOpenAnim.value - 0.18f) / 0.82f).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(contentAlpha)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF131B29),
                        Color(0xFF080B11),
                        Color(0xFF030407),
                    ),
                    radius = 1250f,
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(14.dp)
                .rotate(floatAnim * 0.35f),
        ) {
            // 3DS Upper Clamshell Lid (Animates open from hinge)
            Box(
                modifier = Modifier
                    .width(316.dp)
                    .graphicsLayer {
                        rotationX = lidAngleX
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        cameraDistance = 14f * density
                    }
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 8.dp, bottomEnd = 8.dp),
                        spotColor = Color(0xAA000000),
                    )
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF2B3240),
                                Color(0xFF1A1F28),
                                Color(0xFF12151C),
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(316f, 220f),
                        )
                    )
                    .border(
                        width = 1.4.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0x66FFFFFF),
                                Color(0x22FFFFFF),
                                Color(0x11000000),
                            )
                        ),
                        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 8.dp, bottomEnd = 8.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Upper Lid Bezel: Twin Speakers + Inner Camera + IR + 3D Parallax Badge
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SpeakerGrille()

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Front camera pinhole
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(Color(0xFF06090F), CircleShape)
                                    .border(0.8.dp, Color(0xFF475569), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(2.5.dp)
                                        .background(Color(0xFF38BDF8), CircleShape)
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            // Super-Stable 3D IR emitter dot
                            Box(
                                modifier = Modifier
                                    .size(3.5.dp)
                                    .background(Color(0xFF4C1D95), CircleShape)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = "Nintendo 3DS",
                                color = Color(0xFFE2E8F0),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.8.sp,
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SpeakerGrille()
                            Spacer(Modifier.width(6.dp))
                            // Emerald 3D Parallax Indicator LED
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF0B1318))
                                    .border(0.7.dp, Color(0xFF10B981).copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.5.dp),
                            ) {
                                Text(
                                    text = "3D",
                                    color = Color(0xFF10B981).copy(alpha = ledGlow),
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Spacer(Modifier.width(2.5.dp))
                                Box(
                                    modifier = Modifier
                                        .size(4.5.dp)
                                        .background(Color(0xFF10B981).copy(alpha = ledGlow), CircleShape)
                                        .shadow(4.dp, CircleShape, spotColor = Color(0xFF10B981))
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Upper Screen + Side 3D Parallax Slider Track
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Spacer(Modifier.width(8.dp))

                        // 5:3 (400x240) Upper Stereoscopic Screen
                        Box(
                            modifier = Modifier
                                .width(260.dp)
                                .height(156.dp)
                                .shadow(8.dp, RoundedCornerShape(7.dp))
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color(0xFF070B12))
                                .border(1.2.dp, Color(0x38FFFFFF), RoundedCornerShape(7.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .alpha(screenWakeAlpha)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFF0E1B2B),
                                                Color(0xFF0A1320),
                                                Color(0xFF060B13),
                                            )
                                        )
                                    )
                            )

                            // Holographic Stereoscopic Emblem & Boot Content
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.alpha(screenWakeAlpha),
                            ) {
                                // Dual-plane 3D Stereoscopic Hologram Badge
                                Box(
                                    modifier = Modifier.size(38.dp, 24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .offset(x = (-3).dp, y = (-2).dp)
                                            .size(22.dp, 15.dp)
                                            .border(1.6.dp, Color(0xFF10B981).copy(alpha = 0.85f), RoundedCornerShape(3.dp))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .offset(x = 3.dp, y = 2.dp)
                                            .size(22.dp, 15.dp)
                                            .border(1.6.dp, Color(0xFF38BDF8).copy(alpha = 0.92f), RoundedCornerShape(3.dp))
                                    )
                                }

                                Spacer(Modifier.height(6.dp))

                                Text(
                                    text = "STORM 3DS",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 2.4.sp,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = if (isEnhancedMode) "HORIZON OS - ENHANCED NAND" else "HORIZON 3DS OS v11.17",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Spacer(Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .width(156.dp)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = Color(0xFF10B981),
                                    backgroundColor = Color(0xFF1E293B),
                                )
                            }

                            // Holographic Specular Sweep
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color(0x2238BDF8),
                                                Color(0x1810B981),
                                                Color.Transparent,
                                            ),
                                            start = Offset(260f * (holoSweep - 0.35f), 0f),
                                            end = Offset(260f * (holoSweep + 0.15f), 156f),
                                        )
                                    )
                            )
                        }

                        Spacer(Modifier.width(5.dp))

                        // Physical 3D Parallax Depth Slider Notch on Right Edge
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .height(46.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF0A0D12))
                                .border(0.6.dp, Color(0xFF334155), RoundedCornerShape(3.dp)),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .size(4.dp, 10.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFF10B981).copy(alpha = ledGlow))
                            )
                        }
                    }
                }
            }

            // Cylindrical Clamshell Hinge Barrel
            Row(
                modifier = Modifier
                    .width(316.dp)
                    .height(18.dp)
                    .shadow(10.dp, RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF12161D),
                                Color(0xFF3B4556),
                                Color(0xFF1A2029),
                            )
                        )
                    )
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Left Knuckle
                Box(
                    modifier = Modifier
                        .size(11.dp, 11.dp)
                        .background(Color(0xFF475569), CircleShape)
                )

                // Center MIC pinhole
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(Color(0xFF090D14), CircleShape)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "MIC",
                        color = Color(0xFF94A3B8),
                        fontSize = 6.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // Right Hinge Status LEDs: Notification (Green), Wi-Fi (Amber), Power (Blue)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(4.5.dp)
                            .background(Color(0xFF10B981).copy(alpha = 0.85f), CircleShape)
                    )
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(4.5.dp)
                            .background(Color(0xFFFFB300).copy(alpha = wifiBlink), CircleShape)
                    )
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(5.5.dp)
                            .background(Color(0xFF00B0FF).copy(alpha = ledGlow), CircleShape)
                            .shadow(4.dp, CircleShape, spotColor = Color(0xFF00B0FF))
                    )
                }
            }

            // 3DS Lower Base Deck (Circle Pad + D-Pad | 4:3 Touch Screen | C-Stick + Colored ABXY | SELECT HOME START)
            Box(
                modifier = Modifier
                    .width(316.dp)
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 22.dp, bottomEnd = 22.dp),
                        spotColor = Color(0xAA000000),
                    )
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 22.dp, bottomEnd = 22.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF282F3C),
                                Color(0xFF191E27),
                                Color(0xFF11141A),
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(316f, 200f),
                        )
                    )
                    .border(
                        width = 1.4.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0x44FFFFFF),
                                Color(0x18FFFFFF),
                                Color(0x11000000),
                            )
                        ),
                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 22.dp, bottomEnd = 22.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Left Controls: Circle Pad above D-Pad
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.width(54.dp),
                        ) {
                            // 3DS Circle Pad
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0E121A))
                                    .border(1.dp, Color(0xFF334155), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .shadow(3.dp, CircleShape)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(Color(0xFF64748B), Color(0xFF475569), Color(0xFF334155))
                                            )
                                        )
                                        .border(0.8.dp, Color(0x55FFFFFF), CircleShape)
                                )
                            }

                            // Compact D-Pad
                            Box(
                                modifier = Modifier.size(36.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(11.dp, 32.dp)
                                        .background(Color(0xFF1E293B), RoundedCornerShape(2.5.dp))
                                        .border(0.6.dp, Color(0x33FFFFFF), RoundedCornerShape(2.5.dp))
                                )
                                Box(
                                    modifier = Modifier
                                        .size(32.dp, 11.dp)
                                        .background(Color(0xFF1E293B), RoundedCornerShape(2.5.dp))
                                        .border(0.6.dp, Color(0x33FFFFFF), RoundedCornerShape(2.5.dp))
                                )
                            }
                        }

                        // Center: 4:3 (320x240) Touch Lower Screen
                        Box(
                            modifier = Modifier
                                .width(168.dp)
                                .height(126.dp)
                                .shadow(6.dp, RoundedCornerShape(6.dp))
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF090D14))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .alpha(screenWakeAlpha)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFF101725),
                                                Color(0xFF0A0F18),
                                                Color(0xFF05080D),
                                            )
                                        )
                                    )
                            )
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .alpha(screenWakeAlpha)
                                    .padding(8.dp),
                            ) {
                                Text(
                                    text = "TOUCH TO START",
                                    color = Color(0xFFE2E8F0).copy(alpha = ledGlow),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (isEnhancedMode) "3DS NAND / SD Card Ready" else "3DS Game Card Ready",
                                    color = Color(0xFF64748B),
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }

                        // Right Controls: New 3DS C-Stick Nub above Colored ABXY Caps
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.width(54.dp),
                        ) {
                            // New 3DS C-Stick Nub
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0E121A))
                                    .border(0.8.dp, Color(0xFF334155), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF64748B))
                                )
                            }

                            // New 3DS Colored ABXY Diamond
                            Box(
                                modifier = Modifier.size(48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                ConsoleButton(
                                    label = "X",
                                    color = Color(0xFF38BDF8),
                                    modifier = Modifier.align(Alignment.TopCenter),
                                )
                                ConsoleButton(
                                    label = "Y",
                                    color = Color(0xFF10B981),
                                    modifier = Modifier.align(Alignment.CenterStart),
                                )
                                ConsoleButton(
                                    label = "A",
                                    color = Color(0xFFEF4444),
                                    modifier = Modifier.align(Alignment.CenterEnd),
                                )
                                ConsoleButton(
                                    label = "B",
                                    color = Color(0xFFFACC15),
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Bottom Hardware Bar: SELECT | HOME | START + POWER
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.width(34.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            HardwarePillButton(label = "SELECT", accent = Color(0xFF94A3B8))
                            HardwarePillButton(label = "HOME", accent = Color(0xFF38BDF8))
                            HardwarePillButton(label = "START", accent = Color(0xFF94A3B8))
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .background(Color(0xFF1E293B), CircleShape)
                                    .border(1.dp, Color(0xFF00B0FF), CircleShape)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "PWR",
                                color = Color(0xFF64748B),
                                fontSize = 6.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Status & Architecture Subtitle (Zero Em-Dashes)
            Text(
                text = statusText,
                color = Color(0xFFF1F5F9),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isEnhancedMode) {
                    "Nintendo 3DS Enhanced Mode - ARM11 + ARM9 Sync - NAND Ready"
                } else {
                    "Nintendo 3DS Architecture - Dual ARM11 + PICA200 GPU - 3D Ready"
                },
                color = Color(0xFF64748B),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun SpeakerGrille() {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.size(2.dp).background(Color(0xFF090D14), CircleShape))
            Box(Modifier.size(2.dp).background(Color(0xFF090D14), CircleShape))
            Box(Modifier.size(2.dp).background(Color(0xFF090D14), CircleShape))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.size(2.dp).background(Color(0xFF090D14), CircleShape))
            Box(Modifier.size(2.dp).background(Color(0xFF090D14), CircleShape))
            Box(Modifier.size(2.dp).background(Color(0xFF090D14), CircleShape))
        }
    }
}

@Composable
private fun HardwarePillButton(
    label: String,
    accent: Color,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF141820))
            .border(0.7.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = accent,
            fontSize = 6.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun ConsoleButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(14.dp)
            .shadow(3.dp, CircleShape)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF334155),
                        Color(0xFF1E293B),
                        Color(0xFF0F172A),
                    )
                )
            )
            .border(0.8.dp, color.copy(alpha = 0.78f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 7.5.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
        )
    }
}
