package com.storm3ds.emulator.ui.common.componentbuilders

import android.content.Context
import android.view.View
import com.storm3ds.emulator.ui.common.LayoutComponentViewBuilder
import com.storm3ds.emulator.ui.common.views.Modern3dDepthSliderView

class Depth3dSliderLayoutComponentViewBuilder : LayoutComponentViewBuilder() {
    override fun build(context: Context): View = Modern3dDepthSliderView(context)
    override fun getAspectRatio() = 0.42f
}
