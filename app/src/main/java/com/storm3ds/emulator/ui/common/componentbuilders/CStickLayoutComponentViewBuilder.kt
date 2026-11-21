package com.storm3ds.emulator.ui.common.componentbuilders

import android.content.Context
import android.view.View
import com.storm3ds.emulator.ui.common.LayoutComponentViewBuilder
import com.storm3ds.emulator.ui.common.views.ModernCStickView

class CStickLayoutComponentViewBuilder : LayoutComponentViewBuilder() {
    override fun build(context: Context): View = ModernCStickView(context)
    override fun getAspectRatio() = 1f
}
