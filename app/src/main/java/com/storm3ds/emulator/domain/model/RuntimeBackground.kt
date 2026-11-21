package com.storm3ds.emulator.domain.model

import com.storm3ds.emulator.domain.model.layout.BackgroundMode

data class RuntimeBackground(val background: Background?, val mode: BackgroundMode) {

    companion object {
        val None = RuntimeBackground(null, BackgroundMode.STRETCH)
    }
}