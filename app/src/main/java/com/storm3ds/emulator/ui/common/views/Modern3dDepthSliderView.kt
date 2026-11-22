package com.storm3ds.emulator.ui.common.views

import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.preference.PreferenceManager
import kotlin.math.roundToInt

/**
 * Vertical stereoscopic 3D Depth Slider (0..100) with metallic knurled thumb,
 * snap detent at 0 (OFF), and emerald green glowing 3D LED when depth > 0.
 */
class Modern3dDepthSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var onDepthChanged: ((Int) -> Unit)? = null

    private var currentDepth: Int = 0
    private var isDragging = false

    private val housingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val housingStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#D9090B10")
    }
    private val fillBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
    }
    private val ledGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ledCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#73000000")
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val thumbStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
    }
    private val knurlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
        if (key == "button_color_theme") {
            postInvalidate()
        } else if (key == "3ds_depth_factor" || key == "video_vulkan_3d_depth_strength") {
            val depth = if (sp.contains("video_vulkan_3d_depth_strength")) {
                sp.getString("video_vulkan_3d_depth_strength", "50")?.toIntOrNull()
                    ?: sp.getInt("3ds_depth_factor", 50)
            } else {
                sp.getInt("3ds_depth_factor", 50)
            }
            currentDepth = depth.coerceIn(0, 100)
            postInvalidate()
        }
    }

    init {
        ButtonThemeManager.init(context)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val synced = sp.getString("video_vulkan_3d_depth_strength", null)?.toIntOrNull()
            ?: sp.getInt("3ds_depth_factor", currentDepth)
        currentDepth = synced.coerceIn(0, 100)
        sp.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        PreferenceManager.getDefaultSharedPreferences(context)
            .unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    fun setDepth(depth: Int) {
        val clamped = depth.coerceIn(0, 100)
        if (currentDepth != clamped) {
            currentDepth = clamped
            invalidate()
        }
    }

    fun getDepth(): Int = currentDepth

    private fun trackBounds(w: Float, h: Float): Pair<Float, Float> {
        val topPad = h * 0.21f
        val bottomPad = h * 0.13f
        val trackTop = topPad
        val trackBottom = (h - bottomPad).coerceAtLeast(trackTop + 1f)
        return trackTop to trackBottom
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return false

        val (trackTop, trackBottom) = trackBounds(w, h)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                isDragging = true
                updateDepthFromY(event.y, trackTop, trackBottom)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateDepthFromY(event.y, trackTop, trackBottom)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateDepthFromY(touchY: Float, trackTop: Float, trackBottom: Float) {
        val span = (trackBottom - trackTop).coerceAtLeast(1f)
        val normalized = ((trackBottom - touchY) / span).coerceIn(0f, 1f)
        val raw = (normalized * 100f).roundToInt()
        // Snap detent at 0 (OFF) for authentic 3DS click-off feel
        val snapped = if (raw <= 5) 0 else raw

        if (snapped != currentDepth) {
            val crossedDetent = (currentDepth == 0 && snapped > 0) || (currentDepth > 0 && snapped == 0)
            currentDepth = snapped
            if (crossedDetent) {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            onDepthChanged?.invoke(currentDepth)
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val pad = 4f
        val housingRect = RectF(pad, pad, w - pad, h - pad)
        val cornerR = (w - pad * 2f) * 0.36f

        val (bgCol, strokeCol, textCol, thumbLight, thumbDark) = resolveThemeColors(ButtonThemeManager.currentStyle)

        // Outer housing drop shadow and body
        canvas.drawRoundRect(
            RectF(housingRect.left + 1.5f, housingRect.top + 3f, housingRect.right + 1.5f, housingRect.bottom + 3f),
            cornerR,
            cornerR,
            shadowPaint,
        )
        housingPaint.color = bgCol
        housingStrokePaint.color = strokeCol
        canvas.drawRoundRect(housingRect, cornerR, cornerR, housingPaint)
        canvas.drawRoundRect(housingRect, cornerR, cornerR, housingStrokePaint)

        // Top "3D" label and glowing emerald LED
        val ledCx = cx
        val ledCy = h * 0.085f
        val ledRadius = (w * 0.11f).coerceIn(4f, 10f)
        val is3dActive = currentDepth > 0

        if (is3dActive) {
            val intensity = (0.45f + (currentDepth / 100f) * 0.55f).coerceIn(0f, 1f)
            ledGlowPaint.shader = RadialGradient(
                ledCx,
                ledCy,
                ledRadius * 2.8f,
                Color.argb((210 * intensity).toInt(), 0, 230, 118),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(ledCx, ledCy, ledRadius * 2.8f, ledGlowPaint)
            ledGlowPaint.shader = null
            ledCorePaint.color = Color.parseColor("#00E676")
        } else {
            ledCorePaint.color = Color.parseColor("#475569")
        }
        canvas.drawCircle(ledCx, ledCy, ledRadius, ledCorePaint)

        labelPaint.textSize = (w * 0.24f).coerceIn(11f, 22f)
        labelPaint.color = if (is3dActive) Color.parseColor("#00E676") else textCol
        canvas.drawText("3D", cx, h * 0.165f, labelPaint)

        // Vertical recessed groove
        val (trackTop, trackBottom) = trackBounds(w, h)
        val grooveHalfW = (w * 0.11f).coerceIn(4f, 10f)
        val grooveRect = RectF(cx - grooveHalfW, trackTop, cx + grooveHalfW, trackBottom)
        canvas.drawRoundRect(grooveRect, grooveHalfW, grooveHalfW, groovePaint)

        // Active emerald fill from bottom (0) up to current thumb position
        val fraction = currentDepth / 100f
        val thumbCy = trackBottom - fraction * (trackBottom - trackTop)
        if (is3dActive) {
            fillBarPaint.shader = LinearGradient(
                cx,
                trackBottom,
                cx,
                thumbCy,
                Color.parseColor("#059669"),
                Color.parseColor("#00E676"),
                Shader.TileMode.CLAMP,
            )
            val fillRect = RectF(cx - grooveHalfW * 0.7f, thumbCy, cx + grooveHalfW * 0.7f, trackBottom)
            canvas.drawRoundRect(fillRect, grooveHalfW * 0.7f, grooveHalfW * 0.7f, fillBarPaint)
            fillBarPaint.shader = null
        }

        // Side depth tick marks + detent notch at 0 (OFF)
        tickPaint.color = strokeCol
        val tickLeft = cx - grooveHalfW - (w * 0.14f)
        val tickRight = cx - grooveHalfW - (w * 0.04f)
        for (step in 0..4) {
            val ty = trackBottom - (step / 4f) * (trackBottom - trackTop)
            val isZero = step == 0
            tickPaint.color = if (isZero) Color.parseColor("#94A3B8") else strokeCol
            tickPaint.strokeWidth = if (isZero) 3f else 1.8f
            canvas.drawLine(tickLeft, ty, tickRight, ty, tickPaint)
            canvas.drawLine(cx + grooveHalfW + (w * 0.04f), ty, cx + grooveHalfW + (w * 0.14f), ty, tickPaint)
        }

        // Bottom "OFF" detent legend
        labelPaint.textSize = (w * 0.18f).coerceIn(9f, 16f)
        labelPaint.color = if (!is3dActive) Color.parseColor("#CBD5E1") else Color.parseColor("#64748B")
        canvas.drawText(context.getString(com.storm3ds.emulator.R.string.off).uppercase(), cx, h - (h * 0.045f), labelPaint)

        // Metallic knurled thumb carriage
        val thumbHalfW = (w * 0.34f).coerceAtLeast(12f)
        val thumbHalfH = (h * 0.055f).coerceIn(9f, 18f)
        val thumbRect = RectF(cx - thumbHalfW, thumbCy - thumbHalfH, cx + thumbHalfW, thumbCy + thumbHalfH)
        val thumbR = thumbHalfH * 0.45f

        canvas.drawRoundRect(
            RectF(thumbRect.left + 1.5f, thumbRect.top + 3f, thumbRect.right + 1.5f, thumbRect.bottom + 3f),
            thumbR,
            thumbR,
            shadowPaint,
        )

        thumbPaint.shader = LinearGradient(
            thumbRect.left,
            thumbRect.top,
            thumbRect.left,
            thumbRect.bottom,
            thumbLight,
            thumbDark,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(thumbRect, thumbR, thumbR, thumbPaint)
        thumbPaint.shader = null

        thumbStrokePaint.color = when {
            isDragging && is3dActive -> Color.parseColor("#00E676")
            isDragging -> Color.parseColor("#38BDF8")
            else -> Color.parseColor("#99FFFFFF")
        }
        canvas.drawRoundRect(thumbRect, thumbR, thumbR, thumbStrokePaint)

        // Metallic knurl grip lines across thumb
        val knurlSpan = thumbHalfW * 0.62f
        val knurlStep = thumbHalfH * 0.38f
        knurlPaint.color = Color.parseColor("#800F172A")
        canvas.drawLine(cx - knurlSpan, thumbCy - knurlStep, cx + knurlSpan, thumbCy - knurlStep, knurlPaint)
        canvas.drawLine(cx - knurlSpan, thumbCy, cx + knurlSpan, thumbCy, knurlPaint)
        canvas.drawLine(cx - knurlSpan, thumbCy + knurlStep, cx + knurlSpan, thumbCy + knurlStep, knurlPaint)
    }

    private data class SliderTheme(
        val bg: Int,
        val stroke: Int,
        val text: Int,
        val thumbLight: Int,
        val thumbDark: Int,
    )

    private fun resolveThemeColors(style: ButtonColorStyle): SliderTheme {
        val p = ButtonThemeManager.paletteFor(style)
        return SliderTheme(
            bg = p.housingBg,
            stroke = p.housingStroke,
            text = p.labelText,
            thumbLight = p.thumbLight,
            thumbDark = p.thumbDark,
        )
    }
}
