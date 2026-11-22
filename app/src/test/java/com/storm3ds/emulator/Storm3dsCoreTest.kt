package com.storm3ds.emulator

import android.view.MotionEvent
import com.storm3ds.emulator.common.cheats.BundledMmjCheatsLoader
import com.storm3ds.emulator.domain.model.Input
import com.storm3ds.emulator.domain.model.InputConfig
import com.storm3ds.emulator.domain.model.ThreeDS
import com.storm3ds.emulator.domain.model.layout.LayoutComponent
import com.storm3ds.emulator.impl.emulator.EmulatorMessageQueue
import com.storm3ds.emulator.impl.input.DefaultControllerConfigurationFactory
import com.storm3ds.emulator.nativebridge.NativeLibrary
import com.storm3ds.emulator.ui.cheats.ui.cheatform.CheatFormState
import com.storm3ds.emulator.ui.common.views.ButtonColorStyle
import com.storm3ds.emulator.ui.common.views.ButtonThemeManager
import com.storm3ds.emulator.ui.emulator.skin.ConsoleSkinTheme
import com.storm3ds.emulator.ui.romdetails.ui.parseDiscoveredLanRooms
import com.storm3ds.emulator.ui.stats.PlayStatsStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Storm3dsCoreTest {

    @Test
    fun `threeDs screen constants and aspect ratios match hardware 5to3 and 4to3`() {
        assertEquals(400, ThreeDS.TOP_SCREEN_WIDTH)
        assertEquals(240, ThreeDS.TOP_SCREEN_HEIGHT)
        assertEquals(320, ThreeDS.BOTTOM_SCREEN_WIDTH)
        assertEquals(240, ThreeDS.BOTTOM_SCREEN_HEIGHT)
        assertEquals(400f / 240f, ThreeDS.TOP_SCREEN_ASPECT_RATIO, 0.0001f)
        assertEquals(320f / 240f, ThreeDS.BOTTOM_SCREEN_ASPECT_RATIO, 0.0001f)
        assertEquals(400f / 480f, ThreeDS.HYBRID_SCREEN_ASPECT_RATIO, 0.0001f)
    }

    @Test
    fun `all 16 native 3ds extensions and 3 archive formats are supported`() {
        val nativeExts = listOf(
            "3ds", "cci", "cxi", "app", "cia", "3dsx", "elf", "axf",
            "zcci", "zcxi", "zcia", "z3dsx", "bcci", "bcxi", "bcia", "artic"
        )
        assertEquals(16, ThreeDS.NATIVE_3DS_EXTENSIONS.size)
        for (ext in nativeExts) {
            assertTrue("Expected native ext $ext", ThreeDS.isNative3dsExtension(ext))
            assertTrue("Expected supported ext $ext", ThreeDS.isSupportedExtension(".$ext"))
        }
        for (arc in listOf("zip", "7z", "rar")) {
            assertFalse("Archive $arc is not native", ThreeDS.isNative3dsExtension(arc))
            assertTrue("Expected archive ext $arc", ThreeDS.isSupportedExtension(arc))
        }
        assertFalse(ThreeDS.isSupportedExtension("iso"))
    }

    @Test
    fun `button color styles include all 10 new 3ds styles`() {
        assertTrue(ButtonColorStyle.entries.size >= 34)
        val expectedNew = listOf(
            ButtonColorStyle.N3DS_SFC_COLORED,
            ButtonColorStyle.STORM_CYBER_3D,
            ButtonColorStyle.HYRULE_GOLD_3D,
            ButtonColorStyle.METROID_VISOR,
            ButtonColorStyle.GALAXY_NEBULA,
            ButtonColorStyle.ANIMAL_LEAF,
            ButtonColorStyle.MONSTER_HUNTER_FORGE,
            ButtonColorStyle.POKEMON_POKEBALL,
            ButtonColorStyle.PERSONA_VELVET,
            ButtonColorStyle.CRYSTAL_PRISM_3D,
        )
        assertEquals(10, expectedNew.toSet().size)
    }

    @Test
    fun `console skin theme has 30 3ds skins and preserves legacy fallback aliases`() {
        assertEquals(30, ConsoleSkinTheme.entries.size)
        assertEquals(ConsoleSkinTheme.NEW_3DS_XL_GALAXY, ConsoleSkinTheme.fromKey("n3ds_galaxy"))
        assertEquals(ConsoleSkinTheme.CLASSIC_3DS_AQUA_BLUE, ConsoleSkinTheme.fromKey("n3ds_aqua"))
        assertEquals(ConsoleSkinTheme.STORM_3DS_OBSIDIAN_PRO, ConsoleSkinTheme.fromKey("storm_3ds_obsidian_pro"))
    }

    @Test
    fun `controls and stereo modes expose full 3ds hardware set`() {
        assertTrue(LayoutComponent.CIRCLE_PAD in LayoutComponent.entries)
        assertTrue(LayoutComponent.C_STICK in LayoutComponent.entries)
        assertTrue(LayoutComponent.SLIDER_3D_DEPTH in LayoutComponent.entries)
        assertTrue(LayoutComponent.BUTTON_ZL in LayoutComponent.entries)
        assertTrue(LayoutComponent.BUTTON_ZR in LayoutComponent.entries)
        assertTrue(LayoutComponent.BUTTON_HOME in LayoutComponent.entries)
        assertTrue(LayoutComponent.BUTTON_COMBO_1 in LayoutComponent.entries)
        assertTrue(LayoutComponent.BUTTON_COMBO_2 in LayoutComponent.entries)
        assertTrue(Input.ZL in Input.SYSTEM_BUTTONS)
        assertTrue(Input.ZR in Input.SYSTEM_BUTTONS)
        assertTrue(Input.HOME in Input.SYSTEM_BUTTONS)
        assertEquals(7, NativeLibrary.StereoMode.GYRO_PARALLAX.nativeValue)
    }

    @Test
    fun `mmj gateway cheat txt parser extracts multiple cheats and notes`() {
        val sample = """
            [Max Rupees]
            * Sets Rupees to 9999
            0039C120 0000270F

            [Infinite HP]
            20501234 00000063
            10501238 00000063
        """.trimIndent()
        val parsed = BundledMmjCheatsLoader.parseGatewayTxt(sample)
        assertEquals(2, parsed.size)
        assertEquals("Max Rupees", parsed[0].name)
        assertEquals("Sets Rupees to 9999", parsed[0].notes)
        assertEquals("0039C120 0000270F", parsed[0].code)
        assertEquals("20501234 00000063\n10501238 00000063", parsed[1].code)
    }

    @Test
    fun `curated game profiles asset parses cleanly with at least 25 entries`() {
        val candidates = listOf(
            File("src/main/assets/profiles/game_profiles.json"),
            File("app/src/main/assets/profiles/game_profiles.json"),
        )
        val file = candidates.firstOrNull { it.exists() }
        assertTrue("Missing game_profiles.json", file != null)
        val root = Json.parseToJsonElement(file!!.readText()).jsonObject
        val profiles = root["profiles"]!!.jsonArray
        assertTrue("Expected >= 25 profiles, got ${profiles.size}", profiles.size >= 25)
    }

    @Test
    fun `stereo mode enum values match Azahar settings header ordinals 0 to 7`() {
        assertEquals(0, NativeLibrary.StereoMode.OFF.nativeValue)
        assertEquals(1, NativeLibrary.StereoMode.SIDE_BY_SIDE.nativeValue)
        assertEquals(2, NativeLibrary.StereoMode.SIDE_BY_SIDE_FULL.nativeValue)
        assertEquals(3, NativeLibrary.StereoMode.ANAGLYPH.nativeValue)
        assertEquals(4, NativeLibrary.StereoMode.INTERLACED.nativeValue)
        assertEquals(5, NativeLibrary.StereoMode.REVERSE_INTERLACED.nativeValue)
        assertEquals(6, NativeLibrary.StereoMode.CARDBOARD_VR.nativeValue)
        assertEquals(7, NativeLibrary.StereoMode.GYRO_PARALLAX.nativeValue)
    }

    @Test
    fun `emulator message queue starts and stops cleanly without native deadlock`() {
        val queue = EmulatorMessageQueue { _, _ -> }
        queue.start()
        assertTrue(queue.isActive())
        queue.stop()
        assertFalse(queue.isActive())
        queue.cleanup()
        assertFalse(queue.isActive())
    }

    @Test
    fun `rom decryptor returns clean status without missing JNI errors`() {
        assertEquals(
            MelonRomDecryptor.EncryptionStatus.NOT_ENCRYPTED,
            MelonRomDecryptor.checkEncryption("nonexistent_game.3ds"),
        )
        assertEquals(
            MelonRomDecryptor.DecryptResult.ALREADY_DECRYPTED,
            MelonRomDecryptor.decryptRom("nonexistent_game.3ds"),
        )
    }

    @Test
    fun `azahar netplay room parser supports 3-field room entries`() {
        val rooms = parseDiscoveredLanRooms(
            arrayOf("Mario Kart 7 Room|192.168.1.50:24872|2/8"),
            fallbackTitle = "Mario Kart 7",
        )
        assertEquals(1, rooms.size)
        assertEquals("Mario Kart 7 Room", rooms[0].hostName)
        assertEquals("192.168.1.50", rooms[0].hostIp)
        assertEquals(2, rooms[0].currentPlayers)
        assertEquals(8, rooms[0].maxPlayers)
    }

    @Test
    fun `ui branding strings and readme contain zero WaterSTORM or STORM DS leftovers`() {
        val paths = listOf(
            "src/main/res/values/strings.xml",
            "src/main/res/values-b+ru/strings.xml",
            "src/main/java/com/storm3ds/emulator/ui/romlist/composables/RomBrowserComposables.kt",
            "src/main/java/com/storm3ds/emulator/ui/romlist/NoRomSearchDirectoriesFragment.kt",
            "../README.md",
        )
        for (rel in paths) {
            val f = File(rel).takeIf { it.exists() } ?: File("app", rel)
            assertTrue("Missing file $rel", f.exists())
            val txt = f.readText()
            assertFalse("WaterSTORM found in $rel", "WaterSTORM" in txt)
            assertFalse("STORM DS found in $rel", "STORM DS" in txt)
            assertFalse("Nintendo 3DSi found in $rel", "Nintendo 3DSi" in txt)
        }
    }

    @Test
    fun `coreError and installStatus map all 3ds values cleanly`() {
        assertEquals(NativeLibrary.CoreError.Success, NativeLibrary.CoreError.fromInt(0))
        assertEquals(NativeLibrary.CoreError.ErrorSystemFiles, NativeLibrary.CoreError.fromInt(10))
        assertEquals(NativeLibrary.CoreError.ErrorArticDisconnected, NativeLibrary.CoreError.fromInt(12))
        assertEquals(NativeLibrary.CoreError.ErrorShaderCacheCrash, NativeLibrary.CoreError.fromInt(17))
        assertEquals(NativeLibrary.CoreError.ErrorUnknown, NativeLibrary.CoreError.fromInt(18))
        assertEquals(NativeLibrary.CoreError.ErrorUnknown, NativeLibrary.CoreError.fromInt(999))
        assertTrue(NativeLibrary.InstallStatus.ErrorCannotCheckBundle in NativeLibrary.InstallStatus.entries)
    }

    @Test
    fun `default controller configuration binds ZL ZR HOME and excludes AXIS_X AXIS_Y from dpad`() {
        val config = DefaultControllerConfigurationFactory().buildDefaultControllerConfiguration()
        val mappedInputs = config.inputMapper.map { it.input }.toSet()
        assertTrue(Input.ZL in mappedInputs)
        assertTrue(Input.ZR in mappedInputs)
        assertTrue(Input.HOME in mappedInputs)
        assertTrue(config.getInputAssignments(Input.ZL)?.isNotEmpty() == true)
        assertTrue(config.getInputAssignments(Input.ZR)?.isNotEmpty() == true)

        val dpadInputs = setOf(Input.LEFT, Input.RIGHT, Input.UP, Input.DOWN)
        for (entry in config.inputMapper.filter { it.input in dpadInputs }) {
            for (assignment in listOf(entry.assignment, entry.altAssignment)) {
                if (assignment is InputConfig.Assignment.Axis) {
                    assertFalse(
                        "D-Pad ${entry.input} must not consume analog stick AXIS_X",
                        assignment.axisCode == MotionEvent.AXIS_X,
                    )
                    assertFalse(
                        "D-Pad ${entry.input} must not consume analog stick AXIS_Y",
                        assignment.axisCode == MotionEvent.AXIS_Y,
                    )
                }
            }
        }
    }

    @Test
    fun `button theme manager provides valid distinct palettes for all 34 styles`() {
        val styles = ButtonColorStyle.entries
        assertEquals(34, styles.size)
        val palettes = styles.map { style ->
            val p = ButtonThemeManager.paletteFor(style)
            assertTrue("Expected opaque thumbDark for $style", p.thumbDark != 0)
            assertTrue("Expected opaque labelText for $style", p.labelText != 0)
            p
        }
        assertTrue("Expected multiple distinct palettes", palettes.toSet().size >= 30)
    }

    @Test
    fun `cheat form state preserves multiline gateway 3ds code newlines`() {
        val code = "D3000000 10000000\n0039C120 0000270F"
        val form = CheatFormState(
            isNewCheat = true,
            cheatName = "Max Rupees",
            cheatDescription = "Sets 9999 Rupees",
            cheatCode = code,
        )
        assertTrue("Expected valid cheat form", form.validateFields())
        assertEquals(code, form.buildSubmissionForm().code)
    }

    @Test
    fun `play stats classifies 3dsx homebrew separately from retail 3ds titles`() {
        assertTrue(PlayStatsStore.isDsiGame("HB3DS", "homebrew_menu.3dsx"))
        assertFalse(PlayStatsStore.isDsiGame("0004000000030800", "Mario Kart 7"))
    }

    @Test
    fun `all 10 pause_session_hours_minutes resources use positional args and zero em dashes`() {
        val resRoot = File("src/main/res").takeIf { it.exists() } ?: File("app/src/main/res")
        val targetFiles = resRoot.listFiles { f -> f.isDirectory && f.name.startsWith("values") }
            ?.mapNotNull { dir -> File(dir, "strings_v780_emulator.xml").takeIf { it.isFile } }
            .orEmpty()
        assertEquals(10, targetFiles.size)
        val forbidden = listOf('\u2014', '\u2013', '\u2015', '\u2012')
        for (file in targetFiles) {
            val txt = file.readText()
            val line = txt.lines().firstOrNull { "pause_session_hours_minutes" in it }
            assertTrue("Missing pause_session_hours_minutes in ${file.path}", line != null)
            assertTrue("Expected %1\$d in ${file.path}: $line", "%1\$d" in line!!)
            assertTrue("Expected %2\$d in ${file.path}: $line", "%2\$d" in line)
            for (dash in forbidden) {
                assertFalse("Forbidden dash U+${dash.code.toString(16)} in ${file.path}", dash in txt)
            }
        }
    }

    @Test
    fun `threeDs system language defaults to system auto and maps device locales`() {
        val resRoot = File("src/main/res").takeIf { it.exists() } ?: File("app/src/main/res")
        val prefSystem = File(resRoot, "xml/pref_system.xml").readText()
        assertTrue(
            "3ds_system_language must default to -1 (System Default)",
            Regex("""android:key="3ds_system_language"[\s\S]*?android:defaultValue="-1"""").containsMatchIn(prefSystem),
        )

        val arraysTxt = File(resRoot, "values/arrays.xml").readText()
        assertTrue(
            "system_language_values must start with -1",
            Regex("""<string-array\s+name="system_language_values">\s*<item>-1</item>""").containsMatchIn(arraysTxt),
        )
        assertTrue(
            "system_language_options must start with @string/pref_3ds_lang_system_default",
            Regex("""<string-array\s+name="system_language_options">\s*<item>@string/pref_3ds_lang_system_default</item>""").containsMatchIn(arraysTxt),
        )

        assertEquals(0, MelonEmulator.resolveDevice3dsSystemLanguage(java.util.Locale.JAPAN))
        assertEquals(1, MelonEmulator.resolveDevice3dsSystemLanguage(java.util.Locale.US))
        assertEquals(2, MelonEmulator.resolveDevice3dsSystemLanguage(java.util.Locale.FRANCE))
        assertEquals(3, MelonEmulator.resolveDevice3dsSystemLanguage(java.util.Locale.GERMANY))
        assertEquals(4, MelonEmulator.resolveDevice3dsSystemLanguage(java.util.Locale.ITALY))
        assertEquals(5, MelonEmulator.resolveDevice3dsSystemLanguage(java.util.Locale("es", "ES")))
        assertEquals(6, MelonEmulator.resolveDevice3dsSystemLanguage(java.util.Locale.SIMPLIFIED_CHINESE))
        assertEquals(7, MelonEmulator.resolveDevice3dsSystemLanguage(java.util.Locale.KOREA))
        assertEquals(8, MelonEmulator.resolveDevice3dsSystemLanguage(java.util.Locale("nl", "NL")))
        assertEquals(9, MelonEmulator.resolveDevice3dsSystemLanguage(java.util.Locale("pt", "BR")))
        assertEquals(10, MelonEmulator.resolveDevice3dsSystemLanguage(java.util.Locale("ru", "RU")))
        assertEquals(10, MelonEmulator.resolveDevice3dsSystemLanguage(java.util.Locale("uk", "UA")))
        assertEquals(11, MelonEmulator.resolveDevice3dsSystemLanguage(java.util.Locale.TRADITIONAL_CHINESE))
        assertEquals(1, MelonEmulator.resolveDevice3dsSystemLanguage(java.util.Locale("id", "ID")))
    }

    @Test
    fun `all 10 locales have 100 percent string parity and system_3ds_setup is wired`() {
        val srcJava = File("src/main/java").takeIf { it.exists() } ?: File("app/src/main/java")
        val sysPrefFrag = File(srcJava, "com/storm3ds/emulator/ui/settings/fragments/SystemPreferencesFragment.kt").readText()
        assertTrue("system_3ds_setup preference must have a click listener", "findPreference<Preference>(\"system_3ds_setup\")" in sysPrefFrag)
        assertTrue("system_3ds_setup click listener must invoke showSystem3dsSetupDialog", "showSystem3dsSetupDialog" in sysPrefFrag)

        val resRoot = File("src/main/res").takeIf { it.exists() } ?: File("app/src/main/res")
        val baseDir = File(resRoot, "values")
        val locales = listOf(
            "values-b+ru", "values-b+es", "values-b+fr", "values-de",
            "values-b+it", "values-b+pt+BR", "values-b+id", "values-b+zh+Hans", "values-ja",
        )
        val docFactory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        fun readStrings(dir: File): Set<String> {
            val out = mutableSetOf<String>()
            dir.listFiles { f -> f.isFile && f.extension == "xml" }?.forEach { file ->
                val doc = docFactory.newDocumentBuilder().parse(file)
                val nodes = doc.getElementsByTagName("string")
                for (i in 0 until nodes.length) {
                    val el = nodes.item(i) as org.w3c.dom.Element
                    if (el.getAttribute("translatable") != "false") {
                        out.add(el.getAttribute("name"))
                    }
                }
            }
            return out
        }

        val baseKeys = readStrings(baseDir)
        assertTrue("Expected > 2500 base keys, got ${baseKeys.size}", baseKeys.size > 2500)
        for (loc in locales) {
            val locKeys = readStrings(File(resRoot, loc))
            val missing = baseKeys - locKeys
            assertTrue("Locale $loc is missing ${missing.size} strings, sample=${missing.take(10)}", missing.isEmpty())
        }

        val ruFiles = File(resRoot, "values-b+ru").listFiles { f -> f.isFile && f.extension == "xml" }.orEmpty()
        val forbiddenEnInRu = Regex("""\b(CPU|GPU|RAM|Battery\s+Saver|Native\s+3DS|Overclock)\b""", RegexOption.IGNORE_CASE)
        for (f in ruFiles) {
            val doc = docFactory.newDocumentBuilder().parse(f)
            for (tag in listOf("string", "item")) {
                val nodes = doc.getElementsByTagName(tag)
                for (i in 0 until nodes.length) {
                    val txt = nodes.item(i).textContent
                    assertFalse("Unlocalized English term in ${f.name}: $txt", forbiddenEnInRu.containsMatchIn(txt))
                }
            }
        }
    }

    @Test
    fun `app log defaults to true and system video save preferences are pure 3ds`() {
        val resRoot = File("src/main/res").takeIf { it.exists() } ?: File("app/src/main/res")
        val srcJava = File("src/main/java").takeIf { it.exists() } ?: File("app/src/main/java")

        val prefSystem = File(resRoot, "xml/pref_system.xml").readText()
        assertTrue(
            "system_app_log_file_enabled must default to true",
            Regex("""android:key="system_app_log_file_enabled"[\s\S]*?android:defaultValue="true"""").containsMatchIn(prefSystem),
        )
        assertFalse("pref_system.xml must not reference custom_bios_firmware", "custom_bios_firmware" in prefSystem)
        assertFalse("pref_system.xml must not reference arm9_overclock", "arm9_overclock" in prefSystem)
        assertFalse("pref_system.xml must not reference system_wfc_server", "system_wfc_server" in prefSystem)
        assertTrue("pref_system.xml must contain 3ds_jit_mode", "3ds_jit_mode" in prefSystem)
        assertTrue("pref_system.xml must contain 3ds_low_end_preset", "3ds_low_end_preset" in prefSystem)
        assertTrue("pref_system.xml must contain 3ds_system_files_check", "3ds_system_files_check" in prefSystem)

        val prefCfw = File(resRoot, "xml/pref_custom_firmware.xml").readText()
        assertFalse("pref_custom_firmware.xml must not contain DS BIOS downloaders", "auto_download_ds_bios" in prefCfw)
        assertFalse("pref_custom_firmware.xml must not contain DSi BIOS downloaders", "auto_download_dsi_bios" in prefCfw)
        assertFalse("pref_custom_firmware.xml must not contain duplicate console_type", "console_type" in prefCfw)

        val repoTxt = File(srcJava, "com/storm3ds/emulator/impl/SharedPreferencesSettingsRepository.kt").readText()
        assertTrue(
            "SharedPreferencesSettingsRepository.isAppLogFileEnabled must default to true",
            "getBoolean(\"system_app_log_file_enabled\", true)" in repoTxt,
        )
    }

    @Test
    fun `arm dynarmic configures v6K little endian jit modes and adaptive cache`() {
        val dynarmicCpp = File("../core/src/core/arm/dynarmic/arm_dynarmic.cpp").takeIf { it.exists() }
            ?: File("core/src/core/arm/dynarmic/arm_dynarmic.cpp")
        val txt = dynarmicCpp.readText()
        assertTrue("ArchVersion::v6K must be set", "ArchVersion::v6K" in txt)
        assertTrue("always_little_endian must be enabled", "always_little_endian = true" in txt)
        assertTrue("storm_jit_mode must configure optimization levels", "storm_jit_mode" in txt)
        assertTrue("adaptive code_cache_size must be configured", "code_cache_size" in txt)
        assertTrue("IsReadOnlyMemory override must check BackingMemory + Code", "IsReadOnlyMemory" in txt && "MemoryState::Code" in txt)
    }
}
