package com.storm3ds.emulator.ui.common

import com.storm3ds.emulator.domain.model.layout.LayoutComponent

interface LayoutComponentViewBuilderFactory {
    fun getLayoutComponentViewBuilder(layoutComponent: LayoutComponent): LayoutComponentViewBuilder
}