package com.storm3ds.emulator.ui.emulator.skin

import android.content.SharedPreferences
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.preference.PreferenceManager
import com.storm3ds.emulator.domain.model.Rect
import com.storm3ds.emulator.ui.theme.AppThemeManager
import com.storm3ds.emulator.ui.theme.WatermelonColors

enum class ConsoleSkinTheme {
    // Flagship STORM AURORA FLUID Series
    AURORA_AUTO_THEME,
    AURORA_MIDNIGHT,
    AURORA_CYBERPUNK,
    AURORA_IMPERIAL_GOLD,

    // Classic Nintendo 3DS Series
    CLASSIC_3DS_AQUA_BLUE,
    CLASSIC_3DS_COSMO_BLACK,
    CLASSIC_3DS_FLARE_RED,
    CLASSIC_3DS_PEARL_PINK,
    CLASSIC_3DS_ICE_WHITE,
    CLASSIC_3DS_MIDNIGHT_PURPLE,

    // Nintendo 3DS XL Series
    THREEDS_XL_RED_BLACK,
    THREEDS_XL_BLUE_BLACK,
    THREEDS_XL_SILVER_MARIO,
    THREEDS_XL_NES_RETRO,

    // New Nintendo 3DS / New 3DS XL Series
    NEW_3DS_MATTE_BLACK,
    NEW_3DS_PEARL_WHITE,
    NEW_3DS_XL_METALLIC_BLACK,
    NEW_3DS_XL_METALLIC_BLUE,
    NEW_3DS_XL_GALAXY,
    NEW_3DS_XL_SNES,
    NEW_3DS_XL_HYRULE_GOLD,
    NEW_3DS_XL_MAJORAS_MASK,
    NEW_3DS_XL_SAMUS_METROID,
    NEW_3DS_XL_PIKACHU,

    // Nintendo 2DS / New 2DS XL & STORM Pro Series
    TWODS_SLAB_CRIMSON,
    NEW_2DS_XL_BLACK_TURQUOISE,
    NEW_2DS_XL_WHITE_ORANGE,
    NEW_2DS_XL_POKEBALL,
    NEW_2DS_XL_HYLIAN_SHIELD,
    STORM_3DS_OBSIDIAN_PRO;

    companion object {
        fun fromKey(key: String?): ConsoleSkinTheme {
            return when (key?.lowercase()?.trim()) {
                // Aurora Series
                "aurora_auto_theme" -> AURORA_AUTO_THEME
                "aurora_midnight" -> AURORA_MIDNIGHT
                "aurora_cyberpunk" -> AURORA_CYBERPUNK
                "aurora_imperial_gold" -> AURORA_IMPERIAL_GOLD

                // Classic 3DS Series
                "classic_3ds_aqua_blue", "n3ds_aqua", "ds_lite_ice_blue" -> CLASSIC_3DS_AQUA_BLUE
                "classic_3ds_cosmo_black", "ds_lite_black", "dsi_xl_black" -> CLASSIC_3DS_COSMO_BLACK
                "classic_3ds_flare_red", "crimson_red", "dsi_xl_burgundy" -> CLASSIC_3DS_FLARE_RED
                "classic_3ds_pearl_pink", "ds_lite_coral_pink" -> CLASSIC_3DS_PEARL_PINK
                "classic_3ds_ice_white", "ds_lite_white", "dsi_white" -> CLASSIC_3DS_ICE_WHITE
                "classic_3ds_midnight_purple" -> CLASSIC_3DS_MIDNIGHT_PURPLE

                // 3DS XL Series
                "threeds_xl_red_black", "3ds_xl_red_black" -> THREEDS_XL_RED_BLACK
                "threeds_xl_blue_black", "3ds_xl_blue_black", "dsi_xl_blue" -> THREEDS_XL_BLUE_BLACK
                "threeds_xl_silver_mario", "3ds_xl_silver_mario", "ds_lite_silver" -> THREEDS_XL_SILVER_MARIO
                "threeds_xl_nes_retro", "3ds_xl_nes_retro" -> THREEDS_XL_NES_RETRO

                // New 3DS / New 3DS XL Series
                "new_3ds_matte_black" -> NEW_3DS_MATTE_BLACK
                "new_3ds_pearl_white" -> NEW_3DS_PEARL_WHITE
                "new_3ds_xl_metallic_black", "n3ds_black" -> NEW_3DS_XL_METALLIC_BLACK
                "new_3ds_xl_metallic_blue" -> NEW_3DS_XL_METALLIC_BLUE
                "new_3ds_xl_galaxy", "n3ds_galaxy" -> NEW_3DS_XL_GALAXY
                "new_3ds_xl_snes", "n3ds_snes" -> NEW_3DS_XL_SNES
                "new_3ds_xl_hyrule_gold", "n3ds_hyrule" -> NEW_3DS_XL_HYRULE_GOLD
                "new_3ds_xl_majoras_mask", "n3ds_majoras_mask" -> NEW_3DS_XL_MAJORAS_MASK
                "new_3ds_xl_samus_metroid" -> NEW_3DS_XL_SAMUS_METROID
                "new_3ds_xl_pikachu", "n3ds_pikachu" -> NEW_3DS_XL_PIKACHU

                // 2DS / New 2DS XL & STORM Pro Series
                "twods_slab_crimson", "2ds_slab_crimson" -> TWODS_SLAB_CRIMSON
                "new_2ds_xl_black_turquoise", "switch_neon", "switch_lite_turquoise" -> NEW_2DS_XL_BLACK_TURQUOISE
                "new_2ds_xl_white_orange", "switch_oled_white" -> NEW_2DS_XL_WHITE_ORANGE
                "new_2ds_xl_pokeball" -> NEW_2DS_XL_POKEBALL
                "new_2ds_xl_hylian_shield", "switch_totk_oled" -> NEW_2DS_XL_HYLIAN_SHIELD
                "storm_3ds_obsidian_pro", "switch_pro_gray" -> STORM_3DS_OBSIDIAN_PRO

                else -> AURORA_AUTO_THEME
            }
        }
    }
}

private enum class HardwareFormFactor {
    AURORA,
    CLASSIC_3DS,
    THREEDS_XL,
    NEW_3DS,
    TWODS_SLAB,
    NEW_2DS_XL,
    STORM_PRO
}

private enum class ShellFinish {
    AURORA,
    GLOSS,
    MATTE,
    BRUSHED,
    CARBON
}

private data class ConsoleSkinPalette(
    val primaryBody: Color,
    val secondaryBody: Color,
    val upperLidBody: Color = primaryBody,
    val upperLidShade: Color = secondaryBody,
    val upperInnerGlass: Color = Color(0xFF0A0C10),
    val bezelAccent: Color,
    val innerBevelDark: Color,
    val innerBevelLight: Color,
    val brandTitle: String,
    val formFactor: HardwareFormFactor,
    val finish: ShellFinish,
    val isLightSkin: Boolean = false,
    val accentGlow: Color = bezelAccent,
    val trimAccent: Color = bezelAccent,
    val brandTextColor: Color = if (isLightSkin) Color(0xFF334155) else Color(0xFFE2E8F0),
    val hingeShadow: Color = if (isLightSkin) Color(0x55475569) else Color(0xCC000000),
    val screenRimColor: Color = Color(0xFF0A0B0E),
    val controlsColor: Color = if (isLightSkin) Color(0xFFCBD5E1) else Color(0xFF232732),
    val has3DSlider: Boolean = true,
    val hasFaceTrackingIr: Boolean = false,
    val hasClamshellHinge: Boolean = true,
    val powerLedColor: Color = Color(0xFF00B0FF),
    val hasTwoToneDeck: Boolean = false,
    val hasColoredAbxyHint: Boolean = false,
)

private fun resolveSkinPalette(
    theme: ConsoleSkinTheme,
    appColors: WatermelonColors,
): ConsoleSkinPalette {
    return when (theme) {
        ConsoleSkinTheme.AURORA_AUTO_THEME -> {
            val isLight = appColors.bg.luminance() > 0.5f
            ConsoleSkinPalette(
                primaryBody = appColors.bg,
                secondaryBody = appColors.surface,
                upperLidBody = appColors.surface,
                upperLidShade = appColors.bg,
                upperInnerGlass = if (isLight) appColors.surface2 else Color(0xFF080B10),
                bezelAccent = appColors.line,
                innerBevelDark = if (isLight) Color(0x3364748B) else Color(0xCC000000),
                innerBevelLight = if (isLight) Color(0xDDFFFFFF) else appColors.green.copy(alpha = 0.55f),
                brandTitle = "STORM 3DS AURORA",
                formFactor = HardwareFormFactor.AURORA,
                finish = ShellFinish.AURORA,
                isLightSkin = isLight,
                accentGlow = appColors.green,
                trimAccent = appColors.green,
                brandTextColor = if (isLight) appColors.text.copy(alpha = 0.86f) else appColors.text.copy(alpha = 0.92f),
                hingeShadow = if (isLight) Color(0x3364748B) else Color(0xCC000000),
                screenRimColor = if (isLight) appColors.line else Color(0xFF090B10),
                controlsColor = appColors.surface2,
                hasFaceTrackingIr = true,
                powerLedColor = appColors.green,
            )
        }
        ConsoleSkinTheme.AURORA_MIDNIGHT -> ConsoleSkinPalette(
            primaryBody = Color(0xFF0B091A),
            secondaryBody = Color(0xFF161230),
            upperInnerGlass = Color(0xFF070512),
            bezelAccent = Color(0xFF5B3EA8),
            innerBevelDark = Color(0xDD000000),
            innerBevelLight = Color(0x7700F0FF),
            brandTitle = "STORM 3DS AURORA",
            formFactor = HardwareFormFactor.AURORA,
            finish = ShellFinish.AURORA,
            accentGlow = Color(0xFF00E5FF),
            trimAccent = Color(0xFFA855F7),
            brandTextColor = Color(0xFFE0E7FF),
            controlsColor = Color(0xFF1E1638),
            hasFaceTrackingIr = true,
            powerLedColor = Color(0xFF00E5FF),
        )
        ConsoleSkinTheme.AURORA_CYBERPUNK -> ConsoleSkinPalette(
            primaryBody = Color(0xFF130A26),
            secondaryBody = Color(0xFF21113E),
            upperInnerGlass = Color(0xFF0B0516),
            bezelAccent = Color(0xFFD90368),
            innerBevelDark = Color(0xDD000000),
            innerBevelLight = Color(0x77FF2A8A),
            brandTitle = "STORM 3DS CYBER",
            formFactor = HardwareFormFactor.AURORA,
            finish = ShellFinish.AURORA,
            accentGlow = Color(0xFFFF007F),
            trimAccent = Color(0xFF22D3EE),
            brandTextColor = Color(0xFFFCE7F3),
            controlsColor = Color(0xFF2E134D),
            hasFaceTrackingIr = true,
            powerLedColor = Color(0xFF22D3EE),
        )
        ConsoleSkinTheme.AURORA_IMPERIAL_GOLD -> ConsoleSkinPalette(
            primaryBody = Color(0xFF111217),
            secondaryBody = Color(0xFF1B1C24),
            upperInnerGlass = Color(0xFF090A0D),
            bezelAccent = Color(0xFFB8942E),
            innerBevelDark = Color(0xDD000000),
            innerBevelLight = Color(0x77F5C754),
            brandTitle = "STORM 3DS IMPERIAL",
            formFactor = HardwareFormFactor.AURORA,
            finish = ShellFinish.AURORA,
            accentGlow = Color(0xFFD4AF37),
            trimAccent = Color(0xFFF5C754),
            brandTextColor = Color(0xFFFEF3C7),
            controlsColor = Color(0xFF262730),
            hasFaceTrackingIr = true,
            powerLedColor = Color(0xFF10B981),
        )

        // Classic Nintendo 3DS Series
        ConsoleSkinTheme.CLASSIC_3DS_AQUA_BLUE -> ConsoleSkinPalette(
            primaryBody = Color(0xFF026C9A),
            secondaryBody = Color(0xFF0389C2),
            upperInnerGlass = Color(0xFF090D12),
            bezelAccent = Color(0xFF38BDF8),
            innerBevelDark = Color(0xBB032638),
            innerBevelLight = Color(0x88BAE6FD),
            brandTitle = "Nintendo 3DS",
            formFactor = HardwareFormFactor.CLASSIC_3DS,
            finish = ShellFinish.GLOSS,
            accentGlow = Color(0xFF10B981),
            trimAccent = Color(0xFF38BDF8),
            brandTextColor = Color(0xFFE0F2FE),
            controlsColor = Color(0xFF064E6E),
        )
        ConsoleSkinTheme.CLASSIC_3DS_COSMO_BLACK -> ConsoleSkinPalette(
            primaryBody = Color(0xFF151820),
            secondaryBody = Color(0xFF222733),
            upperInnerGlass = Color(0xFF07080B),
            bezelAccent = Color(0xFF475569),
            innerBevelDark = Color(0xDD000000),
            innerBevelLight = Color(0x66CBD5E1),
            brandTitle = "Nintendo 3DS",
            formFactor = HardwareFormFactor.CLASSIC_3DS,
            finish = ShellFinish.GLOSS,
            accentGlow = Color(0xFF10B981),
            trimAccent = Color(0xFF64748B),
            brandTextColor = Color(0xFFF1F5F9),
            controlsColor = Color(0xFF1E222B),
        )
        ConsoleSkinTheme.CLASSIC_3DS_FLARE_RED -> ConsoleSkinPalette(
            primaryBody = Color(0xFF991B1B),
            secondaryBody = Color(0xFFC81E1E),
            upperInnerGlass = Color(0xFF0C090A),
            bezelAccent = Color(0xFFEF4444),
            innerBevelDark = Color(0xBB450A0A),
            innerBevelLight = Color(0x66FCA5A5),
            brandTitle = "Nintendo 3DS",
            formFactor = HardwareFormFactor.CLASSIC_3DS,
            finish = ShellFinish.GLOSS,
            accentGlow = Color(0xFF10B981),
            trimAccent = Color(0xFFF87171),
            brandTextColor = Color(0xFFFEE2E2),
            controlsColor = Color(0xFF7F1D1D),
        )
        ConsoleSkinTheme.CLASSIC_3DS_PEARL_PINK -> ConsoleSkinPalette(
            primaryBody = Color(0xFFF4B6D2),
            secondaryBody = Color(0xFFFAD4E6),
            upperInnerGlass = Color(0xFF1B1419),
            bezelAccent = Color(0xFFEC4899),
            innerBevelDark = Color(0x559D174D),
            innerBevelLight = Color(0xEEFFFFFF),
            brandTitle = "Nintendo 3DS",
            formFactor = HardwareFormFactor.CLASSIC_3DS,
            finish = ShellFinish.GLOSS,
            isLightSkin = true,
            accentGlow = Color(0xFF10B981),
            trimAccent = Color(0xFFF472B6),
            brandTextColor = Color(0xFF831843),
            screenRimColor = Color(0xFF9D174D),
            controlsColor = Color(0xFFFBCFE8),
        )
        ConsoleSkinTheme.CLASSIC_3DS_ICE_WHITE -> ConsoleSkinPalette(
            primaryBody = Color(0xFFECEFF4),
            secondaryBody = Color(0xFFF8FAFC),
            upperInnerGlass = Color(0xFF111318),
            bezelAccent = Color(0xFFCBD5E1),
            innerBevelDark = Color(0x4464748B),
            innerBevelLight = Color(0xEEFFFFFF),
            brandTitle = "Nintendo 3DS",
            formFactor = HardwareFormFactor.CLASSIC_3DS,
            finish = ShellFinish.GLOSS,
            isLightSkin = true,
            accentGlow = Color(0xFF10B981),
            trimAccent = Color(0xFF94A3B8),
            brandTextColor = Color(0xFF334155),
            screenRimColor = Color(0xFF64748B),
            controlsColor = Color(0xFFE2E8F0),
        )
        ConsoleSkinTheme.CLASSIC_3DS_MIDNIGHT_PURPLE -> ConsoleSkinPalette(
            primaryBody = Color(0xFF3B1261),
            secondaryBody = Color(0xFF531A88),
            upperInnerGlass = Color(0xFF0A0612),
            bezelAccent = Color(0xFF9333EA),
            innerBevelDark = Color(0xBB1E0735),
            innerBevelLight = Color(0x66D8B4FE),
            brandTitle = "Nintendo 3DS",
            formFactor = HardwareFormFactor.CLASSIC_3DS,
            finish = ShellFinish.GLOSS,
            accentGlow = Color(0xFF10B981),
            trimAccent = Color(0xFFA855F7),
            brandTextColor = Color(0xFFF3E8FF),
            controlsColor = Color(0xFF2E104D),
        )

        // Nintendo 3DS XL Series
        ConsoleSkinTheme.THREEDS_XL_RED_BLACK -> ConsoleSkinPalette(
            primaryBody = Color(0xFF1E2026),
            secondaryBody = Color(0xFF2B2E36),
            upperLidBody = Color(0xFFA61B24),
            upperLidShade = Color(0xFF7D1118),
            upperInnerGlass = Color(0xFF111216),
            bezelAccent = Color(0xFFDC2626),
            innerBevelDark = Color(0xCC000000),
            innerBevelLight = Color(0x55FCA5A5),
            brandTitle = "Nintendo 3DS XL",
            formFactor = HardwareFormFactor.THREEDS_XL,
            finish = ShellFinish.MATTE,
            accentGlow = Color(0xFF10B981),
            trimAccent = Color(0xFFEF4444),
            brandTextColor = Color(0xFFFEE2E2),
            controlsColor = Color(0xFF272A32),
            hasTwoToneDeck = true,
        )
        ConsoleSkinTheme.THREEDS_XL_BLUE_BLACK -> ConsoleSkinPalette(
            primaryBody = Color(0xFF1D2028),
            secondaryBody = Color(0xFF2A2E38),
            upperLidBody = Color(0xFF15458F),
            upperLidShade = Color(0xFF0F3166),
            upperInnerGlass = Color(0xFF101217),
            bezelAccent = Color(0xFF3B82F6),
            innerBevelDark = Color(0xCC000000),
            innerBevelLight = Color(0x5593C5FD),
            brandTitle = "Nintendo 3DS XL",
            formFactor = HardwareFormFactor.THREEDS_XL,
            finish = ShellFinish.MATTE,
            accentGlow = Color(0xFF10B981),
            trimAccent = Color(0xFF60A5FA),
            brandTextColor = Color(0xFFDBEAFE),
            controlsColor = Color(0xFF252A34),
            hasTwoToneDeck = true,
        )
        ConsoleSkinTheme.THREEDS_XL_SILVER_MARIO -> ConsoleSkinPalette(
            primaryBody = Color(0xFFBFC5CE),
            secondaryBody = Color(0xFFD5DAE2),
            upperLidBody = Color(0xFFCBD2DC),
            upperLidShade = Color(0xFFA8B0BC),
            upperInnerGlass = Color(0xFF1B1E24),
            bezelAccent = Color(0xFFDC2626),
            innerBevelDark = Color(0x66334155),
            innerBevelLight = Color(0xEEFFFFFF),
            brandTitle = "3DS XL Mario Silver",
            formFactor = HardwareFormFactor.THREEDS_XL,
            finish = ShellFinish.BRUSHED,
            isLightSkin = true,
            accentGlow = Color(0xFF10B981),
            trimAccent = Color(0xFFDC2626),
            brandTextColor = Color(0xFF1E293B),
            screenRimColor = Color(0xFF475569),
            controlsColor = Color(0xFFE2E8F0),
        )
        ConsoleSkinTheme.THREEDS_XL_NES_RETRO -> ConsoleSkinPalette(
            primaryBody = Color(0xFFD5D7DC),
            secondaryBody = Color(0xFFB6BAC3),
            upperLidBody = Color(0xFF26272B),
            upperLidShade = Color(0xFF1A1B1E),
            upperInnerGlass = Color(0xFF141518),
            bezelAccent = Color(0xFFDC2626),
            innerBevelDark = Color(0x771F2937),
            innerBevelLight = Color(0xDDFFFFFF),
            brandTitle = "3DS XL NES Edition",
            formFactor = HardwareFormFactor.THREEDS_XL,
            finish = ShellFinish.MATTE,
            isLightSkin = true,
            accentGlow = Color(0xFF10B981),
            trimAccent = Color(0xFFDC2626),
            brandTextColor = Color(0xFFF3F4F6),
            screenRimColor = Color(0xFF374151),
            controlsColor = Color(0xFF27272A),
            hasTwoToneDeck = true,
        )

        // New Nintendo 3DS / New 3DS XL Series
        ConsoleSkinTheme.NEW_3DS_MATTE_BLACK -> ConsoleSkinPalette(
            primaryBody = Color(0xFF1B1D24),
            secondaryBody = Color(0xFF272A34),
            upperInnerGlass = Color(0xFF0A0B0E),
            bezelAccent = Color(0xFF475569),
            innerBevelDark = Color(0xDD000000),
            innerBevelLight = Color(0x5594A3B8),
            brandTitle = "New Nintendo 3DS",
            formFactor = HardwareFormFactor.NEW_3DS,
            finish = ShellFinish.MATTE,
            accentGlow = Color(0xFF10B981),
            trimAccent = Color(0xFF38BDF8),
            brandTextColor = Color(0xFFE2E8F0),
            controlsColor = Color(0xFF222630),
            hasFaceTrackingIr = true,
            hasColoredAbxyHint = true,
        )
        ConsoleSkinTheme.NEW_3DS_PEARL_WHITE -> ConsoleSkinPalette(
            primaryBody = Color(0xFFF1F5F9),
            secondaryBody = Color(0xFFE2E8F0),
            upperInnerGlass = Color(0xFF161920),
            bezelAccent = Color(0xFFCBD5E1),
            innerBevelDark = Color(0x4464748B),
            innerBevelLight = Color(0xEEFFFFFF),
            brandTitle = "New Nintendo 3DS",
            formFactor = HardwareFormFactor.NEW_3DS,
            finish = ShellFinish.MATTE,
            isLightSkin = true,
            accentGlow = Color(0xFF10B981),
            trimAccent = Color(0xFF60A5FA),
            brandTextColor = Color(0xFF334155),
            screenRimColor = Color(0xFF94A3B8),
            controlsColor = Color(0xFFF8FAFC),
            hasFaceTrackingIr = true,
            hasColoredAbxyHint = true,
        )
        ConsoleSkinTheme.NEW_3DS_XL_METALLIC_BLACK -> ConsoleSkinPalette(
            primaryBody = Color(0xFF1F232C),
            secondaryBody = Color(0xFF323846),
            upperInnerGlass = Color(0xFF090B0E),
            bezelAccent = Color(0xFF64748B),
            innerBevelDark = Color(0xCC000000),
            innerBevelLight = Color(0x77CBD5E1),
            brandTitle = "New Nintendo 3DS XL",
            formFactor = HardwareFormFactor.NEW_3DS,
            finish = ShellFinish.BRUSHED,
            accentGlow = Color(0xFF10B981),
            trimAccent = Color(0xFF94A3B8),
            brandTextColor = Color(0xFFF1F5F9),
            controlsColor = Color(0xFF282D38),
            hasFaceTrackingIr = true,
            hasColoredAbxyHint = true,
        )
        ConsoleSkinTheme.NEW_3DS_XL_METALLIC_BLUE -> ConsoleSkinPalette(
            primaryBody = Color(0xFF0F2D6B),
            secondaryBody = Color(0xFF1B459C),
            upperInnerGlass = Color(0xFF070C18),
            bezelAccent = Color(0xFF3B82F6),
            innerBevelDark = Color(0xBB061433),
            innerBevelLight = Color(0x7793C5FD),
            brandTitle = "New Nintendo 3DS XL",
            formFactor = HardwareFormFactor.NEW_3DS,
            finish = ShellFinish.BRUSHED,
            accentGlow = Color(0xFF10B981),
            trimAccent = Color(0xFF60A5FA),
            brandTextColor = Color(0xFFDBEAFE),
            controlsColor = Color(0xFF173A80),
            hasFaceTrackingIr = true,
            hasColoredAbxyHint = true,
        )
        ConsoleSkinTheme.NEW_3DS_XL_GALAXY -> ConsoleSkinPalette(
            primaryBody = Color(0xFF250747),
            secondaryBody = Color(0xFF421272),
            upperLidBody = Color(0xFF1D0D4A),
            upperLidShade = Color(0xFF4A187D),
            upperInnerGlass = Color(0xFF0A0414),
            bezelAccent = Color(0xFF9333EA),
            innerBevelDark = Color(0xBB130226),
            innerBevelLight = Color(0x77C084FC),
            brandTitle = "New 3DS XL Galaxy",
            formFactor = HardwareFormFactor.NEW_3DS,
            finish = ShellFinish.GLOSS,
            accentGlow = Color(0xFF10B981),
            trimAccent = Color(0xFFC084FC),
            brandTextColor = Color(0xFFF3E8FF),
            controlsColor = Color(0xFF2A0950),
            hasFaceTrackingIr = true,
            hasColoredAbxyHint = true,
        )
        ConsoleSkinTheme.NEW_3DS_XL_SNES -> ConsoleSkinPalette(
            primaryBody = Color(0xFFD0D4DC),
            secondaryBody = Color(0xFFB3B8C4),
            upperInnerGlass = Color(0xFF1B1D22),
            bezelAccent = Color(0xFF6B7280),
            innerBevelDark = Color(0x55374151),
            innerBevelLight = Color(0xEEFFFFFF),
            brandTitle = "New 3DS XL SNES",
            formFactor = HardwareFormFactor.NEW_3DS,
            finish = ShellFinish.MATTE,
            isLightSkin = true,
            accentGlow = Color(0xFF10B981),
            trimAccent = Color(0xFF6366F1),
            brandTextColor = Color(0xFF1F2937),
            screenRimColor = Color(0xFF4B5563),
            controlsColor = Color(0xFFE5E7EB),
            hasFaceTrackingIr = true,
            hasColoredAbxyHint = true,
        )
        ConsoleSkinTheme.NEW_3DS_XL_HYRULE_GOLD -> ConsoleSkinPalette(
            primaryBody = Color(0xFF8A580B),
            secondaryBody = Color(0xFFB87914),
            upperInnerGlass = Color(0xFF130D04),
            bezelAccent = Color(0xFFEAB308),
            innerBevelDark = Color(0xAA451A03),
            innerBevelLight = Color(0x77FDE68A),
            brandTitle = "New 3DS XL Hyrule",
            formFactor = HardwareFormFactor.NEW_3DS,
            finish = ShellFinish.BRUSHED,
            accentGlow = Color(0xFF10B981),
            trimAccent = Color(0xFFFACC15),
            brandTextColor = Color(0xFFFEF3C7),
            controlsColor = Color(0xFF6E4306),
            hasFaceTrackingIr = true,
            hasColoredAbxyHint = true,
        )
        ConsoleSkinTheme.NEW_3DS_XL_MAJORAS_MASK -> ConsoleSkinPalette(
            primaryBody = Color(0xFF96640B),
            secondaryBody = Color(0xFFC48B18),
            upperInnerGlass = Color(0xFF130B1C),
            bezelAccent = Color(0xFF7C3AED),
            innerBevelDark = Color(0xBB3B1D05),
            innerBevelLight = Color(0x88FDE68A),
            brandTitle = "New 3DS XL Majora",
            formFactor = HardwareFormFactor.NEW_3DS,
            finish = ShellFinish.GLOSS,
            accentGlow = Color(0xFF10B981),
            trimAccent = Color(0xFFA855F7),
            brandTextColor = Color(0xFFFEF3C7),
            screenRimColor = Color(0xFF140E1F),
            controlsColor = Color(0xFF2E1065),
            hasFaceTrackingIr = true,
            hasColoredAbxyHint = true,
        )
        ConsoleSkinTheme.NEW_3DS_XL_SAMUS_METROID -> ConsoleSkinPalette(
            primaryBody = Color(0xFF7F1616),
            secondaryBody = Color(0xFFAA2418),
            upperLidBody = Color(0xFFD97706),
            upperLidShade = Color(0xFF9A1B1B),
            upperInnerGlass = Color(0xFF140707),
            bezelAccent = Color(0xFFF59E0B),
            innerBevelDark = Color(0xCC450A0A),
            innerBevelLight = Color(0x77FCD34D),
            brandTitle = "New 3DS XL Samus",
            formFactor = HardwareFormFactor.NEW_3DS,
            finish = ShellFinish.BRUSHED,
            accentGlow = Color(0xFF10B981),
            trimAccent = Color(0xFFF59E0B),
            brandTextColor = Color(0xFFFEF3C7),
            controlsColor = Color(0xFF5C1111),
            hasFaceTrackingIr = true,
            hasTwoToneDeck = true,
        )
        ConsoleSkinTheme.NEW_3DS_XL_PIKACHU -> ConsoleSkinPalette(
            primaryBody = Color(0xFFFACC15),
            secondaryBody = Color(0xFFEAB308),
            upperInnerGlass = Color(0xFF1A1606),
            bezelAccent = Color(0xFFEF4444),
            innerBevelDark = Color(0x88713F12),
            innerBevelLight = Color(0xDDFEF9C3),
            brandTitle = "New 3DS XL Pikachu",
            formFactor = HardwareFormFactor.NEW_3DS,
            finish = ShellFinish.GLOSS,
            isLightSkin = true,
            accentGlow = Color(0xFF10B981),
            trimAccent = Color(0xFFEF4444),
            brandTextColor = Color(0xFF422006),
            screenRimColor = Color(0xFF1F2937),
            controlsColor = Color(0xFFFEF08A),
            hasFaceTrackingIr = true,
            hasColoredAbxyHint = true,
        )

        // Nintendo 2DS / New 2DS XL & STORM Pro Series
        ConsoleSkinTheme.TWODS_SLAB_CRIMSON -> ConsoleSkinPalette(
            primaryBody = Color(0xFF1B1D23),
            secondaryBody = Color(0xFF282B34),
            upperLidBody = Color(0xFF9F1226),
            upperLidShade = Color(0xFF7A0D1C),
            upperInnerGlass = Color(0xFF101116),
            bezelAccent = Color(0xFFE11D48),
            innerBevelDark = Color(0xCC000000),
            innerBevelLight = Color(0x55FDA4AF),
            brandTitle = "Nintendo 2DS",
            formFactor = HardwareFormFactor.TWODS_SLAB,
            finish = ShellFinish.MATTE,
            accentGlow = Color(0xFF10B981),
            trimAccent = Color(0xFFF43F5E),
            brandTextColor = Color(0xFFFFE4E6),
            controlsColor = Color(0xFF9F1226),
            has3DSlider = false,
            hasClamshellHinge = false,
            powerLedColor = Color(0xFF00B0FF),
        )
        ConsoleSkinTheme.NEW_2DS_XL_BLACK_TURQUOISE -> ConsoleSkinPalette(
            primaryBody = Color(0xFF181A20),
            secondaryBody = Color(0xFF232730),
            upperInnerGlass = Color(0xFF08090C),
            bezelAccent = Color(0xFF06B6D4),
            innerBevelDark = Color(0xDD000000),
            innerBevelLight = Color(0x7722D3EE),
            brandTitle = "New Nintendo 2DS XL",
            formFactor = HardwareFormFactor.NEW_2DS_XL,
            finish = ShellFinish.MATTE,
            accentGlow = Color(0xFF10B981),
            trimAccent = Color(0xFF06B6D4),
            brandTextColor = Color(0xFFCFFAFE),
            controlsColor = Color(0xFF0E7490),
            has3DSlider = false,
            powerLedColor = Color(0xFF06B6D4),
        )
        ConsoleSkinTheme.NEW_2DS_XL_WHITE_ORANGE -> ConsoleSkinPalette(
            primaryBody = Color(0xFFF3F5F8),
            secondaryBody = Color(0xFFE2E6ED),
            upperInnerGlass = Color(0xFF16181D),
            bezelAccent = Color(0xFFF97316),
            innerBevelDark = Color(0x4464748B),
            innerBevelLight = Color(0xEEFFFFFF),
            brandTitle = "New Nintendo 2DS XL",
            formFactor = HardwareFormFactor.NEW_2DS_XL,
            finish = ShellFinish.MATTE,
            isLightSkin = true,
            accentGlow = Color(0xFF10B981),
            trimAccent = Color(0xFFF97316),
            brandTextColor = Color(0xFF334155),
            screenRimColor = Color(0xFF94A3B8),
            controlsColor = Color(0xFFFB923C),
            has3DSlider = false,
            powerLedColor = Color(0xFF10B981),
        )
        ConsoleSkinTheme.NEW_2DS_XL_POKEBALL -> ConsoleSkinPalette(
            primaryBody = Color(0xFFF1F5F9),
            secondaryBody = Color(0xFFE2E8F0),
            upperLidBody = Color(0xFFDC2626),
            upperLidShade = Color(0xFF991B1B),
            upperInnerGlass = Color(0xFF121318),
            bezelAccent = Color(0xFFDC2626),
            innerBevelDark = Color(0x661F2937),
            innerBevelLight = Color(0xEEFFFFFF),
            brandTitle = "New 2DS XL Pokeball",
            formFactor = HardwareFormFactor.NEW_2DS_XL,
            finish = ShellFinish.GLOSS,
            isLightSkin = true,
            accentGlow = Color(0xFF10B981),
            trimAccent = Color(0xFFDC2626),
            brandTextColor = Color(0xFFFEE2E2),
            screenRimColor = Color(0xFF1E293B),
            controlsColor = Color(0xFF1E293B),
            has3DSlider = false,
            hasTwoToneDeck = true,
            powerLedColor = Color(0xFF10B981),
        )
        ConsoleSkinTheme.NEW_2DS_XL_HYLIAN_SHIELD -> ConsoleSkinPalette(
            primaryBody = Color(0xFF143570),
            secondaryBody = Color(0xFF1E4996),
            upperInnerGlass = Color(0xFF091224),
            bezelAccent = Color(0xFFD4AF37),
            innerBevelDark = Color(0xCC061024),
            innerBevelLight = Color(0x88FDE68A),
            brandTitle = "New 2DS XL Hylian",
            formFactor = HardwareFormFactor.NEW_2DS_XL,
            finish = ShellFinish.BRUSHED,
            accentGlow = Color(0xFF10B981),
            trimAccent = Color(0xFFEAB308),
            brandTextColor = Color(0xFFFEF3C7),
            screenRimColor = Color(0xFF9CA3AF),
            controlsColor = Color(0xFF1E3A8A),
            has3DSlider = false,
            powerLedColor = Color(0xFF2DD4BF),
        )
        ConsoleSkinTheme.STORM_3DS_OBSIDIAN_PRO -> ConsoleSkinPalette(
            primaryBody = Color(0xFF0E1015),
            secondaryBody = Color(0xFF181C24),
            upperInnerGlass = Color(0xFF06070A),
            bezelAccent = Color(0xFF38BDF8),
            innerBevelDark = Color(0xDD000000),
            innerBevelLight = Color(0x7738BDF8),
            brandTitle = "STORM 3DS OBSIDIAN PRO",
            formFactor = HardwareFormFactor.STORM_PRO,
            finish = ShellFinish.CARBON,
            accentGlow = Color(0xFF10B981),
            trimAccent = Color(0xFF38BDF8),
            brandTextColor = Color(0xFFE0F2FE),
            controlsColor = Color(0xFF1E2430),
            hasFaceTrackingIr = true,
            hasColoredAbxyHint = true,
            powerLedColor = Color(0xFF10B981),
        )
    }
}

@Composable
fun ConsoleSkinFullFrame(
    skinTheme: ConsoleSkinTheme = ConsoleSkinTheme.CLASSIC_3DS_COSMO_BLACK,
    topScreenRect: Rect? = null,
    bottomScreenRect: Rect? = null,
    modifier: Modifier = Modifier,
    overrideTheme: Boolean = false,
    brightnessOverride: Int? = null,
    hybridTopScreenRect: Rect? = null,
    hybridBottomScreenRect: Rect? = null,
    surfaceWidth: Int = 0,
    surfaceHeight: Int = 0,
) {
    val context = LocalContext.current
    val prefs = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    var activeSkinKey by remember {
        mutableStateOf(prefs.getString("video_console_skin_type", null))
    }
    var savedBrightness by remember {
        mutableStateOf(prefs.getInt("video_console_skin_brightness", 100).coerceIn(30, 100))
    }

    if (!overrideTheme || brightnessOverride == null) {
        DisposableEffect(prefs) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                when (key) {
                    "video_console_skin_type" -> activeSkinKey = sp.getString(key, null)
                    "video_console_skin_brightness" -> savedBrightness = sp.getInt(key, 100).coerceIn(30, 100)
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }
    }

    val activeTheme = remember(skinTheme, activeSkinKey, overrideTheme) {
        if (overrideTheme) {
            skinTheme
        } else if (activeSkinKey != null) {
            ConsoleSkinTheme.fromKey(activeSkinKey)
        } else {
            skinTheme
        }
    }

    val effectiveBrightness = (brightnessOverride ?: savedBrightness).coerceIn(30, 100)

    val ledAlpha = 0.94f
    val wifiAlpha = 0.78f
    val threeDAlpha = 0.92f

    // Observe theme and accent state so AURORA_AUTO_THEME follows app themes live
    val currentAppTheme = AppThemeManager.themeState.value
    val currentAccentKey = AppThemeManager.accentColorState.value
    val appColors = remember(currentAppTheme, currentAccentKey) {
        AppThemeManager.getCurrentWatermelonColors(context)
    }

    val palette = remember(activeTheme, appColors) {
        resolveSkinPalette(activeTheme, appColors)
    }

    val density = LocalDensity.current

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        if (w <= 1f || h <= 1f) return@Canvas

        val scaleX = if (surfaceWidth > 0 && kotlin.math.abs(surfaceWidth - w) > 2f) (w / surfaceWidth.toFloat()) else 1f
        val scaleY = if (surfaceHeight > 0 && kotlin.math.abs(surfaceHeight - h) > 2f) (h / surfaceHeight.toFloat()) else 1f

        fun mapRect(r: Rect?): Rect? {
            if (r == null || r.width <= 0 || r.height <= 0) return null
            if (scaleX == 1f && scaleY == 1f) return r
            return Rect(
                x = (r.x * scaleX).toInt(),
                y = (r.y * scaleY).toInt(),
                width = (r.width * scaleX).toInt().coerceAtLeast(1),
                height = (r.height * scaleY).toInt().coerceAtLeast(1),
            )
        }

        val s1 = mapRect(topScreenRect)
        val s2 = mapRect(bottomScreenRect)
        val h1 = mapRect(hybridTopScreenRect)
        val h2 = mapRect(hybridBottomScreenRect)

        val allScreens = listOfNotNull(s1, s2, h1, h2)
        if (allScreens.isEmpty()) return@Canvas

        // Screen Cutout Path guarantees zero pixel occlusion across all screens
        val screenCutoutPath = Path().apply {
            for (scr in allScreens) {
                addRect(
                    androidx.compose.ui.geometry.Rect(
                        scr.x.toFloat(), scr.y.toFloat(), scr.right.toFloat(), scr.bottom.toFloat()
                    )
                )
            }
        }

        val primaryA = s1 ?: h1 ?: s2 ?: h2
        val primaryB = when {
            s1 != null && s2 != null -> s2
            h1 != null && h2 != null && primaryA == h1 -> h2
            primaryA != s2 && s2 != null -> s2
            primaryA != h1 && h1 != null -> h1
            primaryA != h2 && h2 != null -> h2
            else -> null
        }
        val isDualScreen = primaryA != null && primaryB != null
        val isVertical = if (isDualScreen) {
            val a = primaryA!!
            val b = primaryB!!
            val stackedVertically = minOf(a.bottom, b.bottom) <= maxOf(a.y, b.y) + 24
            val sideBySideHorizontally = minOf(a.right, b.right) <= maxOf(a.x, b.x) + 24
            when {
                sideBySideHorizontally && !stackedVertically -> false
                stackedVertically && !sideBySideHorizontally -> true
                else -> w <= h
            }
        } else {
            w <= h
        }

        val outerBezelPx = 3.0f * density.density

        clipPath(screenCutoutPath, clipOp = ClipOp.Difference) {
            if (isDualScreen && !isVertical) {
                drawLandscapeLayout(
                    s1 = primaryA!!,
                    s2 = primaryB!!,
                    w = w,
                    h = h,
                    palette = palette,
                    ledAlpha = ledAlpha,
                    wifiAlpha = wifiAlpha,
                    threeDAlpha = threeDAlpha,
                    outerBezelPx = outerBezelPx,
                    density = density.density,
                )
            } else if (isDualScreen && isVertical) {
                drawPortraitLayout(
                    s1 = primaryA!!,
                    s2 = primaryB!!,
                    w = w,
                    h = h,
                    palette = palette,
                    ledAlpha = ledAlpha,
                    wifiAlpha = wifiAlpha,
                    threeDAlpha = threeDAlpha,
                    outerBezelPx = outerBezelPx,
                    density = density.density,
                )
            } else {
                drawSingleScreenLayout(
                    single = primaryA!!,
                    w = w,
                    h = h,
                    palette = palette,
                    ledAlpha = ledAlpha,
                    wifiAlpha = wifiAlpha,
                    threeDAlpha = threeDAlpha,
                    outerBezelPx = outerBezelPx,
                    density = density.density,
                )
            }

            for (scr in allScreens) {
                drawScreenWellBezel(scr, palette, outerBezelPx, density.density)
            }

            val dimAlpha = (1f - (effectiveBrightness / 100f)).coerceIn(0f, 0.70f)
            if (dimAlpha > 0.005f) {
                drawRect(color = Color.Black.copy(alpha = dimAlpha))
            }
        }
    }
}

private fun DrawScope.drawShellFinishShader(
    topLeft: Offset,
    size: Size,
    palette: ConsoleSkinPalette,
    density: Float,
    isUpperLid: Boolean = false,
) {
    if (size.width <= 0f || size.height <= 0f) return
    val baseStart = if (isUpperLid) palette.upperLidShade else palette.secondaryBody
    val baseEnd = if (isUpperLid) palette.upperLidBody else palette.primaryBody

    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(baseStart, baseEnd, baseStart),
            startY = topLeft.y,
            endY = topLeft.y + size.height,
        ),
        topLeft = topLeft,
        size = size,
    )

    when (palette.finish) {
        ShellFinish.AURORA -> {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.accentGlow.copy(alpha = 0.16f),
                        palette.trimAccent.copy(alpha = 0.06f),
                        Color.Transparent,
                    ),
                    center = Offset(topLeft.x + size.width * 0.5f, topLeft.y + size.height * 0.25f),
                    radius = maxOf(size.width, size.height) * 0.72f,
                ),
                topLeft = topLeft,
                size = size,
            )
        }
        ShellFinish.GLOSS -> {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (palette.isLightSkin) 0.34f else 0.14f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.16f),
                    ),
                    start = topLeft,
                    end = Offset(topLeft.x + size.width * 0.85f, topLeft.y + size.height * 0.75f),
                ),
                topLeft = topLeft,
                size = size,
            )
        }
        ShellFinish.BRUSHED -> {
            val step = (6.5f * density).coerceAtLeast(4f)
            val lineColor = if (palette.isLightSkin) {
                Color.White.copy(alpha = 0.14f)
            } else {
                Color.White.copy(alpha = 0.045f)
            }
            var y = topLeft.y + step
            while (y < topLeft.y + size.height) {
                drawLine(
                    color = lineColor,
                    start = Offset(topLeft.x, y),
                    end = Offset(topLeft.x + size.width, y),
                    strokeWidth = 0.65f * density,
                )
                y += step
            }
        }
        ShellFinish.CARBON -> {
            val step = (9f * density).coerceAtLeast(6f)
            val weave = Color.White.copy(alpha = 0.045f)
            var x = topLeft.x - size.height
            while (x < topLeft.x + size.width + size.height) {
                drawLine(
                    color = weave,
                    start = Offset(x, topLeft.y),
                    end = Offset(x + size.height * 0.5f, topLeft.y + size.height),
                    strokeWidth = 0.8f * density,
                )
                x += step
            }
        }
        ShellFinish.MATTE -> {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (palette.isLightSkin) 0.12f else 0.04f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.12f),
                    ),
                    startY = topLeft.y,
                    endY = topLeft.y + size.height,
                ),
                topLeft = topLeft,
                size = size,
            )
        }
    }
}

private fun DrawScope.drawLandscapeLayout(
    s1: Rect,
    s2: Rect,
    w: Float,
    h: Float,
    palette: ConsoleSkinPalette,
    ledAlpha: Float,
    wifiAlpha: Float,
    threeDAlpha: Float,
    outerBezelPx: Float,
    density: Float,
) {
    val leftScreen = if (s1.x <= s2.x) s1 else s2
    val rightScreen = if (s1.x <= s2.x) s2 else s1

    val minScreenLeft = leftScreen.x.toFloat()
    val maxScreenRight = rightScreen.right.toFloat()
    val minScreenTop = minOf(leftScreen.y, rightScreen.y).toFloat()
    val maxScreenBottom = maxOf(leftScreen.bottom, rightScreen.bottom).toFloat()

    val d36dp = 36f * density
    val d8dp = 8f * density

    // Main Chassis Base with active shader finish
    drawShellFinishShader(Offset.Zero, Size(w, h), palette, density, isUpperLid = false)

    // Top Band
    val safeTopH = (minScreenTop - outerBezelPx - 1f * density).coerceAtLeast(0f)
    if (minScreenTop > 0f) {
        drawLine(
            color = palette.innerBevelLight.copy(alpha = 0.45f),
            start = Offset(0f, 0.5f * density),
            end = Offset(w, 0.5f * density),
            strokeWidth = 1f * density,
        )

        if (safeTopH >= 13f * density) {
            val availW = (maxScreenRight - minScreenLeft).coerceAtLeast(w * 0.50f) - 28f * density
            drawBrandText(
                text = palette.brandTitle,
                centerX = w / 2f,
                centerY = safeTopH * 0.50f,
                maxHeightPx = safeTopH * 0.68f,
                maxWidthPx = availW,
                palette = palette,
                density = density,
            )
            if (safeTopH >= 20f * density) {
                drawCameraLens(
                    center = Offset(minScreenLeft + 16f * density, safeTopH * 0.50f),
                    radiusPx = (2.4f * density).coerceAtMost(safeTopH * 0.25f),
                    palette = palette,
                    density = density,
                    includeIrEmitter = palette.hasFaceTrackingIr,
                )
                draw3DIndicatorBadge(
                    rightEdgeX = maxScreenRight - 6f * density,
                    centerY = safeTopH * 0.50f,
                    maxHeightPx = safeTopH * 0.72f,
                    palette = palette,
                    alpha = threeDAlpha,
                    density = density,
                )
            }
        }
    }

    // Bottom Band with SELECT | HOME | START pill bar
    val safeBottomH = (h - maxScreenBottom - outerBezelPx).coerceAtLeast(0f)
    if (maxScreenBottom < h && safeBottomH >= 14f * density) {
        val barCenterY = maxScreenBottom + outerBezelPx + safeBottomH * 0.50f
        drawSelectHomeStartBar(
            centerX = w / 2f,
            centerY = barCenterY,
            maxWidth = (maxScreenRight - minScreenLeft).coerceAtLeast(110f * density),
            maxHeight = safeBottomH * 0.72f,
            palette = palette,
            density = density,
        )
    }

    // Left Side Wing
    val leftWingWidth = minScreenLeft
    if (leftWingWidth >= d36dp) {
        drawLandscapeSideWing(
            isLeft = true,
            wingX = 0f,
            wingW = leftWingWidth,
            h = h,
            palette = palette,
            ledAlpha = ledAlpha,
            wifiAlpha = wifiAlpha,
            threeDAlpha = threeDAlpha,
            density = density,
        )
    } else if (leftWingWidth >= d8dp) {
        drawLandscapeSlimRail(
            isLeft = true,
            railX = 0f,
            railW = leftWingWidth,
            h = h,
            palette = palette,
            ledAlpha = ledAlpha,
            density = density,
        )
    }

    // Right Side Wing
    val rightWingWidth = w - maxScreenRight
    if (rightWingWidth >= d36dp) {
        drawLandscapeSideWing(
            isLeft = false,
            wingX = maxScreenRight,
            wingW = rightWingWidth,
            h = h,
            palette = palette,
            ledAlpha = ledAlpha,
            wifiAlpha = wifiAlpha,
            threeDAlpha = threeDAlpha,
            density = density,
        )
    } else if (rightWingWidth >= d8dp) {
        drawLandscapeSlimRail(
            isLeft = false,
            railX = maxScreenRight,
            railW = rightWingWidth,
            h = h,
            palette = palette,
            ledAlpha = ledAlpha,
            density = density,
        )
    }

    // Central Clamshell Hinge Bridge Between 5:3 Top & 4:3 Touch Screens
    val horizontalGap = rightScreen.x - leftScreen.right
    if (horizontalGap >= 4f * density) {
        val bridgeX = leftScreen.right.toFloat()
        val bridgeW = horizontalGap.toFloat()
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(palette.secondaryBody, palette.primaryBody, palette.secondaryBody),
                startX = bridgeX,
                endX = bridgeX + bridgeW,
            ),
            topLeft = Offset(bridgeX, minScreenTop),
            size = Size(bridgeW, maxScreenBottom - minScreenTop),
        )
        drawLine(
            color = palette.hingeShadow.copy(alpha = 0.65f),
            start = Offset(bridgeX + bridgeW / 2f, minScreenTop),
            end = Offset(bridgeX + bridgeW / 2f, maxScreenBottom),
            strokeWidth = 1.2f * density,
        )
        if (bridgeW >= 10f * density) {
            drawGlowingLed(
                center = Offset(bridgeX + bridgeW / 2f, maxScreenBottom - 12f * density),
                baseColor = palette.powerLedColor,
                alpha = ledAlpha,
                density = density,
                radiusDp = 1.7f,
                hasHalo = true,
            )
        }
    }

    // Clean Void Compensation for Asymmetric 5:3 + 4:3 Screens
    if (leftScreen.y > minScreenTop.toInt()) {
        drawChassisDeckPanel(
            left = leftScreen.x.toFloat(),
            top = minScreenTop,
            width = leftScreen.width.toFloat(),
            height = leftScreen.y - minScreenTop,
            palette = palette,
        )
    }
    if (leftScreen.bottom < maxScreenBottom.toInt()) {
        drawChassisDeckPanel(
            left = leftScreen.x.toFloat(),
            top = leftScreen.bottom.toFloat(),
            width = leftScreen.width.toFloat(),
            height = maxScreenBottom - leftScreen.bottom,
            palette = palette,
        )
    }
    if (rightScreen.y > minScreenTop.toInt()) {
        drawChassisDeckPanel(
            left = rightScreen.x.toFloat(),
            top = minScreenTop,
            width = rightScreen.width.toFloat(),
            height = rightScreen.y - minScreenTop,
            palette = palette,
        )
    }
    if (rightScreen.bottom < maxScreenBottom.toInt()) {
        drawChassisDeckPanel(
            left = rightScreen.x.toFloat(),
            top = rightScreen.bottom.toFloat(),
            width = rightScreen.width.toFloat(),
            height = maxScreenBottom - rightScreen.bottom,
            palette = palette,
        )
    }
}

private fun DrawScope.drawLandscapeSideWing(
    isLeft: Boolean,
    wingX: Float,
    wingW: Float,
    h: Float,
    palette: ConsoleSkinPalette,
    ledAlpha: Float,
    wifiAlpha: Float,
    threeDAlpha: Float,
    density: Float,
) {
    val radius = (16f * density).coerceAtMost(wingW * 0.38f)

    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = if (isLeft) {
                listOf(palette.upperLidBody, palette.upperLidShade)
            } else {
                listOf(palette.secondaryBody, palette.primaryBody)
            },
            startX = wingX,
            endX = wingX + wingW,
        ),
        topLeft = Offset(wingX, 0f),
        size = Size(wingW, h),
        cornerRadius = CornerRadius(radius, radius),
    )

    val seamW = 3.5f * density
    if (isLeft) {
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, palette.hingeShadow),
                startX = wingX + wingW - seamW,
                endX = wingX + wingW,
            ),
            topLeft = Offset(wingX + wingW - seamW, 0f),
            size = Size(seamW, h),
        )
    } else {
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(palette.hingeShadow, Color.Transparent),
                startX = wingX,
                endX = wingX + seamW,
            ),
            topLeft = Offset(wingX, 0f),
            size = Size(seamW, h),
        )
    }

    val centerX = wingX + wingW * 0.50f
    if (palette.formFactor == HardwareFormFactor.AURORA || palette.formFactor == HardwareFormFactor.STORM_PRO) {
        val trimX = if (isLeft) wingX + 3.5f * density else wingX + wingW - 3.5f * density
        drawLine(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    palette.trimAccent.copy(alpha = 0.72f),
                    Color.Transparent,
                ),
                startY = h * 0.12f,
                endY = h * 0.88f,
            ),
            start = Offset(trimX, h * 0.12f),
            end = Offset(trimX, h * 0.88f),
            strokeWidth = 1.6f * density,
        )
    }

    // Top speaker grilles on both wings
    drawPerforatedSpeakerGrille(
        centerX = centerX,
        centerY = (24f * density).coerceAtMost(h * 0.13f),
        density = density,
        palette = palette,
        scale = 0.85f,
    )

    if (!isLeft) {
        // Right wing: 3D parallax indicator near top & status LEDs near bottom
        draw3DIndicatorBadge(
            rightEdgeX = wingX + wingW - 8f * density,
            centerY = (44f * density).coerceAtMost(h * 0.24f),
            maxHeightPx = 14f * density,
            palette = palette,
            alpha = threeDAlpha,
            density = density,
        )

        val ledY = h - (20f * density).coerceAtMost(h * 0.10f)
        drawGlowingLed(
            center = Offset(centerX - 6f * density, ledY),
            baseColor = Color(0xFFFFB300),
            alpha = wifiAlpha,
            density = density,
            radiusDp = 1.9f,
            hasHalo = true,
        )
        drawGlowingLed(
            center = Offset(centerX + 6f * density, ledY),
            baseColor = palette.powerLedColor,
            alpha = ledAlpha,
            density = density,
            radiusDp = 1.9f,
            hasHalo = true,
        )
    }
}

private fun DrawScope.drawLandscapeSlimRail(
    isLeft: Boolean,
    railX: Float,
    railW: Float,
    h: Float,
    palette: ConsoleSkinPalette,
    ledAlpha: Float,
    density: Float,
) {
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(palette.secondaryBody, palette.primaryBody),
            startX = railX,
            endX = railX + railW,
        ),
        topLeft = Offset(railX, 0f),
        size = Size(railW, h),
    )
    if (palette.formFactor == HardwareFormFactor.AURORA || palette.formFactor == HardwareFormFactor.STORM_PRO) {
        val trimX = railX + railW * 0.5f
        drawLine(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, palette.trimAccent.copy(alpha = 0.72f), Color.Transparent),
                startY = h * 0.2f,
                endY = h * 0.8f,
            ),
            start = Offset(trimX, h * 0.2f),
            end = Offset(trimX, h * 0.8f),
            strokeWidth = 1.4f * density,
        )
    } else if (!isLeft && railW >= 10f * density) {
        drawGlowingLed(
            center = Offset(railX + railW * 0.5f, h * 0.5f),
            baseColor = palette.powerLedColor,
            alpha = ledAlpha,
            density = density,
            radiusDp = 1.6f,
            hasHalo = true,
        )
    }
}

private fun DrawScope.drawGlowingLed(
    center: Offset,
    baseColor: Color,
    alpha: Float,
    density: Float,
    radiusDp: Float = 2.0f,
    hasHalo: Boolean = true,
) {
    val r = (radiusDp * density).coerceAtLeast(1.0f)
    val effectiveColor = baseColor.copy(alpha = alpha.coerceIn(0f, 1f))

    if (hasHalo) {
        val haloRadius = r * 2.6f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    baseColor.copy(alpha = (0.42f * alpha).coerceIn(0f, 1f)),
                    baseColor.copy(alpha = (0.10f * alpha).coerceIn(0f, 1f)),
                    Color.Transparent,
                ),
                center = center,
                radius = haloRadius,
            ),
            radius = haloRadius,
            center = center,
        )
    }

    drawCircle(
        color = Color.Black.copy(alpha = 0.80f),
        radius = r + 0.65f * density,
        center = center,
    )
    drawCircle(
        color = effectiveColor,
        radius = r,
        center = center,
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.88f * alpha),
        radius = r * 0.34f,
        center = Offset(center.x - r * 0.26f, center.y - r * 0.26f),
    )
}

private fun DrawScope.drawPerforatedSpeakerGrille(
    centerX: Float,
    centerY: Float,
    density: Float,
    palette: ConsoleSkinPalette,
    scale: Float = 1.0f,
) {
    val s = scale.coerceIn(0.45f, 1.0f)
    val dotR = 1.25f * density * s
    val spacingX = 3.6f * density * s
    val spacingY = 3.6f * density * s
    val dotColor = Color.Black.copy(alpha = 0.86f)
    val rimColor = if (palette.isLightSkin) {
        Color(0xFF94A3B8).copy(alpha = 0.72f)
    } else {
        palette.innerBevelLight.copy(alpha = 0.36f)
    }

    // Authentic 3DS twin speaker perforation cluster (3x2 dots)
    for (row in 0..1) {
        for (col in -1..1) {
            val dx = centerX + col * spacingX
            val dy = centerY + (row - 0.5f) * spacingY
            drawCircle(dotColor, dotR, Offset(dx, dy))
            drawCircle(rimColor, dotR, Offset(dx, dy), style = Stroke(width = 0.45f * density))
        }
    }
}

private fun DrawScope.draw3DIndicatorBadge(
    rightEdgeX: Float,
    centerY: Float,
    maxHeightPx: Float,
    palette: ConsoleSkinPalette,
    alpha: Float,
    density: Float,
) {
    if (maxHeightPx < 7f * density) return
    val emerald = Color(0xFF10B981)
    val badgeColor = if (palette.has3DSlider) emerald else palette.trimAccent
    val label = if (palette.has3DSlider) "3D" else "2D"
    val ledR = (1.8f * density).coerceAtMost(maxHeightPx * 0.22f)
    val ledCenter = Offset(rightEdgeX - ledR - 2f * density, centerY)

    drawGlowingLed(
        center = ledCenter,
        baseColor = badgeColor,
        alpha = alpha,
        density = density,
        radiusDp = ledR / density,
        hasHalo = true,
    )

    drawIntoCanvas { canvas ->
        val p = Paint().apply {
            isAntiAlias = true
            textSize = (6.8f * density).coerceAtMost(maxHeightPx * 0.62f).coerceAtLeast(5.2f * density)
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = badgeColor.copy(alpha = 0.95f).toArgb()
        }
        val fm = p.fontMetrics
        val baseY = centerY - (fm.ascent + fm.descent) / 2f
        canvas.nativeCanvas.drawText(label, ledCenter.x - ledR - 3.2f * density, baseY, p)
    }
}

private fun DrawScope.drawSelectHomeStartBar(
    centerX: Float,
    centerY: Float,
    maxWidth: Float,
    maxHeight: Float,
    palette: ConsoleSkinPalette,
    density: Float,
) {
    if (maxHeight < 9f * density || maxWidth < 80f * density) return

    val barW = maxWidth.coerceAtMost(210f * density)
    val pillH = (11f * density).coerceAtMost(maxHeight * 0.85f).coerceAtLeast(7f * density)
    val pillW = ((barW - 14f * density) / 3f).coerceAtLeast(22f * density)
    val spacing = ((barW - 3f * pillW) / 2f).coerceAtLeast(4f * density)
    val startX = centerX - (3f * pillW + 2f * spacing) / 2f

    val labels = listOf("SELECT", "HOME", "START")
    val pillBg = if (palette.isLightSkin) Color(0xFFE2E8F0) else Color(0xFF14171F)
    val pillBorder = if (palette.isLightSkin) Color(0xFF94A3B8) else palette.bezelAccent.copy(alpha = 0.65f)
    val labelColor = if (palette.isLightSkin) Color(0xFF334155) else Color(0xFFCBD5E1)

    labels.forEachIndexed { idx, label ->
        val px = startX + idx * (pillW + spacing)
        val py = centerY - pillH / 2f
        val isHome = idx == 1

        drawRoundRect(
            color = pillBg,
            topLeft = Offset(px, py),
            size = Size(pillW, pillH),
            cornerRadius = CornerRadius(pillH * 0.45f, pillH * 0.45f),
        )
        drawRoundRect(
            color = if (isHome) palette.trimAccent.copy(alpha = 0.78f) else pillBorder,
            topLeft = Offset(px, py),
            size = Size(pillW, pillH),
            cornerRadius = CornerRadius(pillH * 0.45f, pillH * 0.45f),
            style = Stroke(width = 0.85f * density),
        )

        drawIntoCanvas { canvas ->
            val p = Paint().apply {
                isAntiAlias = true
                textSize = (5.4f * density).coerceAtMost(pillH * 0.56f).coerceAtLeast(4.2f * density)
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.04f
                color = if (isHome) palette.trimAccent.toArgb() else labelColor.toArgb()
            }
            val fm = p.fontMetrics
            val baseline = centerY - (fm.ascent + fm.descent) / 2f
            canvas.nativeCanvas.drawText(label, px + pillW / 2f, baseline, p)
        }
    }
}

private fun DrawScope.drawPortraitLayout(
    s1: Rect,
    s2: Rect,
    w: Float,
    h: Float,
    palette: ConsoleSkinPalette,
    ledAlpha: Float,
    wifiAlpha: Float,
    threeDAlpha: Float,
    outerBezelPx: Float,
    density: Float,
) {
    val upperScreen = if (s1.y <= s2.y) s1 else s2
    val lowerScreen = if (s1.y <= s2.y) s2 else s1

    val upperTop = upperScreen.y.toFloat()
    val upperBottom = upperScreen.bottom.toFloat()
    val lowerTop = lowerScreen.y.toFloat()
    val lowerBottom = lowerScreen.bottom.toFloat()
    val hingeCenterY = (upperBottom + lowerTop) / 2f

    // 1. Upper 3DS Lid Shell (from 0 to hingeCenterY) & Lower Deck Shell (hingeCenterY to h)
    val upperZoneH = hingeCenterY.coerceAtLeast(upperBottom + outerBezelPx)
    drawShellFinishShader(Offset.Zero, Size(w, upperZoneH), palette, density, isUpperLid = true)
    drawShellFinishShader(Offset(0f, upperZoneH), Size(w, (h - upperZoneH).coerceAtLeast(0f)), palette, density, isUpperLid = false)

    // 2. Authentic Classic 3DS Glossy Black Inner Glass Surround Framing 5:3 Top Screen
    if (palette.formFactor == HardwareFormFactor.CLASSIC_3DS || palette.formFactor == HardwareFormFactor.NEW_2DS_XL) {
        val glassPadX = (upperScreen.x.toFloat() * 0.42f).coerceIn(6f * density, 20f * density)
        val glassPadTop = (upperTop * 0.35f).coerceIn(3f * density, 14f * density)
        val glassLeft = (upperScreen.x - glassPadX).coerceAtLeast(4f * density)
        val glassRight = (upperScreen.right + glassPadX).coerceAtMost(w - 4f * density)
        val glassTop = (upperTop - glassPadTop).coerceAtLeast(2f * density)
        val glassBottom = (upperBottom + outerBezelPx + 2f * density).coerceAtMost(hingeCenterY)
        if (glassRight > glassLeft && glassBottom > glassTop) {
            drawRoundRect(
                color = palette.upperInnerGlass.copy(alpha = 0.88f),
                topLeft = Offset(glassLeft, glassTop),
                size = Size(glassRight - glassLeft, glassBottom - glassTop),
                cornerRadius = CornerRadius(6f * density, 6f * density),
            )
            drawRoundRect(
                color = palette.trimAccent.copy(alpha = 0.35f),
                topLeft = Offset(glassLeft, glassTop),
                size = Size(glassRight - glassLeft, glassBottom - glassTop),
                cornerRadius = CornerRadius(6f * density, 6f * density),
                style = Stroke(width = 0.9f * density),
            )
        }
    }

    // 3. Top Header / Lid Rim (Camera Pinhole, Twin Speakers, Brand Title, Parallax 3D Emerald Badge)
    val safeTopH = (upperTop - outerBezelPx - 1f * density).coerceAtLeast(0f)
    if (upperTop > 0f) {
        drawLine(
            color = if (palette.formFactor == HardwareFormFactor.AURORA || palette.formFactor == HardwareFormFactor.STORM_PRO) {
                palette.trimAccent.copy(alpha = 0.72f)
            } else {
                palette.innerBevelLight.copy(alpha = 0.52f)
            },
            start = Offset(0f, 0.5f * density),
            end = Offset(w, 0.5f * density),
            strokeWidth = 1.2f * density,
        )
    }

    if (safeTopH >= 8f * density) {
        val topCenterY = safeTopH * 0.50f
        val hasSideSpeakers = upperScreen.x >= 22f * density
        val speakerScale = (safeTopH / (16f * density)).coerceIn(0.55f, 0.92f)
        val titleMarginSide = if (hasSideSpeakers) 54f * density else 76f * density

        if (safeTopH >= 32f * density) {
            // Generous lid height: front-facing camera pinhole (+ IR sensor on New 3DS) above brand title
            drawCameraLens(
                center = Offset(w / 2f, safeTopH * 0.28f),
                radiusPx = 2.8f * density,
                palette = palette,
                density = density,
                includeIrEmitter = palette.hasFaceTrackingIr,
            )
            drawBrandText(
                text = palette.brandTitle,
                centerX = w / 2f,
                centerY = safeTopH * 0.72f,
                maxHeightPx = safeTopH * 0.36f,
                maxWidthPx = (w - 2f * titleMarginSide).coerceAtLeast(72f * density),
                palette = palette,
                density = density,
            )
        } else {
            // Compact lid: camera pinhole left of title, title centered
            drawBrandText(
                text = palette.brandTitle,
                centerX = w / 2f,
                centerY = topCenterY,
                maxHeightPx = safeTopH * 0.60f,
                maxWidthPx = (w - 2f * titleMarginSide).coerceAtLeast(72f * density),
                palette = palette,
                density = density,
            )
            if (!hasSideSpeakers) {
                drawCameraLens(
                    center = Offset(w / 2f - 46f * density, topCenterY),
                    radiusPx = (2.1f * density).coerceAtMost(safeTopH * 0.24f),
                    palette = palette,
                    density = density,
                    includeIrEmitter = palette.hasFaceTrackingIr,
                )
            }
        }

        // Twin Stereo Speaker Perforations flanking the 5:3 upper screen
        if (hasSideSpeakers) {
            val speakerCenterY = upperScreen.y + upperScreen.height / 2f
            drawPerforatedSpeakerGrille(upperScreen.x / 2f, speakerCenterY, density, palette, scale = 0.88f)
            drawPerforatedSpeakerGrille(upperScreen.right + (w - upperScreen.right) / 2f, speakerCenterY, density, palette, scale = 0.88f)
            // Also draw inner camera pinhole centered above 5:3 screen if not already drawn
            if (safeTopH < 32f * density) {
                drawCameraLens(
                    center = Offset(w / 2f - 46f * density, topCenterY),
                    radiusPx = (2.0f * density).coerceAtMost(safeTopH * 0.24f),
                    palette = palette,
                    density = density,
                    includeIrEmitter = palette.hasFaceTrackingIr,
                )
            }
        } else {
            drawPerforatedSpeakerGrille(22f * density, topCenterY, density, palette, scale = speakerScale)
            drawPerforatedSpeakerGrille(w - 52f * density, topCenterY, density, palette, scale = speakerScale)
        }

        // Parallax 3D Emerald Indicator Badge on upper right bezel
        draw3DIndicatorBadge(
            rightEdgeX = w - 8f * density,
            centerY = topCenterY,
            maxHeightPx = safeTopH * 0.72f,
            palette = palette,
            alpha = threeDAlpha,
            density = density,
        )
    }

    // 4. Side Margins Around 4:3 Lower Screen (subtle New 3DS / 2DS accent rails when screen is narrower than 5:3 top)
    val lowerLeftMargin = (lowerScreen.x.toFloat() - outerBezelPx).coerceAtLeast(0f)
    val lowerRightMargin = (w - lowerScreen.right.toFloat() - outerBezelPx).coerceAtLeast(0f)
    if (lowerLeftMargin >= 18f * density && lowerRightMargin >= 18f * density) {
        val midY = lowerScreen.y + lowerScreen.height * 0.5f
        // Subtle Circle Pad silhouette on left margin & C-Stick + ABXY dot hint on right margin (strictly outside screen)
        val leftCx = lowerLeftMargin * 0.5f
        val padR = (10f * density).coerceAtMost(lowerLeftMargin * 0.34f)
        drawCircle(
            color = palette.controlsColor.copy(alpha = 0.65f),
            radius = padR,
            center = Offset(leftCx, midY - 12f * density),
        )
        drawCircle(
            color = palette.innerBevelLight.copy(alpha = 0.30f),
            radius = padR,
            center = Offset(leftCx, midY - 12f * density),
            style = Stroke(width = 0.8f * density),
        )

        val rightCx = lowerScreen.right + outerBezelPx + lowerRightMargin * 0.5f
        val btnR = (2.4f * density).coerceAtMost(lowerRightMargin * 0.14f)
        val btnSpread = btnR * 2.3f
        val abxyY = midY - 8f * density
        val abxyColors = if (palette.hasColoredAbxyHint) {
            listOf(Color(0xFF38BDF8), Color(0xFFEF4444), Color(0xFFFACC15), Color(0xFF10B981))
        } else {
            val c = palette.trimAccent.copy(alpha = 0.65f)
            listOf(c, c, c, c)
        }
        drawCircle(abxyColors[0], btnR, Offset(rightCx, abxyY - btnSpread))
        drawCircle(abxyColors[1], btnR, Offset(rightCx + btnSpread, abxyY))
        drawCircle(abxyColors[2], btnR, Offset(rightCx, abxyY + btnSpread))
        drawCircle(abxyColors[3], btnR, Offset(rightCx - btnSpread, abxyY))
    }

    // 5. Cylindrical Clamshell Hinge (or Flat Slab Divider for 2DS)
    val gapTop = upperBottom
    val gapBottom = lowerTop
    val totalGapH = (gapBottom - gapTop).coerceAtLeast(0f)
    val clearGapTop = gapTop + outerBezelPx
    val clearGapBottom = gapBottom - outerBezelPx
    val clearGapH = (clearGapBottom - clearGapTop).coerceAtLeast(0f)

    if (totalGapH > 1f * density) {
        if (!palette.hasClamshellHinge) {
            // Nintendo 2DS Wedge Slab Continuous Divider
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(palette.upperLidBody, palette.primaryBody),
                    startY = gapTop,
                    endY = gapBottom,
                ),
                topLeft = Offset(0f, gapTop),
                size = Size(w, totalGapH),
            )
            drawLine(
                color = palette.trimAccent.copy(alpha = 0.55f),
                start = Offset(10f * density, hingeCenterY),
                end = Offset(w - 10f * density, hingeCenterY),
                strokeWidth = 1.0f * density,
            )
        } else {
            // 3DS Cylindrical Clamshell Hinge Barrel
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.hingeShadow.copy(alpha = 0.50f),
                        palette.innerBevelLight.copy(alpha = if (palette.isLightSkin) 0.72f else 0.42f),
                        palette.secondaryBody,
                        palette.primaryBody,
                        palette.hingeShadow,
                    ),
                    startY = gapTop,
                    endY = gapBottom,
                ),
                topLeft = Offset(0f, gapTop),
                size = Size(w, totalGapH),
            )

            // Left & Right Mechanical Hinge Knuckles
            val knuckleW = 14f * density
            val knuckleTint = palette.bezelAccent.copy(alpha = if (palette.isLightSkin) 0.28f else 0.42f)
            drawRect(knuckleTint, Offset(0f, gapTop), Size(knuckleW, totalGapH))
            drawRect(knuckleTint, Offset(w - knuckleW, gapTop), Size(knuckleW, totalGapH))
            drawLine(palette.hingeShadow, Offset(knuckleW, gapTop), Offset(knuckleW, gapBottom), 1.2f * density)
            drawLine(palette.hingeShadow, Offset(w - knuckleW, gapTop), Offset(w - knuckleW, gapBottom), 1.2f * density)

            if (palette.formFactor == HardwareFormFactor.AURORA || palette.formFactor == HardwareFormFactor.STORM_PRO) {
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, palette.trimAccent.copy(alpha = 0.65f), Color.Transparent),
                        startX = knuckleW + 4f * density,
                        endX = w - knuckleW - 36f * density,
                    ),
                    start = Offset(knuckleW + 4f * density, hingeCenterY),
                    end = Offset(w - knuckleW - 36f * density, hingeCenterY),
                    strokeWidth = 1.1f * density,
                )
            }

            if (clearGapH >= 7f * density) {
                val centerX = w / 2f
                val slotW = 7f * density
                val slotH = (2.1f * density).coerceAtMost(clearGapH * 0.25f)
                val slotCenterX = centerX - 8f * density
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.90f),
                    topLeft = Offset(slotCenterX - slotW / 2f, hingeCenterY - slotH / 2f),
                    size = Size(slotW, slotH),
                    cornerRadius = CornerRadius(slotH / 2f, slotH / 2f),
                )
                if (clearGapH >= 9f * density) {
                    drawIntoCanvas { canvas ->
                        val p = Paint().apply {
                            isAntiAlias = true
                            textSize = (5.6f * density).coerceAtMost(clearGapH * 0.46f)
                            textAlign = Paint.Align.LEFT
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                            color = palette.brandTextColor.copy(alpha = 0.72f).toArgb()
                        }
                        val fm = p.fontMetrics
                        val baseY = hingeCenterY - (fm.ascent + fm.descent) / 2f
                        canvas.nativeCanvas.drawText("MIC", slotCenterX + slotW / 2f + 3f * density, baseY, p)
                    }
                }

                // Glowing Right-Hinge 3DS Status LEDs (Power, Wireless, Notification)
                val ledR = (1.85f * density).coerceAtMost(clearGapH * 0.19f)
                val ledSpacing = 10f * density
                val powerLedX = w - knuckleW - 10f * density
                val wifiLedX = powerLedX - ledSpacing
                val notifLedX = wifiLedX - ledSpacing

                drawGlowingLed(
                    center = Offset(notifLedX, hingeCenterY),
                    baseColor = Color(0xFF10B981),
                    alpha = ledAlpha * 0.82f,
                    density = density,
                    radiusDp = (ledR * 0.85f) / density,
                    hasHalo = false,
                )
                drawGlowingLed(
                    center = Offset(wifiLedX, hingeCenterY),
                    baseColor = Color(0xFFFFB300),
                    alpha = wifiAlpha,
                    density = density,
                    radiusDp = ledR / density,
                    hasHalo = true,
                )
                drawGlowingLed(
                    center = Offset(powerLedX, hingeCenterY),
                    baseColor = palette.powerLedColor,
                    alpha = ledAlpha,
                    density = density,
                    radiusDp = ledR / density,
                    hasHalo = true,
                )
            }
        }
    }

    // 6. Bottom Controller Deck: Authentic SELECT | HOME | START Pill Bar + Fallback LEDs
    val centerLedsDrawn = totalGapH > 1f * density && clearGapH >= 7f * density && palette.hasClamshellHinge
    if (lowerBottom < h) {
        val deckTop = lowerBottom + outerBezelPx
        val deckH = (h - deckTop).coerceAtLeast(0f)

        if (deckH >= 14f * density) {
            drawLine(
                color = if (palette.formFactor == HardwareFormFactor.AURORA || palette.formFactor == HardwareFormFactor.STORM_PRO) {
                    palette.trimAccent.copy(alpha = 0.55f)
                } else {
                    palette.innerBevelLight.copy(alpha = if (palette.isLightSkin) 0.55f else 0.30f)
                },
                start = Offset(w * 0.08f, deckTop + 1.5f * density),
                end = Offset(w * 0.92f, deckTop + 1.5f * density),
                strokeWidth = 1.0f * density,
            )

            // SELECT | HOME | START hardware pill bar below 4:3 touch screen
            val barCenterY = deckTop + (deckH * 0.45f).coerceAtMost(14f * density)
            drawSelectHomeStartBar(
                centerX = w / 2f,
                centerY = barCenterY,
                maxWidth = lowerScreen.width.toFloat().coerceAtLeast(120f * density),
                maxHeight = (deckH * 0.52f).coerceAtMost(13f * density),
                palette = palette,
                density = density,
            )
        }

        if (!centerLedsDrawn && deckH >= 18f * density) {
            val stripY = deckTop + 10.5f * density
            val ledR = 2.0f * density
            drawGlowingLed(
                center = Offset(w - 15f * density, stripY),
                baseColor = palette.powerLedColor,
                alpha = ledAlpha,
                density = density,
                radiusDp = ledR / density,
                hasHalo = true,
            )
            drawGlowingLed(
                center = Offset(w - 26f * density, stripY),
                baseColor = Color(0xFFFFB300),
                alpha = wifiAlpha,
                density = density,
                radiusDp = (ledR * 0.9f) / density,
                hasHalo = true,
            )
        }

        if (deckH >= 32f * density) {
            val portW = 18f * density
            val portH = 3.0f * density
            drawRoundRect(
                color = palette.innerBevelDark.copy(alpha = 0.85f),
                topLeft = Offset((w - portW) / 2f, h - 5.5f * density),
                size = Size(portW, portH),
                cornerRadius = CornerRadius(portH / 2f, portH / 2f),
            )
        }

        drawLine(
            color = palette.hingeShadow.copy(alpha = 0.55f),
            start = Offset(w * 0.16f, h - 2.0f * density),
            end = Offset(w * 0.84f, h - 2.0f * density),
            strokeWidth = 1.2f * density,
        )
    }
}

private fun DrawScope.drawCameraLens(
    center: Offset,
    radiusPx: Float,
    palette: ConsoleSkinPalette,
    density: Float,
    includeIrEmitter: Boolean,
) {
    drawCircle(
        color = Color(0xFF09090B),
        radius = radiusPx,
        center = center,
    )
    drawCircle(
        color = palette.bezelAccent.copy(alpha = 0.85f),
        radius = radiusPx,
        center = center,
        style = Stroke(width = 0.75f * density),
    )
    drawCircle(
        color = Color(0xFF020617),
        radius = radiusPx * 0.58f,
        center = center,
    )
    drawCircle(
        color = Color(0xFF38BDF8).copy(alpha = 0.85f),
        radius = radiusPx * 0.26f,
        center = Offset(center.x - radiusPx * 0.18f, center.y - radiusPx * 0.18f),
    )
    if (includeIrEmitter) {
        drawCircle(
            color = Color(0xFF4C1D95).copy(alpha = 0.88f),
            radius = radiusPx * 0.42f,
            center = Offset(center.x + radiusPx * 2.2f, center.y),
        )
    }
}

private fun DrawScope.drawSingleScreenLayout(
    single: Rect,
    w: Float,
    h: Float,
    palette: ConsoleSkinPalette,
    ledAlpha: Float,
    wifiAlpha: Float,
    threeDAlpha: Float,
    outerBezelPx: Float,
    density: Float,
) {
    drawShellFinishShader(Offset.Zero, Size(w, h), palette, density, isUpperLid = true)

    val safeTopH = (single.y.toFloat() - outerBezelPx - 1f * density).coerceAtLeast(0f)
    if (safeTopH >= 8f * density) {
        val topCenterY = safeTopH * 0.50f
        drawCameraLens(
            center = Offset(20f * density, topCenterY),
            radiusPx = (2.3f * density).coerceAtMost(safeTopH * 0.25f),
            palette = palette,
            density = density,
            includeIrEmitter = palette.hasFaceTrackingIr,
        )
        drawBrandText(
            text = palette.brandTitle,
            centerX = w / 2f,
            centerY = topCenterY,
            maxHeightPx = safeTopH * 0.64f,
            maxWidthPx = w * 0.60f,
            palette = palette,
            density = density,
        )
        draw3DIndicatorBadge(
            rightEdgeX = w - 34f * density,
            centerY = topCenterY,
            maxHeightPx = safeTopH * 0.72f,
            palette = palette,
            alpha = threeDAlpha,
            density = density,
        )
        drawGlowingLed(
            center = Offset(w - 14f * density, topCenterY),
            baseColor = palette.powerLedColor,
            alpha = ledAlpha,
            density = density,
            radiusDp = 1.9f,
            hasHalo = true,
        )
        drawGlowingLed(
            center = Offset(w - 25f * density, topCenterY),
            baseColor = Color(0xFFFFB300),
            alpha = wifiAlpha,
            density = density,
            radiusDp = 1.7f,
            hasHalo = true,
        )
    }

    val safeBottomH = (h - single.bottom.toFloat() - outerBezelPx).coerceAtLeast(0f)
    if (safeBottomH >= 16f * density) {
        val barY = single.bottom.toFloat() + outerBezelPx + safeBottomH * 0.50f
        drawSelectHomeStartBar(
            centerX = w / 2f,
            centerY = barY,
            maxWidth = single.width.toFloat().coerceAtLeast(120f * density),
            maxHeight = (safeBottomH * 0.64f).coerceAtMost(13f * density),
            palette = palette,
            density = density,
        )
    }
}

private fun DrawScope.drawScreenWellBezel(
    screen: Rect,
    palette: ConsoleSkinPalette,
    outerBezelPx: Float,
    density: Float,
) {
    val x = screen.x.toFloat()
    val y = screen.y.toFloat()
    val sw = screen.width.toFloat()
    val sh = screen.height.toFloat()

    val frameStroke = (outerBezelPx * 0.65f).coerceAtLeast(1.5f * density)
    val frameHalf = frameStroke / 2f
    val frameOffset = outerBezelPx - frameHalf

    drawRect(
        color = palette.bezelAccent.copy(alpha = 0.88f),
        topLeft = Offset(x - frameOffset, y - frameOffset),
        size = Size(sw + 2f * frameOffset, sh + 2f * frameOffset),
        style = Stroke(width = frameStroke),
    )

    val edgeInset = outerBezelPx
    drawLine(
        color = palette.innerBevelDark.copy(alpha = 0.75f),
        start = Offset(x - edgeInset, y - edgeInset),
        end = Offset(x + sw + edgeInset, y - edgeInset),
        strokeWidth = 1.0f * density,
    )
    drawLine(
        color = palette.innerBevelDark.copy(alpha = 0.75f),
        start = Offset(x - edgeInset, y - edgeInset),
        end = Offset(x - edgeInset, y + sh + edgeInset),
        strokeWidth = 1.0f * density,
    )
    drawLine(
        color = palette.innerBevelLight.copy(alpha = 0.55f),
        start = Offset(x - edgeInset, y + sh + edgeInset),
        end = Offset(x + sw + edgeInset, y + sh + edgeInset),
        strokeWidth = 0.9f * density,
    )
    drawLine(
        color = palette.innerBevelLight.copy(alpha = 0.55f),
        start = Offset(x + sw + edgeInset, y - edgeInset),
        end = Offset(x + sw + edgeInset, y + sh + edgeInset),
        strokeWidth = 0.9f * density,
    )

    val innerRimStroke = 1.1f * density
    val innerHalf = innerRimStroke / 2f
    if (palette.formFactor == HardwareFormFactor.AURORA || palette.formFactor == HardwareFormFactor.STORM_PRO) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    palette.trimAccent.copy(alpha = 0.90f),
                    palette.bezelAccent.copy(alpha = 0.75f),
                    palette.trimAccent.copy(alpha = 0.90f),
                ),
                start = Offset(x - innerHalf, y - innerHalf),
                end = Offset(x + sw + innerHalf, y + sh + innerHalf),
            ),
            topLeft = Offset(x - innerHalf, y - innerHalf),
            size = Size(sw + 2f * innerHalf, sh + 2f * innerHalf),
            style = Stroke(width = innerRimStroke),
        )
    } else {
        drawRect(
            color = palette.screenRimColor,
            topLeft = Offset(x - innerHalf, y - innerHalf),
            size = Size(sw + 2f * innerHalf, sh + 2f * innerHalf),
            style = Stroke(width = innerRimStroke),
        )
    }
}

private fun DrawScope.drawChassisDeckPanel(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    palette: ConsoleSkinPalette,
) {
    if (width <= 0f || height <= 0f) return
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(palette.secondaryBody, palette.primaryBody),
            startY = top,
            endY = top + height,
        ),
        topLeft = Offset(left, top),
        size = Size(width, height),
    )
}

private fun DrawScope.drawBrandText(
    text: String,
    centerX: Float,
    centerY: Float,
    maxHeightPx: Float,
    maxWidthPx: Float,
    palette: ConsoleSkinPalette,
    density: Float,
) {
    if (text.isBlank() || maxHeightPx < 6f * density || maxWidthPx < 24f * density) return
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.06f
            color = palette.brandTextColor.toArgb()
        }

        var sizePx = (9.5f * density).coerceAtMost(maxHeightPx * 0.84f).coerceAtLeast(6.0f * density)
        paint.textSize = sizePx
        val measuredW = paint.measureText(text)
        if (measuredW > maxWidthPx && measuredW > 0f) {
            sizePx = (sizePx * (maxWidthPx / measuredW)).coerceAtLeast(5.6f * density)
            paint.textSize = sizePx
        }
        val fm = paint.fontMetrics
        val textH = fm.descent - fm.ascent
        if (textH > maxHeightPx && textH > 0f) {
            sizePx = (sizePx * (maxHeightPx / textH)).coerceAtLeast(5.4f * density)
            paint.textSize = sizePx
        }

        val finalFm = paint.fontMetrics
        val baselineY = centerY - (finalFm.ascent + finalFm.descent) / 2f
        canvas.nativeCanvas.drawText(text, centerX, baselineY, paint)
    }
}
