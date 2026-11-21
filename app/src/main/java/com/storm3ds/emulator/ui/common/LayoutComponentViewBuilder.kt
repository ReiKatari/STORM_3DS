package com.storm3ds.emulator.ui.common

import android.content.Context
import android.view.View

abstract class LayoutComponentViewBuilder {
    abstract fun build(context: Context): View
    abstract fun getAspectRatio(): Float
}