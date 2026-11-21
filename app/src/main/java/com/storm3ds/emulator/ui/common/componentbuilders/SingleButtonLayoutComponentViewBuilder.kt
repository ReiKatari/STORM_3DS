package com.storm3ds.emulator.ui.common.componentbuilders

import android.content.Context
import android.view.View
import com.storm3ds.emulator.domain.model.layout.LayoutComponent
import com.storm3ds.emulator.ui.common.LayoutComponentViewBuilder
import com.storm3ds.emulator.ui.common.views.ModernSingleButtonView

class SingleButtonLayoutComponentViewBuilder(private val layoutComponent: LayoutComponent) : LayoutComponentViewBuilder() {
    override fun build(context: Context): View {
        return ModernSingleButtonView(context, layoutComponent)
    }

    override fun getAspectRatio(): Float {
        return when (layoutComponent) {
            LayoutComponent.BUTTON_START, LayoutComponent.BUTTON_SELECT, LayoutComponent.BUTTON_HOME -> 1.8f
            LayoutComponent.BUTTON_L, LayoutComponent.BUTTON_R, LayoutComponent.BUTTON_ZL, LayoutComponent.BUTTON_ZR -> 1.25f
            LayoutComponent.SLIDER_3D_DEPTH -> 0.42f
            else -> 1.0f
        }
    }
}
