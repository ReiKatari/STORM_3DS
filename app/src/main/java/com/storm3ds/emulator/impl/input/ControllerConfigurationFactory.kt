package com.storm3ds.emulator.impl.input

import com.storm3ds.emulator.domain.model.ControllerConfiguration

interface ControllerConfigurationFactory {
    fun buildDefaultControllerConfiguration(): ControllerConfiguration
}