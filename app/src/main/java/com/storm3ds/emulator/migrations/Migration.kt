package com.storm3ds.emulator.migrations

interface Migration {
    val from: Int
    val to: Int

    fun migrate()
}