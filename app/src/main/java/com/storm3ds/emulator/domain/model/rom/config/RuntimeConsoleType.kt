package com.storm3ds.emulator.domain.model.rom.config

import com.storm3ds.emulator.domain.model.ConsoleType

enum class RuntimeConsoleType(val targetConsoleType: ConsoleType?) : RuntimeEnum<RuntimeConsoleType, ConsoleType> {
    DEFAULT(null),
    DS(ConsoleType.DS),
    DSi(ConsoleType.DSi);

    override fun getDefault() = DEFAULT
    override fun getValue() = targetConsoleType!!
}