package com.storm3ds.emulator.domain.model

object ThreeDS {
    const val TOP_SCREEN_WIDTH = 400
    const val TOP_SCREEN_HEIGHT = 240
    const val BOTTOM_SCREEN_WIDTH = 320
    const val BOTTOM_SCREEN_HEIGHT = 240

    const val TOP_SCREEN_ASPECT_RATIO = TOP_SCREEN_WIDTH.toFloat() / TOP_SCREEN_HEIGHT.toFloat() // 5:3 (1.6667f)
    const val BOTTOM_SCREEN_ASPECT_RATIO = BOTTOM_SCREEN_WIDTH.toFloat() / BOTTOM_SCREEN_HEIGHT.toFloat() // 4:3 (1.3333f)
    const val HYBRID_SCREEN_ASPECT_RATIO = TOP_SCREEN_WIDTH.toFloat() / (TOP_SCREEN_HEIGHT + BOTTOM_SCREEN_HEIGHT).toFloat()

    val NATIVE_3DS_EXTENSIONS = setOf(
        "3ds", "cci", "cxi", "app", "cia", "3dsx", "elf", "axf",
        "zcci", "zcxi", "zcia", "z3dsx", "bcci", "bcxi", "bcia", "artic"
    )

    val ARCHIVE_EXTENSIONS = setOf("zip", "7z", "rar")

    val ALL_SUPPORTED_EXTENSIONS = NATIVE_3DS_EXTENSIONS + ARCHIVE_EXTENSIONS

    fun isSupportedExtension(ext: String): Boolean =
        ext.lowercase().trim().removePrefix(".") in ALL_SUPPORTED_EXTENSIONS

    fun isNative3dsExtension(ext: String): Boolean =
        ext.lowercase().trim().removePrefix(".") in NATIVE_3DS_EXTENSIONS
}
