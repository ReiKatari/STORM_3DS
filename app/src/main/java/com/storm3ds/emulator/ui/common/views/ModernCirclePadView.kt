package com.storm3ds.emulator.ui.common.views

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.preference.PreferenceManager
import kotlin.math.hypot
import kotlin.math.min

/**
 * Nintendo 3DS recessed analog Circle Pad with concentric machined rubber cap,
 * draggable 360-degree thumb tracking, and spring-back release animation.
 */
class ModernCirclePadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var onAxisChanged: ((Float, Float) -> Unit)? = null

    private var axisX = 0f
    private var axisY = 0f
    private var isDragging = false
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var springAnimator: ValueAnimator? = null

    private val wellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val wellRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val wellInnerShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#55000000")
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
        strokeCap = Paint.Cap.ROUND
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#66000000")
    }
    private val activeGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val capBevelLightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#80FFFFFF")
    }
    private val capBevelDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#80000000")
    }
    private val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val centerDishPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

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
        springAnimator?.cancel()
        PreferenceManager.getDefaultSharedPreferences(context)
            .unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    fun updateAxis(x: Float, y: Float) {
        springAnimator?.cancel()
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
        val outerRadius = size * 0.45f
        val thumbRadius = size * 0.255f
        val maxTravel = (outerRadius - thumbRadius * 0.48f).coerceAtLeast(1f)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                springAnimator?.cancel()
                activePointerId = event.getPointerId(0)
                isDragging = true
                updateFromTouch(event.x, event.y, cx, cy, maxTravel)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val idx = event.findPointerIndex(activePointerId)
                if (idx >= 0) {
                    updateFromTouch(event.getX(idx), event.getY(idx), cx, cy, maxTravel)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                activePointerId = MotionEvent.INVALID_POINTER_ID
                parent?.requestDisallowInterceptTouchEvent(false)
                onAxisChanged?.invoke(0f, 0f)
                animateSpringBack()
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                if (event.getPointerId(pointerIndex) == activePointerId) {
                    isDragging = false
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    parent?.requestDisallowInterceptTouchEvent(false)
                    onAxisChanged?.invoke(0f, 0f)
                    animateSpringBack()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateFromTouch(touchX: Float, touchY: Float, cx: Float, cy: Float, maxTravel: Float) {
        val dx = touchX - cx
        val dy = touchY - cy
        val dist = hypot(dx, dy)
        val clampedDist = min(dist, maxTravel)
        val newX = if (dist > 0.001f) (dx / dist) * (clampedDist / maxTravel) else 0f
        val newY = if (dist > 0.001f) (dy / dist) * (clampedDist / maxTravel) else 0f

        if (newX != axisX || newY != axisY) {
            axisX = newX
            axisY = newY
            onAxisChanged?.invoke(axisX, axisY)
            invalidate()
        }
    }

    private fun animateSpringBack() {
        springAnimator?.cancel()
        val startX = axisX
        val startY = axisY
        if (hypot(startX, startY) < 0.005f) {
            axisX = 0f
            axisY = 0f
            invalidate()
            return
        }
        springAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 140L
            interpolator = OvershootInterpolator(1.1f)
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
        val outerRadius = size * 0.45f
        val thumbRadius = size * 0.255f
        val maxTravel = outerRadius - thumbRadius * 0.48f

        val palette = resolvePalette(ButtonThemeManager.currentStyle)

        // Recessed outer gate well
        wellPaint.color = palette.wellBg
        wellRimPaint.color = palette.wellRim
        canvas.drawCircle(cx, cy, outerRadius, wellPaint)
        canvas.drawCircle(cx, cy, outerRadius - 3f, wellInnerShadowPaint)
        canvas.drawCircle(cx, cy, outerRadius, wellRimPaint)

        // Cardinal directional ticks inside gate
        tickPaint.color = palette.tickColor
        val tickInner = outerRadius * 0.80f
        val tickOuter = outerRadius * 0.94f
        canvas.drawLine(cx, cy - tickOuter, cx, cy - tickInner, tickPaint)
        canvas.drawLine(cx, cy + tickOuter, cx, cy + tickInner, tickPaint)
        canvas.drawLine(cx - tickOuter, cy, cx - tickInner, cy, tickPaint)
        canvas.drawLine(cx + tickOuter, cy, cx + tickInner, cy, tickPaint)

        val thumbCx = cx + axisX * maxTravel
        val thumbCy = cy + axisY * maxTravel
        val mag = hypot(axisX, axisY).coerceIn(0f, 1f)

        // Drop shadow
        canvas.drawCircle(
            thumbCx + 2f + axisX * 2f,
            thumbCy + 4f + axisY * 2f,
            thumbRadius * 1.03f,
            shadowPaint,
        )

        // Active engagement glow
        if (isDragging || mag > 0.05f) {
            activeGlowPaint.color = palette.glow
            activeGlowPaint.alpha = (70 + (mag * 130)).toInt().coerceIn(0, 210)
            canvas.drawCircle(thumbCx, thumbCy, thumbRadius * 1.16f, activeGlowPaint)
        }

        // Machined rubber cap radial gradient
        capPaint.shader = RadialGradient(
            thumbCx - thumbRadius * 0.28f,
            thumbCy - thumbRadius * 0.30f,
            thumbRadius * 1.4f,
            palette.capTop,
            palette.capBottom,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(thumbCx, thumbCy, thumbRadius, capPaint)
        capPaint.shader = null

        // Concentric machined rubber grip rings (authentic 3DS Circle Pad texture)
        groovePaint.color = palette.grooveDark
        canvas.drawCircle(thumbCx, thumbCy, thumbRadius * 0.80f, groovePaint)
        canvas.drawCircle(thumbCx, thumbCy, thumbRadius * 0.58f, groovePaint)
        canvas.drawCircle(thumbCx, thumbCy, thumbRadius * 0.36f, groovePaint)

        centerDishPaint.color = palette.dishFill
        canvas.drawCircle(thumbCx, thumbCy, thumbRadius * 0.28f, centerDishPaint)

        // Tactile bevel rim
        val capRect = RectF(
            thumbCx - thumbRadius,
            thumbCy - thumbRadius,
            thumbCx + thumbRadius,
            thumbCy + thumbRadius,
        )
        canvas.drawArc(capRect, 135f, 180f, false, capBevelLightPaint)
        canvas.drawArc(capRect, -45f, 180f, false, capBevelDarkPaint)
    }

    private data class PadPalette(
        val wellBg: Int,
        val wellRim: Int,
        val tickColor: Int,
        val capTop: Int,
        val capBottom: Int,
        val dishFill: Int,
        val grooveDark: Int,
        val glow: Int,
    )

    private fun resolvePalette(style: ButtonColorStyle): PadPalette {
        return when (style) {
            ButtonColorStyle.CLASSIC_WHITE, ButtonColorStyle.WII_CRYSTAL, ButtonColorStyle.SWITCH_OLED -> PadPalette(
                wellBg = Color.parseColor("#33CBD5E1"),
                wellRim = Color.parseColor("#6694A3B8"),
                tickColor = Color.parseColor("#8064748B"),
                capTop = Color.parseColor("#FFF8FAFC"),
                capBottom = Color.parseColor("#FFCBD5E1"),
                dishFill = Color.parseColor("#FFE2E8F0"),
                grooveDark = Color.parseColor("#4D64748B"),
                glow = Color.parseColor("#8038BDF8"),
            )
            ButtonColorStyle.NINTENDO_3DS_AQUA -> PadPalette(
                wellBg = Color.parseColor("#33004E64"),
                wellRim = Color.parseColor("#6600D2D3"),
                tickColor = Color.parseColor("#8000D2D3"),
                capTop = Color.parseColor("#FF64748B"),
                capBottom = Color.parseColor("#FF1E293B"),
                dishFill = Color.parseColor("#FF334155"),
                grooveDark = Color.parseColor("#5500D2D3"),
                glow = Color.parseColor("#8000D2D3"),
            )
            ButtonColorStyle.STORM_CYBER_3D -> PadPalette(
                wellBg = Color.parseColor("#330A1E24"),
                wellRim = Color.parseColor("#6600F5D4"),
                tickColor = Color.parseColor("#8000F5D4"),
                capTop = Color.parseColor("#FF1E3A44"),
                capBottom = Color.parseColor("#FF0A161B"),
                dishFill = Color.parseColor("#FF11262D"),
                grooveDark = Color.parseColor("#6600F5D4"),
                glow = Color.parseColor("#8000F5D4"),
            )
            ButtonColorStyle.HYRULE_GOLD_3D -> PadPalette(
                wellBg = Color.parseColor("#333B2A06"),
                wellRim = Color.parseColor("#6610B981"),
                tickColor = Color.parseColor("#8010B981"),
                capTop = Color.parseColor("#FFD9A326"),
                capBottom = Color.parseColor("#FF6E470B"),
                dishFill = Color.parseColor("#FF92620A"),
                grooveDark = Color.parseColor("#5510B981"),
                glow = Color.parseColor("#8010B981"),
            )
            ButtonColorStyle.METROID_VISOR -> PadPalette(
                wellBg = Color.parseColor("#333B0A0A"),
                wellRim = Color.parseColor("#66F59E0B"),
                tickColor = Color.parseColor("#80F59E0B"),
                capTop = Color.parseColor("#FFB91C1C"),
                capBottom = Color.parseColor("#FF450A0A"),
                dishFill = Color.parseColor("#FF7F1D1D"),
                grooveDark = Color.parseColor("#66F59E0B"),
                glow = Color.parseColor("#80F59E0B"),
            )
            ButtonColorStyle.GALAXY_NEBULA -> PadPalette(
                wellBg = Color.parseColor("#332E1065"),
                wellRim = Color.parseColor("#66E879F9"),
                tickColor = Color.parseColor("#80E879F9"),
                capTop = Color.parseColor("#FF6D28D9"),
                capBottom = Color.parseColor("#FF1E1B4B"),
                dishFill = Color.parseColor("#FF3B0764"),
                grooveDark = Color.parseColor("#55E879F9"),
                glow = Color.parseColor("#80E879F9"),
            )
            ButtonColorStyle.ANIMAL_LEAF -> PadPalette(
                wellBg = Color.parseColor("#336EE7B7"),
                wellRim = Color.parseColor("#66059669"),
                tickColor = Color.parseColor("#80059669"),
                capTop = Color.parseColor("#FFFFFBEB"),
                capBottom = Color.parseColor("#FFA7F3D0"),
                dishFill = Color.parseColor("#FFFEF3C7"),
                grooveDark = Color.parseColor("#4D059669"),
                glow = Color.parseColor("#8034D399"),
            )
            ButtonColorStyle.MONSTER_HUNTER_FORGE -> PadPalette(
                wellBg = Color.parseColor("#331F242D"),
                wellRim = Color.parseColor("#66F97316"),
                tickColor = Color.parseColor("#80F97316"),
                capTop = Color.parseColor("#FF374151"),
                capBottom = Color.parseColor("#FF111827"),
                dishFill = Color.parseColor("#FF1F2937"),
                grooveDark = Color.parseColor("#66F97316"),
                glow = Color.parseColor("#80F97316"),
            )
            ButtonColorStyle.POKEMON_POKEBALL -> PadPalette(
                wellBg = Color.parseColor("#333F0E12"),
                wellRim = Color.parseColor("#80FFFFFF"),
                tickColor = Color.parseColor("#80FFFFFF"),
                capTop = Color.parseColor("#FFF8FAFC"),
                capBottom = Color.parseColor("#FFCBD5E1"),
                dishFill = Color.parseColor("#FFDC2626"),
                grooveDark = Color.parseColor("#4D1E293B"),
                glow = Color.parseColor("#80EF4444"),
            )
            ButtonColorStyle.PERSONA_VELVET -> PadPalette(
                wellBg = Color.parseColor("#3317255A"),
                wellRim = Color.parseColor("#66CBD5E1"),
                tickColor = Color.parseColor("#80CBD5E1"),
                capTop = Color.parseColor("#FF1E40AF"),
                capBottom = Color.parseColor("#FF0F172A"),
                dishFill = Color.parseColor("#FF17255A"),
                grooveDark = Color.parseColor("#55CBD5E1"),
                glow = Color.parseColor("#80CBD5E1"),
            )
            ButtonColorStyle.CRYSTAL_PRISM_3D -> PadPalette(
                wellBg = Color.parseColor("#2E1E293B"),
                wellRim = Color.parseColor("#667DD3FC"),
                tickColor = Color.parseColor("#80BAE6FD"),
                capTop = Color.parseColor("#CC475569"),
                capBottom = Color.parseColor("#CC1E293B"),
                dishFill = Color.parseColor("#B3334155"),
                grooveDark = Color.parseColor("#55BAE6FD"),
                glow = Color.parseColor("#807DD3FC"),
            )
            ButtonColorStyle.CRIMSON_RUBY, ButtonColorStyle.VIRTUAL_BOY -> PadPalette(
                wellBg = Color.parseColor("#332D0608"),
                wellRim = Color.parseColor("#66FF1744"),
                tickColor = Color.parseColor("#80FF1744"),
                capTop = Color.parseColor("#FF991B1B"),
                capBottom = Color.parseColor("#FF3B0609"),
                dishFill = Color.parseColor("#FF5B090E"),
                grooveDark = Color.parseColor("#55FF1744"),
                glow = Color.parseColor("#80FF1744"),
            )
            ButtonColorStyle.GOLD_LUXURY -> PadPalette(
                wellBg = Color.parseColor("#332A200B"),
                wellRim = Color.parseColor("#66F59E0B"),
                tickColor = Color.parseColor("#80FFD700"),
                capTop = Color.parseColor("#FFD97706"),
                capBottom = Color.parseColor("#FF5C2B05"),
                dishFill = Color.parseColor("#FF78350F"),
                grooveDark = Color.parseColor("#55FFD700"),
                glow = Color.parseColor("#80FFD700"),
            )
            ButtonColorStyle.EMERALD_MATRIX -> PadPalette(
                wellBg = Color.parseColor("#33052614"),
                wellRim = Color.parseColor("#6600E676"),
                tickColor = Color.parseColor("#8000E676"),
                capTop = Color.parseColor("#FF059669"),
                capBottom = Color.parseColor("#FF022C22"),
                dishFill = Color.parseColor("#FF064E3B"),
                grooveDark = Color.parseColor("#5500E676"),
                glow = Color.parseColor("#8000E676"),
            )
            else -> {
                val p = ButtonThemeManager.paletteFor(style)
                val glowArg = (p.accentGlow and 0x00FFFFFF) or 0x80000000.toInt()
                PadPalette(
                    wellBg = p.housingBg,
                    wellRim = p.housingStroke,
                    tickColor = p.housingStroke,
                    capTop = p.thumbLight,
                    capBottom = p.thumbDark,
                    dishFill = p.thumbDark,
                    grooveDark = p.housingStroke,
                    glow = glowArg,
                )
            }
        }
    }
}
