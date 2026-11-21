package com.storm3ds.emulator.ui.common.componentbuilders

import android.content.Context
import androidx.core.content.ContextCompat
import com.storm3ds.emulator.R
import com.storm3ds.emulator.domain.model.ThreeDS

class TopScreenLayoutComponentViewBuilder : ScreenLayoutComponentViewBuilder() {
    override fun getBackgroundDrawable(context: Context) =
        ContextCompat.getDrawable(context, R.drawable.background_top_screen)

    override fun getAspectRatio() = ThreeDS.TOP_SCREEN_ASPECT_RATIO
}
