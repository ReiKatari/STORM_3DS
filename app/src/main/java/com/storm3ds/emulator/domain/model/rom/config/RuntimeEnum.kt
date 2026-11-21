package com.storm3ds.emulator.domain.model.rom.config

interface RuntimeEnum<T, U> {
    fun getDefault(): T
    fun getValue(): U
}