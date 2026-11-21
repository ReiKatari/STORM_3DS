package com.storm3ds.emulator.ui.emulator.input.componentbuilder

import android.content.Context
import android.graphics.drawable.Drawable
import com.storm3ds.emulator.domain.model.ThreeDS
import com.storm3ds.emulator.ui.common.componentbuilders.ScreenLayoutComponentViewBuilder

class RuntimeScreenLayoutComponentViewBuilder(
    private val aspectRatio: Float = ThreeDS.TOP_SCREEN_ASPECT_RATIO,
) : ScreenLayoutComponentViewBuilder() {
    override fun getBackgroundDrawable(context: Context): Drawable? = null
    override fun getAspectRatio() = aspectRatio
}
