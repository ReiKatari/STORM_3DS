package com.storm3ds.emulator.ui.common.views

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.preference.PreferenceManager
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * New Nintendo 3DS compact micro C-Stick nub with isometric 3D concave top
 * and radial micro-tilt feedback.
 */
class ModernCStickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var onAxisChanged: ((Float, Float) -> Unit)? = null

    private var axisX = 0f
    private var axisY = 0f
    private var isEngaged = false
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var resetAnimator: ValueAnimator? = null

    private val bezelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bezelStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
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

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "button_color_theme") {
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
        resetAnimator?.cancel()
        PreferenceManager.getDefaultSharedPreferences(context)
            .unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    fun updateAxis(x: Float, y: Float) {
        resetAnimator?.cancel()
        val mag = hypot(x, y)
        if (mag > 1f) {
            axisX = x / mag
            axisY = y / mag
        } else {
            axisX = x.coerceIn(-1f, 1f)
            axisY = y.coerceIn(-1f, 1f)
        }
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return false

        val size = min(w, h)
        val cx = w / 2f
        val cy = h / 2f
        val activeRadius = (size * 0.42f).coerceAtLeast(1f)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                resetAnimator?.cancel()
                activePointerId = event.getPointerId(0)
                isEngaged = true
                updateFromTouch(event.x, event.y, cx, cy, activeRadius)
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
                isEngaged = false
                activePointerId = MotionEvent.INVALID_POINTER_ID
                parent?.requestDisallowInterceptTouchEvent(false)
                onAxisChanged?.invoke(0f, 0f)
                animateReturn()
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                if (event.getPointerId(pointerIndex) == activePointerId) {
                    isEngaged = false
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    parent?.requestDisallowInterceptTouchEvent(false)
                    onAxisChanged?.invoke(0f, 0f)
                    animateReturn()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateFromTouch(touchX: Float, touchY: Float, cx: Float, cy: Float, maxRadius: Float) {
        val dx = touchX - cx
        val dy = touchY - cy
        val dist = hypot(dx, dy)
        val normDist = (dist / maxRadius).coerceIn(0f, 1f)
        val newX = if (dist > 0.001f) (dx / dist) * normDist else 0f
        val newY = if (dist > 0.001f) (dy / dist) * normDist else 0f

        if (newX != axisX || newY != axisY) {
            axisX = newX
            axisY = newY
            onAxisChanged?.invoke(axisX, axisY)
            invalidate()
        }
    }

    private fun animateReturn() {
        resetAnimator?.cancel()
        val startX = axisX
        val startY = axisY
        if (hypot(startX, startY) < 0.005f) {
            axisX = 0f
            axisY = 0f
            invalidate()
            return
        }
        resetAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 100L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val f = anim.animatedValue as Float
                axisX = startX * f
                axisY = startY * f
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val size = min(w, h)
        val cx = w / 2f
        val cy = h / 2f
        val bezelRadius = size * 0.44f
        val nubRadius = size * 0.30f
        val maxTiltShift = size * 0.085f

        val (bezelBg, bezelStroke, nubTop, nubBottom, accentGlow, textTint) = resolveColors(ButtonThemeManager.currentStyle)

        // Compact recessed bezel ring
        bezelPaint.color = bezelBg
        bezelStrokePaint.color = bezelStroke
        canvas.drawCircle(cx, cy, bezelRadius, bezelPaint)
        canvas.drawCircle(cx, cy, bezelRadius, bezelStrokePaint)

        // Micro directional dots at N/E/S/W
        val dotR = size * 0.024f
        val dotDist = bezelRadius * 0.85f
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = bezelStroke
        }
        canvas.drawCircle(cx, cy - dotDist, dotR, dotPaint)
        canvas.drawCircle(cx, cy + dotDist, dotR, dotPaint)
        canvas.drawCircle(cx - dotDist, cy, dotR, dotPaint)
        canvas.drawCircle(cx + dotDist, cy, dotR, dotPaint)

        val mag = hypot(axisX, axisY).coerceIn(0f, 1f)

        // Radial micro-tilt directional glow arc
        if (isEngaged || mag > 0.06f) {
            val angleDeg = Math.toDegrees(atan2(axisY.toDouble(), axisX.toDouble())).toFloat()
            tiltArcPaint.color = accentGlow
            tiltArcPaint.alpha = (90 + mag * 165).toInt().coerceIn(0, 255)
            val arcRect = RectF(
                cx - bezelRadius * 0.90f,
                cy - bezelRadius * 0.90f,
                cx + bezelRadius * 0.90f,
                cy + bezelRadius * 0.90f,
            )
            canvas.drawArc(arcRect, angleDeg - 38f, 76f, false, tiltArcPaint)
        }

        // Isometric micro-tilt position & perspective foreshortening
        val nubCx = cx + axisX * maxTiltShift
        val nubCy = cy + axisY * maxTiltShift

        canvas.drawCircle(nubCx + 1.5f, nubCy + 3.5f, nubRadius * 1.04f, shadowPaint)

        canvas.save()
        if (mag > 0.02f) {
            val angleDeg = Math.toDegrees(atan2(axisY.toDouble(), axisX.toDouble())).toFloat()
            canvas.rotate(angleDeg, nubCx, nubCy)
            canvas.scale(1f - mag * 0.10f, 1f + mag * 0.03f, nubCx, nubCy)
            canvas.rotate(-angleDeg, nubCx, nubCy)
        }

        // Nub outer bevel skirt
        nubBodyPaint.shader = RadialGradient(
            nubCx - nubRadius * 0.25f,
            nubCy - nubRadius * 0.30f,
            nubRadius * 1.35f,
            nubTop,
            nubBottom,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(nubCx, nubCy, nubRadius, nubBodyPaint)
        nubBodyPaint.shader = null

        // Isometric 3D concave top dish (inverted gradient: darker top-left, lit bottom-right inner rim)
        val dishRadius = nubRadius * 0.72f
        nubConcavePaint.shader = LinearGradient(
            nubCx - dishRadius,
            nubCy - dishRadius,
            nubCx + dishRadius,
            nubCy + dishRadius,
            nubBottom,
            nubTop,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(nubCx, nubCy, dishRadius, nubConcavePaint)
        nubConcavePaint.shader = null

        nubRimPaint.color = if (isEngaged || mag > 0.06f) accentGlow else Color.parseColor("#66FFFFFF")
        canvas.drawCircle(nubCx, nubCy, dishRadius, nubRimPaint)
        canvas.drawCircle(nubCx, nubCy, nubRadius, nubRimPaint)

        // Subtle C-stick micro label
        labelPaint.color = textTint
        labelPaint.alpha = 185
        labelPaint.textSize = nubRadius * 0.62f
        val textY = nubCy - (labelPaint.descent() + labelPaint.ascent()) / 2f
        canvas.drawText("C", nubCx, textY, labelPaint)

        canvas.restore()
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
            else -> CStickColors(
                Color.parseColor("#40111318"), Color.parseColor("#66FFFFFF"),
                Color.parseColor("#FF64748B"), Color.parseColor("#FF1E293B"),
                Color.parseColor("#FF00E5FF"), Color.parseColor("#FFF0F4F8"),
            )
        }
    }
}
