package com.storm3ds.emulator.utils

import com.storm3ds.emulator.R
import com.storm3ds.emulator.domain.model.layout.LayoutComponent

fun getLayoutComponentName(layoutComponent: LayoutComponent): Int {
    return when (layoutComponent) {
        LayoutComponent.TOP_SCREEN -> R.string.top_screen
        LayoutComponent.BOTTOM_SCREEN -> R.string.bottom_screen
        LayoutComponent.HYBRID_SCREEN -> R.string.hybrid_screen
        LayoutComponent.DPAD -> R.string.input_dpad
        LayoutComponent.BUTTONS -> R.string.input_abxy_buttons
        LayoutComponent.BUTTON_L -> R.string.input_l
        LayoutComponent.BUTTON_R -> R.string.input_r
        LayoutComponent.BUTTON_START -> R.string.input_start
        LayoutComponent.BUTTON_SELECT -> R.string.input_select
        LayoutComponent.BUTTON_HINGE -> R.string.input_lid
        LayoutComponent.BUTTON_PAUSE -> R.string.input_pause
        LayoutComponent.BUTTON_FAST_FORWARD_TOGGLE -> R.string.input_fast_forward
        LayoutComponent.BUTTON_MICROPHONE_TOGGLE -> R.string.input_microphone
        LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT -> R.string.input_toggle_soft_input
        LayoutComponent.BUTTON_RESET -> R.string.reset
        LayoutComponent.BUTTON_SWAP_SCREENS -> R.string.input_swap_screens
        LayoutComponent.BUTTON_QUICK_SAVE -> R.string.input_quick_save
        LayoutComponent.BUTTON_QUICK_LOAD -> R.string.input_quick_load
        LayoutComponent.BUTTON_REWIND -> R.string.rewind
        LayoutComponent.BUTTON_TRANSLATE -> R.string.button_translate
        LayoutComponent.BUTTON_TOGGLE_EXTRA_BUTTONS -> R.string.input_toggle_extra_buttons
        LayoutComponent.BUTTON_LOCK_ROTATION -> R.string.input_lock_rotation
        LayoutComponent.BUTTON_TOGGLE_ANALOG_STICK -> R.string.input_toggle_analog_stick
        LayoutComponent.CIRCLE_PAD -> R.string.input_circle_pad
        LayoutComponent.C_STICK -> R.string.input_c_stick
        LayoutComponent.SLIDER_3D_DEPTH -> R.string.input_slider_3d_depth
        LayoutComponent.BUTTON_ZL -> R.string.input_zl
        LayoutComponent.BUTTON_ZR -> R.string.input_zr
        LayoutComponent.BUTTON_HOME -> R.string.input_home
        LayoutComponent.BUTTON_COMBO_1 -> R.string.input_combo_1
        LayoutComponent.BUTTON_COMBO_2 -> R.string.input_combo_2
    }
}
