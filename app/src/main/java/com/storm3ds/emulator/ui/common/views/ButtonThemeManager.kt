package com.storm3ds.emulator.ui.common.views

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

enum class ButtonColorStyle {
    CYBER_CYAN,
    CLASSIC_WHITE,
    CLASSIC_GREY,
    SNES_SUPER,
    CRIMSON_RUBY,
    MIDNIGHT_PURPLE,
    GOLD_LUXURY,
    EMERALD_MATRIX,
    WII_CRYSTAL,
    WII_U_DARK,
    SWITCH_NEON,
    SWITCH_OLED,
    VIRTUAL_BOY,
    GAMECUBE_INDIGO,
    GAMEBOY_DMG,
    GBA_GLACIER,
    FAMICOM_RETRO,
    NINTENDO_3DS_AQUA,
    STORM_ARCADE_RETRO,
    STORM_AURORA_SPECTRUM,
    STORM_CYBERPUNK_NEON,
    STORM_FROST_GLACIER,
    STORM_SAKURA_BLOSSOM,
    STORM_SOLAR_FUSION,
    N3DS_SFC_COLORED,
    STORM_CYBER_3D,
    HYRULE_GOLD_3D,
    METROID_VISOR,
    GALAXY_NEBULA,
    ANIMAL_LEAF,
    MONSTER_HUNTER_FORGE,
    POKEMON_POKEBALL,
    PERSONA_VELVET,
    CRYSTAL_PRISM_3D
}

object ButtonThemeManager {
    var currentStyle: ButtonColorStyle = ButtonColorStyle.CYBER_CYAN
        private set

    private var registeredPrefs: SharedPreferences? = null
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { sp: SharedPreferences, key: String? ->
        if (key == "button_color_theme") {
            val newKey = sp.getString("button_color_theme", "cyber_cyan") ?: "cyber_cyan"
            currentStyle = parseStyle(newKey)
        }
    }

    fun init(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val styleKey = prefs.getString("button_color_theme", "cyber_cyan") ?: "cyber_cyan"
        currentStyle = parseStyle(styleKey)

        if (registeredPrefs !== prefs) {
            registeredPrefs?.unregisterOnSharedPreferenceChangeListener(prefListener)
            prefs.registerOnSharedPreferenceChangeListener(prefListener)
            registeredPrefs = prefs
        }
    }

    private fun parseStyle(key: String): ButtonColorStyle {
        return when (key) {
            "classic_white" -> ButtonColorStyle.CLASSIC_WHITE
            "classic_grey" -> ButtonColorStyle.CLASSIC_GREY
            "snes_super" -> ButtonColorStyle.SNES_SUPER
            "crimson_ruby" -> ButtonColorStyle.CRIMSON_RUBY
            "midnight_purple" -> ButtonColorStyle.MIDNIGHT_PURPLE
            "gold_luxury" -> ButtonColorStyle.GOLD_LUXURY
            "emerald_matrix" -> ButtonColorStyle.EMERALD_MATRIX
            "wii_crystal" -> ButtonColorStyle.WII_CRYSTAL
            "wii_u_dark" -> ButtonColorStyle.WII_U_DARK
            "switch_neon" -> ButtonColorStyle.SWITCH_NEON
            "switch_oled" -> ButtonColorStyle.SWITCH_OLED
            "virtual_boy" -> ButtonColorStyle.VIRTUAL_BOY
            "gamecube_indigo" -> ButtonColorStyle.GAMECUBE_INDIGO
            "gameboy_dmg" -> ButtonColorStyle.GAMEBOY_DMG
            "gba_glacier" -> ButtonColorStyle.GBA_GLACIER
            "famicom_retro" -> ButtonColorStyle.FAMICOM_RETRO
            "3ds_aqua" -> ButtonColorStyle.NINTENDO_3DS_AQUA
            "arcade_retro" -> ButtonColorStyle.STORM_ARCADE_RETRO
            "aurora_spectrum" -> ButtonColorStyle.STORM_AURORA_SPECTRUM
            "cyberpunk_neon" -> ButtonColorStyle.STORM_CYBERPUNK_NEON
            "frost_glacier" -> ButtonColorStyle.STORM_FROST_GLACIER
            "sakura_blossom" -> ButtonColorStyle.STORM_SAKURA_BLOSSOM
            "solar_fusion" -> ButtonColorStyle.STORM_SOLAR_FUSION
            "n3ds_sfc_colored" -> ButtonColorStyle.N3DS_SFC_COLORED
            "storm_cyber_3d" -> ButtonColorStyle.STORM_CYBER_3D
            "hyrule_gold_3d" -> ButtonColorStyle.HYRULE_GOLD_3D
            "metroid_visor" -> ButtonColorStyle.METROID_VISOR
            "galaxy_nebula" -> ButtonColorStyle.GALAXY_NEBULA
            "animal_leaf" -> ButtonColorStyle.ANIMAL_LEAF
            "mh_forge" -> ButtonColorStyle.MONSTER_HUNTER_FORGE
            "pokemon_pokeball" -> ButtonColorStyle.POKEMON_POKEBALL
            "persona_velvet" -> ButtonColorStyle.PERSONA_VELVET
            "crystal_prism_3d" -> ButtonColorStyle.CRYSTAL_PRISM_3D
            else -> ButtonColorStyle.CYBER_CYAN
        }
    }
}
