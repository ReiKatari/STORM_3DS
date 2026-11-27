package com.storm3ds.emulator.ui.common.views

import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.preference.PreferenceManager
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min

/**
 * New Nintendo 3DS C-Stick nub with circular thumb travel matching Circle Pad,
 * true round cap (no oval squashing), and smooth zero-allocation tracking.
 */
class ModernCStickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val DEADZONE = 0.035f
        private const val SEND_EPSILON = 0.0015f
    }

    var onAxisChanged: ((Float, Float) -> Unit)? = null

    private var targetAxisX = 0f
    private var targetAxisY = 0f
    private var displayAxisX = 0f
    private var displayAxisY = 0f
    private var lastSentX = 0f
    private var lastSentY = 0f
    private var isEngaged = false
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var lastFrameTimeMs = 0L

    private val bezelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bezelStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tiltArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4.5f
        strokeCap = Paint.Cap.ROUND
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#73000000")
    }
    private val nubBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val nubConcavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val nubRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }

    private val arcRect = RectF()
    private val shaderMatrix = Matrix()
    private var cachedNubShader: RadialGradient? = null
    private var cachedConcaveShader: LinearGradient? = null
    private var cachedShaderSize = -1f
    private var cachedShaderStyle: ButtonColorStyle? = null

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "button_color_theme") {
            cachedNubShader = null
            cachedConcaveShader = null
            postInvalidate()
        }
    }

    init {
        ButtonThemeManager.init(context)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        PreferenceManager.getDefaultSharedPreferences(context)
            .registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        PreferenceManager.getDefaultSharedPreferences(context)
            .unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    fun updateAxis(x: Float, y: Float) {
        val mag = hypot(x, y)
        val clampedX = if (mag > 1f) x / mag else x.coerceIn(-1f, 1f)
        val clampedY = if (mag > 1f) y / mag else y.coerceIn(-1f, 1f)
        targetAxisX = clampedX
        targetAxisY = clampedY
        displayAxisX = clampedX
        displayAxisY = clampedY
        lastSentX = clampedX
        lastSentY = clampedY
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return false

        val size = min(w, h)
        val cx = w / 2f
        val cy = h / 2f
        val bezelRadius = size * 0.44f
        val nubRadius = size * 0.25f
        val maxTravel = (bezelRadius - nubRadius * 0.85f).coerceAtLeast(1f)
        val activeRadius = (maxTravel * 1.45f).coerceAtLeast(1f)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                activePointerId = event.getPointerId(0)
                isEngaged = true
                lastFrameTimeMs = SystemClock.uptimeMillis()
                updateFromTouch(event.x, event.y, cx, cy, activeRadius)
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (activePointerId == MotionEvent.INVALID_POINTER_ID) {
                    val idx = event.actionIndex
                    parent?.requestDisallowInterceptTouchEvent(true)
                    activePointerId = event.getPointerId(idx)
                    isEngaged = true
                    lastFrameTimeMs = SystemClock.uptimeMillis()
                    updateFromTouch(event.getX(idx), event.getY(idx), cx, cy, activeRadius)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val idx = event.findPointerIndex(activePointerId)
                if (idx >= 0) {
                    updateFromTouch(event.getX(idx), event.getY(idx), cx, cy, activeRadius)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                releaseStick()
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                if (event.getPointerId(pointerIndex) == activePointerId) {
                    releaseStick()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun releaseStick() {
        isEngaged = false
        activePointerId = MotionEvent.INVALID_POINTER_ID
        parent?.requestDisallowInterceptTouchEvent(false)
        targetAxisX = 0f
        targetAxisY = 0f
        lastFrameTimeMs = SystemClock.uptimeMillis()
        emitAxisIfNeeded(0f, 0f, forceZero = true)
        postInvalidateOnAnimation()
    }

    private fun updateFromTouch(touchX: Float, touchY: Float, cx: Float, cy: Float, maxRadius: Float) {
        val dx = touchX - cx
        val dy = touchY - cy
        val dist = hypot(dx, dy)
        val rawNorm = (dist / maxRadius).coerceIn(0f, 1f)
        val newX: Float
        val newY: Float
        if (rawNorm <= DEADZONE || dist <= 0.001f) {
            newX = 0f
            newY = 0f
        } else {
            val scaledMag = ((rawNorm - DEADZONE) / (1f - DEADZONE)).coerceIn(0f, 1f)
            val ux = dx / dist
            val uy = dy / dist
            newX = ux * scaledMag
            newY = uy * scaledMag
        }

        targetAxisX = newX
        targetAxisY = newY
        emitAxisIfNeeded(newX, newY, forceZero = (newX == 0f && newY == 0f))
        postInvalidateOnAnimation()
    }

    private fun emitAxisIfNeeded(newX: Float, newY: Float, forceZero: Boolean) {
        val delta = hypot(newX - lastSentX, newY - lastSentY)
        val zeroChanged = forceZero && (lastSentX != 0f || lastSentY != 0f)
        if (zeroChanged || delta >= SEND_EPSILON) {
            lastSentX = newX
            lastSentY = newY
            onAxisChanged?.invoke(newX, newY)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val now = SystemClock.uptimeMillis()
        if (lastFrameTimeMs == 0L) lastFrameTimeMs = now
        val dtSec = ((now - lastFrameTimeMs).coerceIn(1L, 50L)) / 1000f
        lastFrameTimeMs = now

        val diffX = targetAxisX - displayAxisX
        val diffY = targetAxisY - displayAxisY
        val diffMag = hypot(diffX, diffY)
        val needsMoreFrames = if (diffMag > 0.002f) {
            val speed = if (isEngaged) 48f else 24f
            val alpha = 1f - exp(-dtSec * speed)
            displayAxisX += diffX * alpha
            displayAxisY += diffY * alpha
            true
        } else {
            displayAxisX = targetAxisX
            displayAxisY = targetAxisY
            false
        }

        val size = min(w, h)
        val cx = w / 2f
        val cy = h / 2f
        val bezelRadius = size * 0.44f
        val nubRadius = size * 0.25f
        val maxTravel = (bezelRadius - nubRadius * 0.85f).coerceAtLeast(1f)

        val currentStyle = ButtonThemeManager.currentStyle
        val (bezelBg, bezelStroke, nubTop, nubBottom, accentGlow, textTint) = resolveColors(currentStyle)

        // Compact recessed bezel ring
        bezelPaint.color = bezelBg
        bezelStrokePaint.color = bezelStroke
        canvas.drawCircle(cx, cy, bezelRadius, bezelPaint)
        canvas.drawCircle(cx, cy, bezelRadius, bezelStrokePaint)

        // Micro directional dots at N/E/S/W
        val dotR = size * 0.024f
        val dotDist = bezelRadius * 0.85f
        dotPaint.color = bezelStroke
        canvas.drawCircle(cx, cy - dotDist, dotR, dotPaint)
        canvas.drawCircle(cx, cy + dotDist, dotR, dotPaint)
        canvas.drawCircle(cx - dotDist, cy, dotR, dotPaint)
        canvas.drawCircle(cx + dotDist, cy, dotR, dotPaint)

        val mag = hypot(displayAxisX, displayAxisY).coerceIn(0f, 1f)

        // Radial directional glow arc
        if (isEngaged || mag > 0.06f) {
            val angleDeg = Math.toDegrees(atan2(displayAxisY.toDouble(), displayAxisX.toDouble())).toFloat()
            tiltArcPaint.color = accentGlow
            tiltArcPaint.alpha = (90 + mag * 165).toInt().coerceIn(0, 255)
            arcRect.set(
                cx - bezelRadius * 0.90f,
                cy - bezelRadius * 0.90f,
                cx + bezelRadius * 0.90f,
                cy + bezelRadius * 0.90f,
            )
            canvas.drawArc(arcRect, angleDeg - 38f, 76f, false, tiltArcPaint)
        }

        // Circular analog position matching left Circle Pad (no oval squashing)
        val nubCx = cx + displayAxisX * maxTravel
        val nubCy = cy + displayAxisY * maxTravel

        canvas.drawCircle(nubCx + 1.5f, nubCy + 2.5f, nubRadius * 1.03f, shadowPaint)

        val dishRadius = nubRadius * 0.72f
        if (cachedNubShader == null || cachedConcaveShader == null ||
            cachedShaderSize != size || cachedShaderStyle != currentStyle
        ) {
            cachedShaderSize = size
            cachedShaderStyle = currentStyle
            cachedNubShader = RadialGradient(
                -nubRadius * 0.25f,
                -nubRadius * 0.30f,
                nubRadius * 1.35f,
                nubTop,
                nubBottom,
                Shader.TileMode.CLAMP,
            )
            cachedConcaveShader = LinearGradient(
                -dishRadius,
                -dishRadius,
                dishRadius,
                dishRadius,
                nubBottom,
                nubTop,
                Shader.TileMode.CLAMP,
            )
        }

        shaderMatrix.setTranslate(nubCx, nubCy)
        val nubShader = cachedNubShader!!
        nubShader.setLocalMatrix(shaderMatrix)
        nubBodyPaint.shader = nubShader
        canvas.drawCircle(nubCx, nubCy, nubRadius, nubBodyPaint)
        nubBodyPaint.shader = null

        val concaveShader = cachedConcaveShader!!
        concaveShader.setLocalMatrix(shaderMatrix)
        nubConcavePaint.shader = concaveShader
        canvas.drawCircle(nubCx, nubCy, dishRadius, nubConcavePaint)
        nubConcavePaint.shader = null

        nubRimPaint.color = if (isEngaged || mag > 0.06f) accentGlow else Color.parseColor("#66FFFFFF")
        canvas.drawCircle(nubCx, nubCy, dishRadius, nubRimPaint)
        canvas.drawCircle(nubCx, nubCy, nubRadius, nubRimPaint)

        labelPaint.color = textTint
        labelPaint.alpha = 185
        labelPaint.textSize = nubRadius * 0.62f
        val textY = nubCy - (labelPaint.descent() + labelPaint.ascent()) / 2f
        canvas.drawText("C", nubCx, textY, labelPaint)

        if (needsMoreFrames) {
            postInvalidateOnAnimation()
        }
    }

    private data class CStickColors(
        val bezelBg: Int,
        val bezelStroke: Int,
        val nubTop: Int,
        val nubBottom: Int,
        val accentGlow: Int,
        val textTint: Int,
    )

    private fun resolveColors(style: ButtonColorStyle): CStickColors {
        return when (style) {
            ButtonColorStyle.CLASSIC_WHITE, ButtonColorStyle.WII_CRYSTAL, ButtonColorStyle.SWITCH_OLED -> CStickColors(
                Color.parseColor("#40CBD5E1"), Color.parseColor("#8094A3B8"),
                Color.parseColor("#FFF8FAFC"), Color.parseColor("#FF94A3B8"),
                Color.parseColor("#FF0284C7"), Color.parseColor("#FF0F172A"),
            )
            ButtonColorStyle.STORM_CYBER_3D -> CStickColors(
                Color.parseColor("#4D0A1E24"), Color.parseColor("#8000F5D4"),
                Color.parseColor("#FF1E3A44"), Color.parseColor("#FF051216"),
                Color.parseColor("#FF00F5D4"), Color.parseColor("#FFA5F3FC"),
            )
            ButtonColorStyle.HYRULE_GOLD_3D -> CStickColors(
                Color.parseColor("#4D3B2A06"), Color.parseColor("#8010B981"),
                Color.parseColor("#FFD9A326"), Color.parseColor("#FF5C3D05"),
                Color.parseColor("#FF10B981"), Color.parseColor("#FFFFFBEB"),
            )
            ButtonColorStyle.METROID_VISOR -> CStickColors(
                Color.parseColor("#4D3B0A0A"), Color.parseColor("#80F59E0B"),
                Color.parseColor("#FFDC2626"), Color.parseColor("#FF450A0A"),
                Color.parseColor("#FFFBBF24"), Color.parseColor("#FFFEF3C7"),
            )
            ButtonColorStyle.GALAXY_NEBULA -> CStickColors(
                Color.parseColor("#4D2E1065"), Color.parseColor("#80E879F9"),
                Color.parseColor("#FF7E22CE"), Color.parseColor("#FF1E1B4B"),
                Color.parseColor("#FFF0ABFC"), Color.parseColor("#FFF8FAFC"),
            )
            ButtonColorStyle.ANIMAL_LEAF -> CStickColors(
                Color.parseColor("#4D6EE7B7"), Color.parseColor("#80059669"),
                Color.parseColor("#FFFFFBEB"), Color.parseColor("#FF6EE7B7"),
                Color.parseColor("#FF10B981"), Color.parseColor("#FF064E3B"),
            )
            ButtonColorStyle.MONSTER_HUNTER_FORGE -> CStickColors(
                Color.parseColor("#4D1F242D"), Color.parseColor("#80F97316"),
                Color.parseColor("#FF374151"), Color.parseColor("#FF111827"),
                Color.parseColor("#FFF97316"), Color.parseColor("#FFFED7AA"),
            )
            ButtonColorStyle.POKEMON_POKEBALL -> CStickColors(
                Color.parseColor("#4D3F0E12"), Color.parseColor("#99FFFFFF"),
                Color.parseColor("#FFEF4444"), Color.parseColor("#FF7F1D1D"),
                Color.parseColor("#FFFFFFFF"), Color.parseColor("#FFFFFFFF"),
            )
            ButtonColorStyle.PERSONA_VELVET -> CStickColors(
                Color.parseColor("#4D17255A"), Color.parseColor("#80CBD5E1"),
                Color.parseColor("#FF1D4ED8"), Color.parseColor("#FF0F172A"),
                Color.parseColor("#FFE2E8F0"), Color.parseColor("#FFF8FAFC"),
            )
            ButtonColorStyle.CRYSTAL_PRISM_3D -> CStickColors(
                Color.parseColor("#331E293B"), Color.parseColor("#80BAE6FD"),
                Color.parseColor("#CC475569"), Color.parseColor("#CC0F172A"),
                Color.parseColor("#FF7DD3FC"), Color.parseColor("#FFE0F2FE"),
            )
            ButtonColorStyle.GAMECUBE_INDIGO -> CStickColors(
                Color.parseColor("#4D2A2952"), Color.parseColor("#80F59E0B"),
                Color.parseColor("#FFFACC15"), Color.parseColor("#FFA16207"),
                Color.parseColor("#FFFDE047"), Color.parseColor("#FF0F172A"),
            )
            else -> {
                val p = ButtonThemeManager.paletteFor(style)
                CStickColors(p.housingBg, p.housingStroke, p.thumbLight, p.thumbDark, p.accentGlow, p.labelText)
            }
        }
    }
}
