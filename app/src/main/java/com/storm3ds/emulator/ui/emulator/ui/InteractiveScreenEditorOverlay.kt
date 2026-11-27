package com.storm3ds.emulator.ui.emulator.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.storm3ds.emulator.R
import com.storm3ds.emulator.domain.model.Rect
import com.storm3ds.emulator.domain.model.ThreeDS
import com.storm3ds.emulator.ui.theme.SpaceGrotesk
import com.storm3ds.emulator.ui.theme.watermelon
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class ResizeAnchor {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
}

@Composable
fun InteractiveScreenEditorOverlay(
    initialTopRect: Rect,
    initialBottomRect: Rect,
    surfaceWidth: Int,
    surfaceHeight: Int,
    onRectsChanged: (topRect: Rect, bottomRect: Rect) -> Unit,
    onResetRequested: () -> Pair<Rect, Rect>,
    onCancel: () -> Unit,
    onDone: (topRect: Rect, bottomRect: Rect) -> Unit,
) {
    BackHandler(onBack = onCancel)

    var editorWidth by remember(surfaceWidth) { mutableIntStateOf(surfaceWidth.coerceAtLeast(320)) }
    var editorHeight by remember(surfaceHeight) { mutableIntStateOf(surfaceHeight.coerceAtLeast(320)) }

    var topRect by remember(initialTopRect) { mutableStateOf(initialTopRect) }
    var bottomRect by remember(initialBottomRect) { mutableStateOf(initialBottomRect) }
    var selectedId by remember { mutableIntStateOf(0) } // 0 = top screen, 1 = bottom screen
    var aspectLock by remember { mutableStateOf(true) }

    val topColor = Color(0xFF38BDF8)
    val bottomColor = Color(0xFF34D399)
    val colors = watermelon

    val selectedCenterY = if (selectedId == 0) {
        topRect.y + topRect.height / 2f
    } else {
        bottomRect.y + bottomRect.height / 2f
    }
    val placeToolbarAtTop = selectedCenterY > editorHeight * 0.50f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.34f))
            .onSizeChanged { sz ->
                if (sz.width > 0 && sz.height > 0) {
                    editorWidth = sz.width
                    editorHeight = sz.height
                }
            },
    ) {
        EditableScreenFrame(
            label = stringResource(R.string.screen_editor_top_label),
            rect = topRect,
            otherRect = bottomRect,
            surfaceW = editorWidth,
            surfaceH = editorHeight,
            accentColor = topColor,
            defaultAspectRatio = ThreeDS.TOP_SCREEN_ASPECT_RATIO.toFloat(),
            isSelected = selectedId == 0,
            aspectLock = aspectLock,
            onSelect = { selectedId = 0 },
            onRectChange = { next ->
                topRect = next
                onRectsChanged(topRect, bottomRect)
            },
        )

        EditableScreenFrame(
            label = stringResource(R.string.screen_editor_bottom_label),
            rect = bottomRect,
            otherRect = topRect,
            surfaceW = editorWidth,
            surfaceH = editorHeight,
            accentColor = bottomColor,
            defaultAspectRatio = ThreeDS.BOTTOM_SCREEN_ASPECT_RATIO.toFloat(),
            isSelected = selectedId == 1,
            aspectLock = aspectLock,
            onSelect = { selectedId = 1 },
            onRectChange = { next ->
                bottomRect = next
                onRectsChanged(topRect, bottomRect)
            },
        )

        val toolbarAlignment = if (placeToolbarAtTop) Alignment.TopCenter else Alignment.BottomCenter
        Column(
            modifier = Modifier
                .align(toolbarAlignment)
                .then(if (placeToolbarAtTop) Modifier.statusBarsPadding() else Modifier.navigationBarsPadding())
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface.copy(alpha = 0.94f))
                .border(1.dp, colors.line, RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.screen_editor_hint),
                fontFamily = SpaceGrotesk,
                fontSize = 11.5.sp,
                color = colors.text2,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EditorActionPill(
                    label = stringResource(R.string.screen_editor_aspect_lock),
                    icon = Icons.Filled.AspectRatio,
                    active = aspectLock,
                    activeColor = colors.green,
                    bgColor = if (aspectLock) colors.greenDim else colors.surface2,
                    borderColor = if (aspectLock) colors.green else colors.line,
                    textColor = if (aspectLock) colors.text else colors.text2,
                    onClick = { aspectLock = !aspectLock },
                    modifier = Modifier.weight(1f),
                )
                EditorActionPill(
                    label = stringResource(R.string.screen_editor_reset),
                    icon = Icons.Filled.Refresh,
                    active = false,
                    activeColor = colors.text,
                    bgColor = colors.surface2,
                    borderColor = colors.line,
                    textColor = colors.text,
                    onClick = {
                        val (defTop, defBot) = onResetRequested()
                        topRect = defTop
                        bottomRect = defBot
                        onRectsChanged(topRect, bottomRect)
                    },
                    modifier = Modifier.weight(1f),
                )
                EditorActionPill(
                    label = stringResource(R.string.screen_editor_cancel),
                    icon = Icons.Filled.Close,
                    active = false,
                    activeColor = colors.text2,
                    bgColor = colors.surface2,
                    borderColor = colors.line,
                    textColor = colors.text2,
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                )
                EditorActionPill(
                    label = stringResource(R.string.screen_editor_done),
                    icon = Icons.Filled.Check,
                    active = true,
                    activeColor = Color.Black,
                    bgColor = colors.green,
                    borderColor = colors.green,
                    textColor = Color.Black,
                    onClick = { onDone(topRect, bottomRect) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun EditorActionPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    activeColor: Color,
    bgColor: Color,
    borderColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) activeColor else textColor,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontFamily = SpaceGrotesk,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            fontSize = 11.5.sp,
            color = textColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun EditableScreenFrame(
    label: String,
    rect: Rect,
    otherRect: Rect,
    surfaceW: Int,
    surfaceH: Int,
    accentColor: Color,
    defaultAspectRatio: Float,
    isSelected: Boolean,
    aspectLock: Boolean,
    onSelect: () -> Unit,
    onRectChange: (Rect) -> Unit,
) {
    val density = LocalDensity.current
    val currentRect by rememberUpdatedState(rect)
    val currentOther by rememberUpdatedState(otherRect)
    val currentSurfaceW by rememberUpdatedState(surfaceW)
    val currentSurfaceH by rememberUpdatedState(surfaceH)
    val currentAspectLock by rememberUpdatedState(aspectLock)
    val currentOnChange by rememberUpdatedState(onRectChange)
    val currentOnSelect by rememberUpdatedState(onSelect)

    val minW = with(density) { 84.dp.toPx() }.coerceAtLeast(110f)
    val minH = with(density) { 64.dp.toPx() }.coerceAtLeast(80f)
    val snapThreshold = with(density) { 10.dp.toPx() }
    val widthDp = with(density) { rect.width.toDp() }
    val heightDp = with(density) { rect.height.toDp() }

    Box(
        modifier = Modifier
            .offset { IntOffset(rect.x, rect.y) }
            .size(widthDp, heightDp)
            .background(accentColor.copy(alpha = if (isSelected) 0.14f else 0.06f))
            .border(
                width = if (isSelected) 2.5.dp else 1.5.dp,
                color = if (isSelected) accentColor else accentColor.copy(alpha = 0.65f),
                shape = RoundedCornerShape(4.dp),
            )
            .pointerInput(defaultAspectRatio) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    currentOnSelect()
                    var floatX = currentRect.x.toFloat()
                    var floatY = currentRect.y.toFloat()
                    var floatW = currentRect.width.toFloat()
                    var floatH = currentRect.height.toFloat()

                    while (true) {
                        val event = awaitPointerEvent()
                        val activePointers = event.changes.filter { it.pressed }
                        if (activePointers.isEmpty()) break

                        val sW = currentSurfaceW.toFloat().coerceAtLeast(minW)
                        val sH = currentSurfaceH.toFloat().coerceAtLeast(minH)

                        if (activePointers.size >= 2) {
                            val zoom = event.calculateZoom()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val prevCentroid = event.calculateCentroid(useCurrent = false)
                            val panX = centroid.x - prevCentroid.x
                            val panY = centroid.y - prevCentroid.y
                            val cx = floatX + floatW / 2f + panX
                            val cy = floatY + floatH / 2f + panY

                            var nextW = (floatW * zoom).coerceIn(minW, sW)
                            var nextH = if (currentAspectLock) {
                                nextW / defaultAspectRatio
                            } else {
                                (floatH * zoom).coerceIn(minH, sH)
                            }
                            if (currentAspectLock && nextH > sH) {
                                nextH = sH
                                nextW = nextH * defaultAspectRatio
                            }
                            if (currentAspectLock && nextH < minH) {
                                nextH = minH
                                nextW = nextH * defaultAspectRatio
                            }
                            nextW = nextW.coerceAtMost(sW)
                            nextH = nextH.coerceAtMost(sH)
                            floatW = nextW
                            floatH = nextH
                            floatX = (cx - floatW / 2f).coerceIn(0f, max(0f, sW - floatW))
                            floatY = (cy - floatH / 2f).coerceIn(0f, max(0f, sH - floatH))
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                            currentOnChange(
                                Rect(
                                    floatX.roundToInt(),
                                    floatY.roundToInt(),
                                    floatW.roundToInt().coerceAtLeast(minW.roundToInt()),
                                    floatH.roundToInt().coerceAtLeast(minH.roundToInt()),
                                ),
                            )
                        } else {
                            val ch = activePointers[0]
                            val delta = ch.position - ch.previousPosition
                            if (delta.x != 0f || delta.y != 0f) {
                                floatX = (floatX + delta.x).coerceIn(0f, max(0f, sW - floatW))
                                floatY = (floatY + delta.y).coerceIn(0f, max(0f, sH - floatH))

                                val snapped = snapPosition(
                                    x = floatX,
                                    y = floatY,
                                    w = floatW,
                                    h = floatH,
                                    surfaceW = sW,
                                    surfaceH = sH,
                                    other = currentOther,
                                    threshold = snapThreshold,
                                )
                                ch.consume()
                                currentOnChange(
                                    Rect(
                                        snapped.first.roundToInt(),
                                        snapped.second.roundToInt(),
                                        floatW.roundToInt(),
                                        floatH.roundToInt(),
                                    ),
                                )
                            }
                        }
                    }
                }
            },
    ) {
        // Screen badge top-left inside frame
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.72f))
                .border(1.dp, accentColor.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$label  ${rect.width}x${rect.height}",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = accentColor,
            )
        }

        // Helper function for handles
        val handleResize: (ResizeAnchor, Float, Float, Rect) -> Rect = { anchor, dx, dy, start ->
            resizeRect(
                anchor = anchor,
                dx = dx,
                dy = dy,
                start = start,
                surfaceW = currentSurfaceW.toFloat(),
                surfaceH = currentSurfaceH.toFloat(),
                minW = minW,
                minH = minH,
                aspectLock = currentAspectLock,
                aspectRatio = defaultAspectRatio,
            )
        }

        // 4 corner resize handles (large 40dp hit areas with visible glowing nodes)
        CornerHandle(
            alignment = Alignment.TopStart,
            color = accentColor,
            onStart = currentOnSelect,
            onDrag = { dx, dy -> currentOnChange(handleResize(ResizeAnchor.TOP_LEFT, dx, dy, currentRect)) },
        )
        CornerHandle(
            alignment = Alignment.TopEnd,
            color = accentColor,
            onStart = currentOnSelect,
            onDrag = { dx, dy -> currentOnChange(handleResize(ResizeAnchor.TOP_RIGHT, dx, dy, currentRect)) },
        )
        CornerHandle(
            alignment = Alignment.BottomStart,
            color = accentColor,
            onStart = currentOnSelect,
            onDrag = { dx, dy -> currentOnChange(handleResize(ResizeAnchor.BOTTOM_LEFT, dx, dy, currentRect)) },
        )
        CornerHandle(
            alignment = Alignment.BottomEnd,
            color = accentColor,
            onStart = currentOnSelect,
            onDrag = { dx, dy -> currentOnChange(handleResize(ResizeAnchor.BOTTOM_RIGHT, dx, dy, currentRect)) },
        )

        // 4 edge resize handles
        EdgeHandle(
            alignment = Alignment.CenterStart,
            vertical = true,
            color = accentColor,
            onStart = currentOnSelect,
            onDrag = { dx, dy -> currentOnChange(handleResize(ResizeAnchor.LEFT, dx, dy, currentRect)) },
        )
        EdgeHandle(
            alignment = Alignment.CenterEnd,
            vertical = true,
            color = accentColor,
            onStart = currentOnSelect,
            onDrag = { dx, dy -> currentOnChange(handleResize(ResizeAnchor.RIGHT, dx, dy, currentRect)) },
        )
        EdgeHandle(
            alignment = Alignment.TopCenter,
            vertical = false,
            color = accentColor,
            onStart = currentOnSelect,
            onDrag = { dx, dy -> currentOnChange(handleResize(ResizeAnchor.TOP, dx, dy, currentRect)) },
        )
        EdgeHandle(
            alignment = Alignment.BottomCenter,
            vertical = false,
            color = accentColor,
            onStart = currentOnSelect,
            onDrag = { dx, dy -> currentOnChange(handleResize(ResizeAnchor.BOTTOM, dx, dy, currentRect)) },
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.CornerHandle(
    alignment: Alignment,
    color: Color,
    onStart: () -> Unit,
    onDrag: (Float, Float) -> Unit,
) {
    val curStart by rememberUpdatedState(onStart)
    val curDrag by rememberUpdatedState(onDrag)
    Box(
        modifier = Modifier
            .align(alignment)
            .size(38.dp)
            .pointerInput(alignment) {
                detectDragGestures(
                    onDragStart = { curStart() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        curDrag(dragAmount.x, dragAmount.y)
                    },
                )
            },
        contentAlignment = alignment,
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(color)
                .border(2.dp, Color.White, CircleShape),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.EdgeHandle(
    alignment: Alignment,
    vertical: Boolean,
    color: Color,
    onStart: () -> Unit,
    onDrag: (Float, Float) -> Unit,
) {
    val curStart by rememberUpdatedState(onStart)
    val curDrag by rememberUpdatedState(onDrag)
    Box(
        modifier = Modifier
            .align(alignment)
            .then(if (vertical) Modifier.width(24.dp).height(48.dp) else Modifier.width(48.dp).height(24.dp))
            .pointerInput(alignment) {
                detectDragGestures(
                    onDragStart = { curStart() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        curDrag(dragAmount.x, dragAmount.y)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .then(if (vertical) Modifier.width(5.dp).height(26.dp) else Modifier.width(26.dp).height(5.dp))
                .clip(RoundedCornerShape(3.dp))
                .background(color.copy(alpha = 0.9f))
                .border(1.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(3.dp)),
        )
    }
}

private fun snapPosition(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    surfaceW: Float,
    surfaceH: Float,
    other: Rect,
    threshold: Float,
): Pair<Float, Float> {
    var outX = x
    var outY = y

    val centerX = (surfaceW - w) / 2f
    val otherCenterX = other.x + (other.width - w) / 2f
    val xTargets = floatArrayOf(
        0f,
        centerX,
        surfaceW - w,
        other.x.toFloat(),
        otherCenterX,
        (other.right - w),
        other.right.toFloat(),
        other.x.toFloat() - w,
    )
    for (tx in xTargets) {
        val clamped = tx.coerceIn(0f, max(0f, surfaceW - w))
        if (abs(outX - clamped) <= threshold) {
            outX = clamped
            break
        }
    }

    val centerY = (surfaceH - h) / 2f
    val yTargets = floatArrayOf(
        0f,
        centerY,
        surfaceH - h,
        other.y.toFloat(),
        other.bottom.toFloat(),
        other.y.toFloat() - h,
    )
    for (ty in yTargets) {
        val clamped = ty.coerceIn(0f, max(0f, surfaceH - h))
        if (abs(outY - clamped) <= threshold) {
            outY = clamped
            break
        }
    }
    return outX to outY
}

private fun resizeRect(
    anchor: ResizeAnchor,
    dx: Float,
    dy: Float,
    start: Rect,
    surfaceW: Float,
    surfaceH: Float,
    minW: Float,
    minH: Float,
    aspectLock: Boolean,
    aspectRatio: Float,
): Rect {
    var left = start.x.toFloat()
    var top = start.y.toFloat()
    var right = start.right.toFloat()
    var bottom = start.bottom.toFloat()

    if (!aspectLock) {
        when (anchor) {
            ResizeAnchor.LEFT, ResizeAnchor.TOP_LEFT, ResizeAnchor.BOTTOM_LEFT ->
                left = (left + dx).coerceIn(0f, right - minW)
            ResizeAnchor.RIGHT, ResizeAnchor.TOP_RIGHT, ResizeAnchor.BOTTOM_RIGHT ->
                right = (right + dx).coerceIn(left + minW, surfaceW)
            else -> {}
        }
        when (anchor) {
            ResizeAnchor.TOP, ResizeAnchor.TOP_LEFT, ResizeAnchor.TOP_RIGHT ->
                top = (top + dy).coerceIn(0f, bottom - minH)
            ResizeAnchor.BOTTOM, ResizeAnchor.BOTTOM_LEFT, ResizeAnchor.BOTTOM_RIGHT ->
                bottom = (bottom + dy).coerceIn(top + minH, surfaceH)
            else -> {}
        }
        return Rect(
            left.roundToInt(),
            top.roundToInt(),
            (right - left).roundToInt(),
            (bottom - top).roundToInt(),
        )
    }

    // Aspect-locked resizing from opposite corner or centered orthogonal axis
    val maxLockedW: Float
    val candW: Float
    when (anchor) {
        ResizeAnchor.BOTTOM_RIGHT -> {
            maxLockedW = min(surfaceW - left, (surfaceH - top) * aspectRatio).coerceAtLeast(minW)
            val fromX = (right + dx) - left
            val fromY = ((bottom + dy) - top) * aspectRatio
            candW = (if (abs(dx) >= abs(dy)) fromX else fromY).coerceIn(minW, maxLockedW)
            right = left + candW
            bottom = top + candW / aspectRatio
        }
        ResizeAnchor.BOTTOM_LEFT -> {
            maxLockedW = min(right, (surfaceH - top) * aspectRatio).coerceAtLeast(minW)
            val fromX = right - (left + dx)
            val fromY = ((bottom + dy) - top) * aspectRatio
            candW = (if (abs(dx) >= abs(dy)) fromX else fromY).coerceIn(minW, maxLockedW)
            left = right - candW
            bottom = top + candW / aspectRatio
        }
        ResizeAnchor.TOP_RIGHT -> {
            maxLockedW = min(surfaceW - left, bottom * aspectRatio).coerceAtLeast(minW)
            val fromX = (right + dx) - left
            val fromY = (bottom - (top + dy)) * aspectRatio
            candW = (if (abs(dx) >= abs(dy)) fromX else fromY).coerceIn(minW, maxLockedW)
            right = left + candW
            top = bottom - candW / aspectRatio
        }
        ResizeAnchor.TOP_LEFT -> {
            maxLockedW = min(right, bottom * aspectRatio).coerceAtLeast(minW)
            val fromX = right - (left + dx)
            val fromY = (bottom - (top + dy)) * aspectRatio
            candW = (if (abs(dx) >= abs(dy)) fromX else fromY).coerceIn(minW, maxLockedW)
            left = right - candW
            top = bottom - candW / aspectRatio
        }
        ResizeAnchor.RIGHT -> {
            val midY = (top + bottom) / 2f
            maxLockedW = min(surfaceW - left, surfaceH * aspectRatio).coerceAtLeast(minW)
            candW = ((right + dx) - left).coerceIn(minW, maxLockedW)
            val candH = candW / aspectRatio
            right = left + candW
            top = (midY - candH / 2f).coerceIn(0f, max(0f, surfaceH - candH))
            bottom = top + candH
        }
        ResizeAnchor.LEFT -> {
            val midY = (top + bottom) / 2f
            maxLockedW = min(right, surfaceH * aspectRatio).coerceAtLeast(minW)
            candW = (right - (left + dx)).coerceIn(minW, maxLockedW)
            val candH = candW / aspectRatio
            left = right - candW
            top = (midY - candH / 2f).coerceIn(0f, max(0f, surfaceH - candH))
            bottom = top + candH
        }
        ResizeAnchor.BOTTOM -> {
            val midX = (left + right) / 2f
            val maxLockedH = min(surfaceH - top, surfaceW / aspectRatio).coerceAtLeast(minH)
            val candH = ((bottom + dy) - top).coerceIn(minH, maxLockedH)
            candW = candH * aspectRatio
            bottom = top + candH
            left = (midX - candW / 2f).coerceIn(0f, max(0f, surfaceW - candW))
            right = left + candW
        }
        ResizeAnchor.TOP -> {
            val midX = (left + right) / 2f
            val maxLockedH = min(bottom, surfaceW / aspectRatio).coerceAtLeast(minH)
            val candH = (bottom - (top + dy)).coerceIn(minH, maxLockedH)
            candW = candH * aspectRatio
            top = bottom - candH
            left = (midX - candW / 2f).coerceIn(0f, max(0f, surfaceW - candW))
            right = left + candW
        }
    }
    return Rect(
        left.roundToInt(),
        top.roundToInt(),
        (right - left).roundToInt().coerceAtLeast(minW.roundToInt()),
        (bottom - top).roundToInt().coerceAtLeast(minH.roundToInt()),
    )
}
