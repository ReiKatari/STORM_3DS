package com.storm3ds.emulator.ui.emulator

import com.storm3ds.emulator.domain.model.ThreeDS
import com.storm3ds.emulator.domain.model.layout.LayoutComponent
import com.storm3ds.emulator.ui.common.LayoutComponentViewBuilder
import com.storm3ds.emulator.ui.common.LayoutComponentViewBuilderFactory
import com.storm3ds.emulator.ui.common.componentbuilders.*
import com.storm3ds.emulator.ui.emulator.input.componentbuilder.RuntimeScreenLayoutComponentViewBuilder

class RuntimeLayoutComponentViewBuilderFactory : LayoutComponentViewBuilderFactory {
    private val layoutComponentViewBuilderCache = mutableMapOf<LayoutComponent, LayoutComponentViewBuilder>()

    override fun getLayoutComponentViewBuilder(layoutComponent: LayoutComponent): LayoutComponentViewBuilder {
        return layoutComponentViewBuilderCache.getOrElse(layoutComponent) {
            val builder = when (layoutComponent) {
                LayoutComponent.TOP_SCREEN -> RuntimeScreenLayoutComponentViewBuilder(ThreeDS.TOP_SCREEN_ASPECT_RATIO)
                LayoutComponent.BOTTOM_SCREEN -> RuntimeScreenLayoutComponentViewBuilder(ThreeDS.BOTTOM_SCREEN_ASPECT_RATIO)
                LayoutComponent.HYBRID_SCREEN -> RuntimeScreenLayoutComponentViewBuilder(ThreeDS.HYBRID_SCREEN_ASPECT_RATIO)
                LayoutComponent.DPAD -> DpadLayoutComponentViewBuilder()
                LayoutComponent.BUTTONS -> ButtonsLayoutComponentViewBuilder()
                LayoutComponent.CIRCLE_PAD -> CirclePadLayoutComponentViewBuilder()
                LayoutComponent.C_STICK -> CStickLayoutComponentViewBuilder()
                LayoutComponent.SLIDER_3D_DEPTH -> Depth3dSliderLayoutComponentViewBuilder()
                else -> SingleButtonLayoutComponentViewBuilder(layoutComponent)
            }

            layoutComponentViewBuilderCache[layoutComponent] = builder
            builder
        }
    }
}
