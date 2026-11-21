package com.storm3ds.emulator.ui.common.componentbuilders

import android.content.Context
import android.view.View
import com.storm3ds.emulator.ui.common.LayoutComponentViewBuilder
import com.storm3ds.emulator.ui.common.views.ModernButtonsView

class ButtonsLayoutComponentViewBuilder : LayoutComponentViewBuilder() {
    override fun build(context: Context): View {
        val view = ModernButtonsView(context)
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        view.buttonSpread = prefs.getFloat("pref_button_cluster_spread", 1.0f)
        view.buttonInnerScale = prefs.getFloat("pref_button_cluster_inner_scale", 1.0f)
        return view
    }

    override fun getAspectRatio() = 1f
}