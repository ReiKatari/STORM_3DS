package com.storm3ds.emulator.ui.common.componentbuilders

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.storm3ds.emulator.R
import com.storm3ds.emulator.domain.model.ThreeDS
import com.storm3ds.emulator.ui.common.LayoutComponentViewBuilder

class HybridScreenLayoutComponentViewBuilder : LayoutComponentViewBuilder() {
    override fun build(context: Context): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(buildScreenHalf(context, R.drawable.background_top_screen), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ))
            addView(buildScreenHalf(context, R.drawable.background_bottom_screen), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ))
        }
    }

    override fun getAspectRatio() = ThreeDS.HYBRID_SCREEN_ASPECT_RATIO

    private fun buildScreenHalf(context: Context, backgroundRes: Int): View {
        return View(context).apply {
            background = ContextCompat.getDrawable(context, backgroundRes)
        }
    }
}
