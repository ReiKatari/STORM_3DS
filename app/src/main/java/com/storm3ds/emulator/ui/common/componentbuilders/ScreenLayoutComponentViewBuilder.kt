package com.storm3ds.emulator.ui.common.componentbuilders

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import com.storm3ds.emulator.domain.model.ThreeDS
import com.storm3ds.emulator.ui.common.LayoutComponentViewBuilder

abstract class ScreenLayoutComponentViewBuilder : LayoutComponentViewBuilder() {
    override fun build(context: Context): View {
        return View(context).apply {
            background = getBackgroundDrawable(context)
            isClickable = true
            isFocusable = false
        }
    }

    override fun getAspectRatio() = ThreeDS.TOP_SCREEN_ASPECT_RATIO

    protected abstract fun getBackgroundDrawable(context: Context): Drawable?
}
