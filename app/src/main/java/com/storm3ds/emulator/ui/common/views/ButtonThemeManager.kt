package com.storm3ds.emulator.ui.common.views

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager


data class ButtonStylePalette(
    val housingBg: Int,
    val housingStroke: Int,
    val labelText: Int,
    val thumbLight: Int,
    val thumbDark: Int,
    val accentGlow: Int,
)

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


    fun paletteFor(style: ButtonColorStyle): ButtonStylePalette = when (style) {
        ButtonColorStyle.CYBER_CYAN -> ButtonStylePalette(0xE6151E2B.toInt(), 0x8000E5FF.toInt(), 0xFFA5F3FC.toInt(), 0xFF67E8F9.toInt(), 0xFF0E7490.toInt(), 0xFF00E5FF.toInt())
        ButtonColorStyle.CLASSIC_WHITE -> ButtonStylePalette(0xE6F1F5F9.toInt(), 0x8094A3B8.toInt(), 0xFF0F172A.toInt(), 0xFFFFFFFF.toInt(), 0xFFCBD5E1.toInt(), 0xFF0284C7.toInt())
        ButtonColorStyle.CLASSIC_GREY -> ButtonStylePalette(0xE6334155.toInt(), 0x8094A3B8.toInt(), 0xFFF1F5F9.toInt(), 0xFFCBD5E1.toInt(), 0xFF475569.toInt(), 0xFF94A3B8.toInt())
        ButtonColorStyle.SNES_SUPER -> ButtonStylePalette(0xE63A384A.toInt(), 0x80A855F7.toInt(), 0xFFE2E8F0.toInt(), 0xFFC084FC.toInt(), 0xFF6B21A8.toInt(), 0xFFA855F7.toInt())
        ButtonColorStyle.CRIMSON_RUBY -> ButtonStylePalette(0xE62D0608.toInt(), 0x80FF1744.toInt(), 0xFFFFCDD2.toInt(), 0xFFEF4444.toInt(), 0xFF7F1D1D.toInt(), 0xFFFF1744.toInt())
        ButtonColorStyle.MIDNIGHT_PURPLE -> ButtonStylePalette(0xE61F1738.toInt(), 0x80C084FC.toInt(), 0xFFE9D5FF.toInt(), 0xFFC084FC.toInt(), 0xFF581C87.toInt(), 0xFFC084FC.toInt())
        ButtonColorStyle.GOLD_LUXURY -> ButtonStylePalette(0xE62A200B.toInt(), 0x80FFD700.toInt(), 0xFFFEF08A.toInt(), 0xFFFACC15.toInt(), 0xFF854D0E.toInt(), 0xFFFFD700.toInt())
        ButtonColorStyle.EMERALD_MATRIX -> ButtonStylePalette(0xE6052614.toInt(), 0x8000E676.toInt(), 0xFFA7F3D0.toInt(), 0xFF34D399.toInt(), 0xFF065F46.toInt(), 0xFF00E676.toInt())
        ButtonColorStyle.WII_CRYSTAL -> ButtonStylePalette(0xE6E0F2FE.toInt(), 0x8038BDF8.toInt(), 0xFF0F172A.toInt(), 0xFFFFFFFF.toInt(), 0xFFBAE6FD.toInt(), 0xFF0284C7.toInt())
        ButtonColorStyle.WII_U_DARK -> ButtonStylePalette(0xE61E222B.toInt(), 0x8038BDF8.toInt(), 0xFFE0F2FE.toInt(), 0xFF38BDF8.toInt(), 0xFF1E293B.toInt(), 0xFF38BDF8.toInt())
        ButtonColorStyle.SWITCH_NEON -> ButtonStylePalette(0xE60F172A.toInt(), 0x80FF3B30.toInt(), 0xFFF8FAFC.toInt(), 0xFF38BDF8.toInt(), 0xFFFF3B30.toInt(), 0xFF0AB9E6.toInt())
        ButtonColorStyle.SWITCH_OLED -> ButtonStylePalette(0xE6F8FAFC.toInt(), 0x8064748B.toInt(), 0xFF0F172A.toInt(), 0xFFFFFFFF.toInt(), 0xFF94A3B8.toInt(), 0xFF334155.toInt())
        ButtonColorStyle.VIRTUAL_BOY -> ButtonStylePalette(0xE6180003.toInt(), 0x99FF0033.toInt(), 0xFFFF0033.toInt(), 0xFFFF0033.toInt(), 0xFF450009.toInt(), 0xFFFF0033.toInt())
        ButtonColorStyle.GAMECUBE_INDIGO -> ButtonStylePalette(0xE62A2952.toInt(), 0x80818CF8.toInt(), 0xFFE0E7FF.toInt(), 0xFFFACC15.toInt(), 0xFFA16207.toInt(), 0xFF6C5CE7.toInt())
        ButtonColorStyle.GAMEBOY_DMG -> ButtonStylePalette(0xE64A4A50.toInt(), 0x809D174D.toInt(), 0xFFF2C9D8.toInt(), 0xFFD1D5DB.toInt(), 0xFF6B7280.toInt(), 0xFFDB2777.toInt())
        ButtonColorStyle.GBA_GLACIER -> ButtonStylePalette(0xE63B5998.toInt(), 0x8093C5FD.toInt(), 0xFFDBEAFE.toInt(), 0xFF93C5FD.toInt(), 0xFF1E3A8A.toInt(), 0xFF60A5FA.toInt())
        ButtonColorStyle.FAMICOM_RETRO -> ButtonStylePalette(0xE65C1111.toInt(), 0x80DAA520.toInt(), 0xFFFFD700.toInt(), 0xFFFDE047.toInt(), 0xFF9A3412.toInt(), 0xFFFFD700.toInt())
        ButtonColorStyle.NINTENDO_3DS_AQUA -> ButtonStylePalette(0xE6004E64.toInt(), 0x8000FFFF.toInt(), 0xFFA5F3FC.toInt(), 0xFF22D3EE.toInt(), 0xFF155E75.toInt(), 0xFF00FFFF.toInt())
        ButtonColorStyle.STORM_ARCADE_RETRO -> ButtonStylePalette(0xE61C1917.toInt(), 0x80F59E0B.toInt(), 0xFFFEF3C7.toInt(), 0xFFF59E0B.toInt(), 0xFF78350F.toInt(), 0xFFF97316.toInt())
        ButtonColorStyle.STORM_AURORA_SPECTRUM -> ButtonStylePalette(0xE60F172A.toInt(), 0x802DD4BF.toInt(), 0xFFCCFBF1.toInt(), 0xFF2DD4BF.toInt(), 0xFF6D28D9.toInt(), 0xFF2DD4BF.toInt())
        ButtonColorStyle.STORM_CYBERPUNK_NEON -> ButtonStylePalette(0xE61A0B2E.toInt(), 0x80F472B6.toInt(), 0xFFFBCFE8.toInt(), 0xFFF472B6.toInt(), 0xFF4C1D95.toInt(), 0xFF00F5D4.toInt())
        ButtonColorStyle.STORM_FROST_GLACIER -> ButtonStylePalette(0xE60C2336.toInt(), 0x807DD3FC.toInt(), 0xFFE0F2FE.toInt(), 0xFFBAE6FD.toInt(), 0xFF0369A1.toInt(), 0xFF38BDF8.toInt())
        ButtonColorStyle.STORM_SAKURA_BLOSSOM -> ButtonStylePalette(0xE63B1325.toInt(), 0x80F9A8D4.toInt(), 0xFFFCE7F3.toInt(), 0xFFF9A8D4.toInt(), 0xFF9D174D.toInt(), 0xFFF472B6.toInt())
        ButtonColorStyle.STORM_SOLAR_FUSION -> ButtonStylePalette(0xE63B1406.toInt(), 0x80FB923C.toInt(), 0xFFFFEDD5.toInt(), 0xFFFBBF24.toInt(), 0xFF9A3412.toInt(), 0xFFFB923C.toInt())
        ButtonColorStyle.N3DS_SFC_COLORED -> ButtonStylePalette(0xE62B2E38.toInt(), 0x8094A3B8.toInt(), 0xFFF1F5F9.toInt(), 0xFF94A3B8.toInt(), 0xFF334155.toInt(), 0xFF38BDF8.toInt())
        ButtonColorStyle.STORM_CYBER_3D -> ButtonStylePalette(0xE60A1E24.toInt(), 0x8000F5D4.toInt(), 0xFFA5F3FC.toInt(), 0xFF67E8F9.toInt(), 0xFF0F766E.toInt(), 0xFF00F5D4.toInt())
        ButtonColorStyle.HYRULE_GOLD_3D -> ButtonStylePalette(0xE64A3306.toInt(), 0x8010B981.toInt(), 0xFFFDE68A.toInt(), 0xFFFCD34D.toInt(), 0xFF92400E.toInt(), 0xFF10B981.toInt())
        ButtonColorStyle.METROID_VISOR -> ButtonStylePalette(0xE6450A0A.toInt(), 0x80F59E0B.toInt(), 0xFFFDE68A.toInt(), 0xFFEF4444.toInt(), 0xFF7F1D1D.toInt(), 0xFFFBBF24.toInt())
        ButtonColorStyle.GALAXY_NEBULA -> ButtonStylePalette(0xE62E1065.toInt(), 0x80E879F9.toInt(), 0xFFE9D5FF.toInt(), 0xFFC084FC.toInt(), 0xFF4C1D95.toInt(), 0xFFF0ABFC.toInt())
        ButtonColorStyle.ANIMAL_LEAF -> ButtonStylePalette(0xE6A7F3D0.toInt(), 0x80059669.toInt(), 0xFF064E3B.toInt(), 0xFFFFFBEB.toInt(), 0xFF6EE7B7.toInt(), 0xFF10B981.toInt())
        ButtonColorStyle.MONSTER_HUNTER_FORGE -> ButtonStylePalette(0xE61F242D.toInt(), 0x80F97316.toInt(), 0xFFFED7AA.toInt(), 0xFF9CA3AF.toInt(), 0xFF374151.toInt(), 0xFFF97316.toInt())
        ButtonColorStyle.POKEMON_POKEBALL -> ButtonStylePalette(0xE63F0E12.toInt(), 0x99FFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFFEF4444.toInt(), 0xFF7F1D1D.toInt(), 0xFFEF4444.toInt())
        ButtonColorStyle.PERSONA_VELVET -> ButtonStylePalette(0xE617255A.toInt(), 0x80CBD5E1.toInt(), 0xFFF8FAFC.toInt(), 0xFF3B82F6.toInt(), 0xFF0F172A.toInt(), 0xFF60A5FA.toInt())
        ButtonColorStyle.CRYSTAL_PRISM_3D -> ButtonStylePalette(0xCC1E293B.toInt(), 0x80BAE6FD.toInt(), 0xFFE0F2FE.toInt(), 0xFF7DD3FC.toInt(), 0xFF0F172A.toInt(), 0xFF7DD3FC.toInt())
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
