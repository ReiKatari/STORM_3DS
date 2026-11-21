package com.storm3ds.emulator.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun WatermelonMark(
    modifier: Modifier = Modifier,
    height: Dp = 24.dp,
) {
    Canvas(modifier = modifier.size(width = height * 22f / 24f, height = height)) {
        val u = size.height / 24f
        val fullW = 22f * u
        val topH = 10f * u
        val corner = CornerRadius(2.4f * u, 2.4f * u)

        val topBrush = Brush.linearGradient(
            colors = listOf(Color(0xFF00E5FF), Color(0xFF0066FF)),
            start = Offset(0f, 0f),
            end = Offset(fullW, topH),
        )
        val bottomBrush = Brush.linearGradient(
            colors = listOf(Color(0xFF0066FF), Color(0xFF7B2CBF)),
            start = Offset(0f, 13.5f * u),
            end = Offset(fullW, 24f * u),
        )

        // Wide 5:3 Top 3DS Screen
        drawRoundRect(
            brush = topBrush,
            topLeft = Offset(0f, 0.5f * u),
            size = Size(fullW, topH),
            cornerRadius = corner,
        )
        // Stereoscopic depth bezel inside top 3D screen
        drawRoundRect(
            color = Color(0xFF0D0B1C),
            topLeft = Offset(2.2f * u, 2.1f * u),
            size = Size(fullW - 4.4f * u, topH - 3.2f * u),
            cornerRadius = CornerRadius(1.2f * u, 1.2f * u),
        )
        drawRoundRect(
            color = Color(0xFF00E5FF),
            topLeft = Offset(3.4f * u, 3.0f * u),
            size = Size(fullW - 6.8f * u, topH - 5.0f * u),
            cornerRadius = CornerRadius(0.8f * u, 0.8f * u),
            style = Stroke(width = 0.7f * u),
        )

        // Glowing 3DS Hinge Bar
        drawRoundRect(
            color = Color(0xFF5CF2FF),
            topLeft = Offset(0.6f * u, 11.4f * u),
            size = Size(fullW - 1.2f * u, 1.3f * u),
            cornerRadius = CornerRadius(0.65f * u, 0.65f * u),
        )

        // Left Circle Pad Indicator
        drawCircle(
            color = Color(0xFF00E5FF),
            radius = 2.0f * u,
            center = Offset(2.6f * u, 18.6f * u),
            style = Stroke(width = 1.0f * u),
        )

        // Narrower 4:3 Bottom Touch Screen (offset right for asymmetric 3DS feel)
        val botLeft = 6.2f * u
        val botW = 14.8f * u
        val botTop = 13.6f * u
        val botH = 9.9f * u
        drawRoundRect(
            brush = bottomBrush,
            topLeft = Offset(botLeft, botTop),
            size = Size(botW, botH),
            cornerRadius = corner,
        )

        // STORM Lightning Spark centered in lower screen
        val cx = botLeft + botW * 0.5f
        val lightningPath = Path().apply {
            moveTo(cx + 1.0f * u, botTop + 1.8f * u)
            lineTo(cx - 1.8f * u, botTop + 5.2f * u)
            lineTo(cx + 0.1f * u, botTop + 5.2f * u)
            lineTo(cx - 1.0f * u, botTop + 8.3f * u)
            lineTo(cx + 2.0f * u, botTop + 4.5f * u)
            lineTo(cx + 0.1f * u, botTop + 4.5f * u)
            close()
        }
        drawPath(path = lightningPath, color = Color.White)
    }
}
