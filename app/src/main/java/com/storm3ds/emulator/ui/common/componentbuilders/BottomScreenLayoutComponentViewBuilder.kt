package com.storm3ds.emulator.ui.common.componentbuilders

import android.content.Context
import androidx.core.content.ContextCompat
import com.storm3ds.emulator.R
import com.storm3ds.emulator.domain.model.ThreeDS

class BottomScreenLayoutComponentViewBuilder : ScreenLayoutComponentViewBuilder() {
    override fun getBackgroundDrawable(context: Context) =
        ContextCompat.getDrawable(context, R.drawable.background_bottom_screen)

    override fun getAspectRatio() = ThreeDS.BOTTOM_SCREEN_ASPECT_RATIO
}
