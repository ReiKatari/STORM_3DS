package com.storm3ds.emulator.nativebridge.model

enum class GameMediaType(val value: Int) {
    UNKNOWN(0),
    GAME_CARD(1),
    SD_CARD(2),
    INTERNAL(3);

    companion object {
        fun fromInt(v: Int): GameMediaType? = entries.firstOrNull { it.value == v } ?: UNKNOWN
    }
}
