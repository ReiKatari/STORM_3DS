package com.storm3ds.emulator.ui.layouteditor

import com.storm3ds.emulator.domain.model.layout.LayoutComponent
import com.storm3ds.emulator.ui.common.LayoutComponentViewBuilder
import com.storm3ds.emulator.ui.common.LayoutComponentViewBuilderFactory
import com.storm3ds.emulator.ui.common.componentbuilders.*

class EditorLayoutComponentViewBuilderFactory : LayoutComponentViewBuilderFactory {
    private val layoutComponentViewBuilderCache = mutableMapOf<LayoutComponent, LayoutComponentViewBuilder>()

    override fun getLayoutComponentViewBuilder(layoutComponent: LayoutComponent): LayoutComponentViewBuilder {
        return layoutComponentViewBuilderCache.getOrElse(layoutComponent) {
            val builder = when (layoutComponent) {
                LayoutComponent.TOP_SCREEN -> TopScreenLayoutComponentViewBuilder()
                LayoutComponent.BOTTOM_SCREEN -> BottomScreenLayoutComponentViewBuilder()
                LayoutComponent.HYBRID_SCREEN -> HybridScreenLayoutComponentViewBuilder()
                LayoutComponent.DPAD -> EditorBackgroundLayoutComponentViewBuilder(DpadLayoutComponentViewBuilder())
                LayoutComponent.BUTTONS -> EditorBackgroundLayoutComponentViewBuilder(ButtonsLayoutComponentViewBuilder())
                LayoutComponent.CIRCLE_PAD -> EditorBackgroundLayoutComponentViewBuilder(CirclePadLayoutComponentViewBuilder())
                LayoutComponent.C_STICK -> EditorBackgroundLayoutComponentViewBuilder(CStickLayoutComponentViewBuilder())
                LayoutComponent.SLIDER_3D_DEPTH -> EditorBackgroundLayoutComponentViewBuilder(Depth3dSliderLayoutComponentViewBuilder())
                else -> EditorBackgroundLayoutComponentViewBuilder(SingleButtonLayoutComponentViewBuilder(layoutComponent))
            }

            layoutComponentViewBuilderCache[layoutComponent] = builder
            builder
        }
    }
}
