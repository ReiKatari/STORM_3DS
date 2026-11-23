// Copyright 2023-2025 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the misc/licenses/gplv2.txt file included.

package com.storm3ds.emulator.nativebridge.utils

object SystemSaveGame {
    const val DEFAULT_3DS_USERNAME = "STORM 3DS"
    const val MAX_USERNAME_UTF16_LEN = 10

    fun sanitizeUsername(raw: String?): String {
        val trimmed = raw.orEmpty().trim()
        if (trimmed.isEmpty()) return DEFAULT_3DS_USERNAME
        if (trimmed.length <= MAX_USERNAME_UTF16_LEN) return trimmed
        var cut = MAX_USERNAME_UTF16_LEN
        if (Character.isHighSurrogate(trimmed[cut - 1])) {
            cut -= 1
        }
        val clipped = trimmed.substring(0, cut).trimEnd()
        return clipped.ifEmpty { DEFAULT_3DS_USERNAME }
    }

    fun resolveEffectiveUsername(prefName: String?, nandName: String?): String {
        val prefTrimmed = prefName?.trim().orEmpty()
        if (prefTrimmed.isNotEmpty() && !prefTrimmed.equals("AZAHAR", ignoreCase = true)) {
            return sanitizeUsername(prefTrimmed)
        }
        val nandTrimmed = nandName?.trim().orEmpty()
        if (nandTrimmed.isEmpty() || nandTrimmed.equals("AZAHAR", ignoreCase = true)) {
            return DEFAULT_3DS_USERNAME
        }
        return sanitizeUsername(nandTrimmed)
    }

    external fun save()

    external fun load()

    external fun getIsSystemSetupNeeded(): Boolean

    external fun setSystemSetupNeeded(needed: Boolean)

    external fun getUsername(): String

    external fun setUsername(username: String)

    /**
     * Returns birthday as an array with the month as the first element and the
     * day as the second element
     */
    external fun getBirthday(): ShortArray

    external fun setBirthday(month: Short, day: Short)

    external fun getSystemLanguage(): Int

    external fun setSystemLanguage(language: Int)

    external fun getSoundOutputMode(): Int

    external fun setSoundOutputMode(mode: Int)

    external fun getCountryCode(): Short

    external fun setCountryCode(countryCode: Short)

    external fun getPlayCoins(): Int

    external fun setPlayCoins(coins: Int)

    external fun getConsoleId(): Long

    external fun regenerateConsoleId()

    external fun getMac(): String

    external fun regenerateMac()

    external fun getCountryCompatibility(region: Int): Int
}

enum class BirthdayMonth(val code: Int, val days: Int) {
    JANUARY(1, 31),
    FEBRUARY(2, 29),
    MARCH(3, 31),
    APRIL(4, 30),
    MAY(5, 31),
    JUNE(6, 30),
    JULY(7, 31),
    AUGUST(8, 31),
    SEPTEMBER(9, 30),
    OCTOBER(10, 31),
    NOVEMBER(11, 30),
    DECEMBER(12, 31);

    companion object {
        fun getMonthFromCode(code: Short): BirthdayMonth? =
            entries.firstOrNull { it.code == code.toInt() }
    }
}
