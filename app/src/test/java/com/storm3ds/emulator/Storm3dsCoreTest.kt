package com.storm3ds.emulator

import android.content.SharedPreferences
import android.view.MotionEvent
import com.storm3ds.emulator.common.UriFileHandler
import com.storm3ds.emulator.common.cheats.BundledMmjCheatsLoader
import com.storm3ds.emulator.common.network.HttpRedirectHelper
import com.storm3ds.emulator.common.romprocessors.CompressedRomFileProcessor
import com.storm3ds.emulator.domain.model.Input
import com.storm3ds.emulator.domain.model.InputConfig
import com.storm3ds.emulator.domain.model.ThreeDS
import com.storm3ds.emulator.domain.model.Version
import com.storm3ds.emulator.domain.model.layout.LayoutComponent
import com.storm3ds.emulator.github.APK_CONTENT_TYPE
import com.storm3ds.emulator.github.GitHubReleaseSelector
import com.storm3ds.emulator.github.GitHubUpdateChannel
import com.storm3ds.emulator.github.StormUpdateMirrorClient
import com.storm3ds.emulator.github.services.UpdateApkMetadata
import com.storm3ds.emulator.github.services.UpdateApkRejectionReason
import com.storm3ds.emulator.github.services.UpdateApkValidationPolicy
import com.storm3ds.emulator.github.dtos.AssetDto
import com.storm3ds.emulator.github.dtos.ReleaseDto
import com.storm3ds.emulator.impl.AdrenoVulkanDriverSupport
import com.storm3ds.emulator.impl.emulator.EmulatorMessageQueue
import com.storm3ds.emulator.impl.input.DefaultControllerConfigurationFactory
import com.storm3ds.emulator.nativebridge.NativeLibrary
import com.storm3ds.emulator.nativebridge.utils.SystemSaveGame
import com.storm3ds.emulator.utils.getFloatCompat
import com.storm3ds.emulator.utils.getLongCompat
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun `all 17 native 3ds extensions and 3 archive formats are supported`() {
        val nativeExts = listOf(
            "3ds", "cci", "cxi", "app", "cia", "3dsx", "elf", "axf",
            "z3ds", "zcci", "zcxi", "zcia", "z3dsx", "bcci", "bcxi", "bcia", "artic"
        )
        assertEquals(17, ThreeDS.NATIVE_3DS_EXTENSIONS.size)
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

    @Test
    fun `default 3ds username is STORM 3DS and migrates AZAHAR safely`() {
        assertEquals("STORM 3DS", SystemSaveGame.DEFAULT_3DS_USERNAME)
        assertEquals(10, SystemSaveGame.MAX_USERNAME_UTF16_LEN)
        assertEquals("STORM 3DS", SystemSaveGame.sanitizeUsername(null))
        assertEquals("STORM 3DS", SystemSaveGame.sanitizeUsername("   "))
        assertEquals("ABCDEFGHIJ", SystemSaveGame.sanitizeUsername("ABCDEFGHIJKLM"))
        assertEquals("123456789", SystemSaveGame.sanitizeUsername("123456789\uD83D\uDE00"))

        assertEquals("STORM 3DS", SystemSaveGame.resolveEffectiveUsername("", ""))
        assertEquals("STORM 3DS", SystemSaveGame.resolveEffectiveUsername("AZAHAR", "AZAHAR"))
        assertEquals("STORM 3DS", SystemSaveGame.resolveEffectiveUsername("", "AZAHAR"))
        assertEquals("ReiKa", SystemSaveGame.resolveEffectiveUsername("ReiKa", "AZAHAR"))
        assertEquals("ReiKa", SystemSaveGame.resolveEffectiveUsername("", "ReiKa"))

        val cfgDefaults = File("../core/src/core/hle/service/cfg/cfg_defaults.cpp").takeIf { it.exists() }
            ?: File("core/src/core/hle/service/cfg/cfg_defaults.cpp")
        val cppTxt = cfgDefaults.readText()
        assertTrue("cfg_defaults.cpp must use u\"STORM 3DS\"", """u"STORM 3DS"""" in cppTxt)
        assertFalse("cfg_defaults.cpp must not contain AZAHAR", "AZAHAR" in cppTxt)
    }

    @Test
    fun `mali panvk architecture classification and driver package heuristics`() {
        val v7G52 = AdrenoVulkanDriverSupport.classifyMaliArchFamily("Mali-G52")
        assertEquals(AdrenoVulkanDriverSupport.MaliArchFamily.V7_BIFROST, v7G52)
        assertTrue(v7G52.isSupportedByMalisx2())

        assertEquals(
            AdrenoVulkanDriverSupport.MaliArchFamily.V7_BIFROST,
            AdrenoVulkanDriverSupport.classifyMaliArchFamily("Mali-G76"),
        )
        assertEquals(
            AdrenoVulkanDriverSupport.MaliArchFamily.V9_VALHALL,
            AdrenoVulkanDriverSupport.classifyMaliArchFamily("Mali-G78"),
        )
        assertEquals(
            AdrenoVulkanDriverSupport.MaliArchFamily.V10_VALHALL,
            AdrenoVulkanDriverSupport.classifyMaliArchFamily("Mali-G610"),
        )
        assertEquals(
            AdrenoVulkanDriverSupport.MaliArchFamily.V10_VALHALL,
            AdrenoVulkanDriverSupport.classifyMaliArchFamily("Mali-G710"),
        )
        assertEquals(
            AdrenoVulkanDriverSupport.MaliArchFamily.V11_VALHALL,
            AdrenoVulkanDriverSupport.classifyMaliArchFamily("Immortalis-G715"),
        )
        assertEquals(
            AdrenoVulkanDriverSupport.MaliArchFamily.V12_FIFTH_GEN,
            AdrenoVulkanDriverSupport.classifyMaliArchFamily("Immortalis-G720"),
        )

        val midgard = AdrenoVulkanDriverSupport.classifyMaliArchFamily("Mali-G72")
        assertEquals(AdrenoVulkanDriverSupport.MaliArchFamily.UNSUPPORTED_MIDGARD_OR_V6, midgard)
        assertFalse(midgard.isSupportedByMalisx2())
        assertEquals(
            AdrenoVulkanDriverSupport.MaliArchFamily.UNSUPPORTED_MIDGARD_OR_V6,
            AdrenoVulkanDriverSupport.classifyMaliArchFamily("Mali-T880"),
        )

        val v13Plus = AdrenoVulkanDriverSupport.classifyMaliArchFamily("Immortalis-G925")
        assertEquals(AdrenoVulkanDriverSupport.MaliArchFamily.UNSUPPORTED_V13_PLUS, v13Plus)
        assertFalse(v13Plus.isSupportedByMalisx2())

        assertTrue(AdrenoVulkanDriverSupport.isDriverLikelyMalisx2Package("malisx2-v1.zip", "Mesa PanVK", "libvulkan_panfrost.so"))
        assertFalse(AdrenoVulkanDriverSupport.isDriverLikelyAdrenoTurnipPackage("malisx2-v1.zip", "Mesa PanVK", "libvulkan_panfrost.so"))

        assertTrue(AdrenoVulkanDriverSupport.isDriverLikelyAdrenoTurnipPackage("Turnip-v25.zip", "Mesa Turnip", "vulkan.ad07xx.so"))
        assertFalse(AdrenoVulkanDriverSupport.isDriverLikelyMalisx2Package("Turnip-v25.zip", "Mesa Turnip", "vulkan.ad07xx.so"))
    }

    @Test
    fun `shared preferences compat heals int stored in float and long keys`() {
        val backing = mutableMapOf<String, Any?>(
            "btn_alpha" to 2,
            "ra_gen" to 2,
        )
        val prefs = object : SharedPreferences {
            override fun getAll(): MutableMap<String, *> = backing.toMutableMap()
            override fun getString(key: String?, defValue: String?): String? =
                (backing[key] as? String) ?: defValue
            @Suppress("UNCHECKED_CAST")
            override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
                (backing[key] as? MutableSet<String>) ?: defValues
            override fun getInt(key: String?, defValue: Int): Int {
                val v = backing[key] ?: return defValue
                return v as? Int ?: throw ClassCastException("Not Int")
            }
            override fun getLong(key: String?, defValue: Long): Long {
                val v = backing[key] ?: return defValue
                return v as? Long ?: throw ClassCastException("Not Long")
            }
            override fun getFloat(key: String?, defValue: Float): Float {
                val v = backing[key] ?: return defValue
                return v as? Float ?: throw ClassCastException("Not Float")
            }
            override fun getBoolean(key: String?, defValue: Boolean): Boolean {
                val v = backing[key] ?: return defValue
                return v as? Boolean ?: throw ClassCastException("Not Boolean")
            }
            override fun contains(key: String?): Boolean = backing.containsKey(key)
            override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
                private val pending = mutableMapOf<String, Any?>()
                private var cleared = false
                override fun putString(key: String?, value: String?) = apply { if (key != null) pending[key] = value }
                override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { if (key != null) pending[key] = values }
                override fun putInt(key: String?, value: Int) = apply { if (key != null) pending[key] = value }
                override fun putLong(key: String?, value: Long) = apply { if (key != null) pending[key] = value }
                override fun putFloat(key: String?, value: Float) = apply { if (key != null) pending[key] = value }
                override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) pending[key] = value }
                override fun remove(key: String?) = apply { if (key != null) pending[key] = null }
                override fun clear() = apply { cleared = true }
                override fun commit(): Boolean {
                    if (cleared) backing.clear()
                    for ((k, v) in pending) {
                        if (v == null) backing.remove(k) else backing[k] = v
                    }
                    return true
                }
                override fun apply() {
                    commit()
                }
            }
            override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
            override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        }

        assertEquals(2.0f, prefs.getFloatCompat("btn_alpha", 1.0f), 0.0001f)
        assertEquals(2L, prefs.getLongCompat("ra_gen", 0L))
        assertTrue(backing["btn_alpha"] is Float)
        assertTrue(backing["ra_gen"] is Long)
    }

    @Test
    fun `uri file handler resolveWriteModes and saf truncation fallback`() {
        assertEquals(listOf("wt", "rwt", "rw"), UriFileHandler.resolveWriteModes("wt"))
        assertEquals(listOf("rwt", "rw"), UriFileHandler.resolveWriteModes("rwt"))
        assertEquals(listOf("r"), UriFileHandler.resolveWriteModes("r"))

        val triedModes = mutableListOf<String>()
        var truncatedCount = 0
        var closedCount = 0
        val fd = UriFileHandler.openSafFileDescriptorWithFallback(
            requestedMode = "wt",
            openDescriptor = { mode ->
                triedModes.add(mode)
                when (mode) {
                    "wt" -> throw UnsupportedOperationException("wt unsupported")
                    "rwt" -> throw SecurityException("rwt unsupported")
                    "rw" -> "fake_pfd"
                    else -> null
                }
            },
            truncateDescriptor = { _ -> truncatedCount += 1 },
            closeDescriptor = { _ -> closedCount += 1 },
            detachDescriptor = { _ -> 42 },
        )
        assertEquals(42, fd)
        assertEquals(listOf("wt", "rwt", "rw"), triedModes)
        assertEquals(1, truncatedCount)
        assertEquals(0, closedCount)
    }

    @Test
    fun `cia installed game path matching handles slash, backslash, and compact title ids`() {
        val tid = 0x000400000f700200L
        val slashPath = "/storage/emulated/0/STORM 3DS/sdmc/Nintendo 3DS/00000000000000000000000000000000/00000000000000000000000000000000/title/00040000/0f700200/content/00000000.app"
        val backslashPath = """C:\STORM_3DS\title\00040000\0F700200\content\00000000.app"""
        val compactPath = "/tmp/000400000F700200.app"
        val otherPath = "/storage/emulated/0/STORM 3DS/sdmc/Nintendo 3DS/00000000000000000000000000000000/00000000000000000000000000000000/title/00040000/00030800/content/00000000.app"

        assertEquals(slashPath, MelonEmulator.matchInstalledGamePathByTitleId(listOf(otherPath, slashPath), tid))
        assertEquals(backslashPath, MelonEmulator.matchInstalledGamePathByTitleId(listOf(otherPath, backslashPath), tid))
        assertEquals(compactPath, MelonEmulator.matchInstalledGamePathByTitleId(listOf(otherPath, compactPath), tid))
        assertEquals(null, MelonEmulator.matchInstalledGamePathByTitleId(listOf(otherPath), tid))
        assertEquals(null, MelonEmulator.matchInstalledGamePathByTitleId(listOf(slashPath), 0L))
    }

    @Test
    fun `compressed rom entry priority prefers playable 3ds formats over cia and homebrew`() {
        assertEquals(3, CompressedRomFileProcessor.romEntryPriority("game.3ds"))
        assertEquals(3, CompressedRomFileProcessor.romEntryPriority("game.zcci"))
        assertEquals(3, CompressedRomFileProcessor.romEntryPriority("game.bcci"))
        assertEquals(3, CompressedRomFileProcessor.romEntryPriority("game.cxi"))
        assertEquals(2, CompressedRomFileProcessor.romEntryPriority("Xenoblade.zcia"))
        assertEquals(2, CompressedRomFileProcessor.romEntryPriority("game.cia"))
        assertEquals(2, CompressedRomFileProcessor.romEntryPriority("bundle.bcia"))
        assertEquals(1, CompressedRomFileProcessor.romEntryPriority("homebrew.elf"))
        assertEquals(0, CompressedRomFileProcessor.romEntryPriority("readme.txt"))
    }

    @Test
    fun `native core guards prevent 4gb overflow, cia read infinite loop, and n3ds rejection`() {
        val srcJava = File("src/main/java").takeIf { it.exists() } ?: File("app/src/main/java")
        val ncchCpp = File("../core/src/core/file_sys/ncch_container.cpp").takeIf { it.exists() }
            ?: File("core/src/core/file_sys/ncch_container.cpp")
        val amCpp = File("../core/src/core/hle/service/am/am.cpp").takeIf { it.exists() }
            ?: File("core/src/core/hle/service/am/am.cpp")
        val coreCpp = File("../core/src/core/core.cpp").takeIf { it.exists() }
            ?: File("core/src/core/core.cpp")

        val ncchTxt = ncchCpp.readText()
        assertTrue("ncch_container must cast partition/romfs offsets to u64", "static_cast<u64>(ncch_header.romfs_offset)" in ncchTxt)
        assertTrue("ncch_container must cast NCSD partition offset to u64", "static_cast<u64>(ncsd_header.partitions[partition].offset)" in ncchTxt)

        val amTxt = amCpp.readText()
        assertTrue("MAX_IMPORT_FILE_SIZE must allow 4 GiB content", "0x100000000ULL" in amTxt)
        assertTrue("InstallCIA must break on bytes_read == 0", "bytes_read == 0" in amTxt)
        assertTrue("GetCIAInfos must unwrap Z3DSReadIOFile", "Z3DSReadIOFile::GetUnderlyingFileMagic" in amTxt)

        val coreTxt = coreCpp.readText()
        assertTrue("Core::System::Load must auto-enable is_new_3ds for N3DS exclusives", "auto-enabling New 3DS mode" in coreTxt)

        val workerTxt = File(srcJava, "com/storm3ds/emulator/nativebridge/utils/CiaInstallWorker.kt").readText()
        assertTrue("CiaInstallWorker must strip leading '!' before checking File", "removePrefix(\"!\")" in workerTxt)
    }

    @Test
    fun `storm games world download url normalization handles relative, proxy, and empty urls`() {
        assertEquals(
            "https://stormgamesworld.ru/uploads/programs/storm_3ds/STORM_3DS_1.4.0.apk",
            StormUpdateMirrorClient.normalizeStormGamesWorldDownloadUrl(
                rawDownloadUrl = "/uploads/programs/storm_3ds/STORM_3DS_1.4.0.apk",
                fileName = "STORM_3DS_1.4.0.apk",
                isAltHost = false,
            ),
        )
        assertEquals(
            "https://storm-games.world/uploads/programs/storm_3ds/STORM_3DS_1.4.0.apk",
            StormUpdateMirrorClient.normalizeStormGamesWorldDownloadUrl(
                rawDownloadUrl = "/uploads/programs/storm_3ds/STORM_3DS_1.4.0.apk",
                fileName = "STORM_3DS_1.4.0.apk",
                isAltHost = true,
            ),
        )
        assertEquals(
            "https://stormgamesworld.ru/uploads/programs/storm_3ds/STORM_3DS_1.4.0.apk",
            StormUpdateMirrorClient.normalizeStormGamesWorldDownloadUrl(
                rawDownloadUrl = "https://0.0.0.0:4000/uploads/programs/storm_3ds/STORM_3DS_1.4.0.apk",
                fileName = "STORM_3DS_1.4.0.apk",
                isAltHost = false,
            ),
        )
        assertEquals(
            "https://stormgamesworld.ru/uploads/programs/storm_3ds/STORM_3DS_1.4.0.apk",
            StormUpdateMirrorClient.normalizeStormGamesWorldDownloadUrl(
                rawDownloadUrl = "https://0.0.0.0:4000/api/programs/git?repo=STORM_3DS&action=download_asset",
                fileName = "STORM_3DS_1.4.0.apk",
                isAltHost = false,
            ),
        )
        assertEquals(
            "https://stormgamesworld.ru/uploads/programs/storm_3ds/STORM_3DS_1.4.0.apk",
            StormUpdateMirrorClient.normalizeStormGamesWorldDownloadUrl(
                rawDownloadUrl = "",
                fileName = "STORM_3DS_1.4.0.apk",
                isAltHost = false,
            ),
        )
        assertEquals(
            "https://github.com/ReiKatari/STORM_3DS/releases/download/1.4.0/STORM_3DS_1.4.0.apk",
            StormUpdateMirrorClient.normalizeStormGamesWorldDownloadUrl(
                rawDownloadUrl = "https://github.com/ReiKatari/STORM_3DS/releases/download/1.4.0/STORM_3DS_1.4.0.apk",
                fileName = "STORM_3DS_1.4.0.apk",
                isAltHost = false,
            ),
        )
    }

    @Test
    fun `release selector accepts 1_4_0 from github and both sgw url casings while rejecting relative urls`() {
        val currentVersion = Version.fromString("1.3.0")

        fun makeRelease(htmlUrl: String, downloadUrl: String) = ReleaseDto(
            tagName = "1.4.0",
            name = "STORM 3DS 1.4.0",
            body = "Release notes",
            createdAt = "2026-11-24T00:00:00Z",
            publishedAt = "2026-11-24T00:00:00Z",
            htmlUrl = htmlUrl,
            draft = false,
            prerelease = false,
            assets = listOf(
                AssetDto(
                    id = 1L,
                    url = downloadUrl,
                    name = "STORM_3DS_1.4.0.apk",
                    size = 82_379_898L,
                    contentType = APK_CONTENT_TYPE,
                ),
            ),
        )

        val ghCandidate = GitHubReleaseSelector.selectProduction(
            releases = listOf(
                makeRelease(
                    htmlUrl = "https://github.com/ReiKatari/STORM_3DS/releases/tag/1.4.0",
                    downloadUrl = "https://github.com/ReiKatari/STORM_3DS/releases/download/1.4.0/STORM_3DS_1.4.0.apk",
                ),
            ),
            currentVersion = currentVersion,
            channel = GitHubUpdateChannel.STABLE,
            skippedVersion = null,
            preferLegacyApk = false,
        )
        assertNotNull("GitHub 1.4.0 release should be selected", ghCandidate)
        assertEquals("1.4.0", ghCandidate?.version.toString())

        val sgwLowerCandidate = GitHubReleaseSelector.selectProduction(
            releases = listOf(
                makeRelease(
                    htmlUrl = "https://stormgamesworld.ru/programs?repo=STORM_3DS&tag=1.4.0",
                    downloadUrl = "https://stormgamesworld.ru/uploads/programs/storm_3ds/STORM_3DS_1.4.0.apk",
                ),
            ),
            currentVersion = currentVersion,
            channel = GitHubUpdateChannel.STABLE,
            skippedVersion = null,
            preferLegacyApk = false,
        )
        assertNotNull("SGW lowercase storm_3ds 1.4.0 release should be selected", sgwLowerCandidate)

        val sgwUpperCandidate = GitHubReleaseSelector.selectProduction(
            releases = listOf(
                makeRelease(
                    htmlUrl = "https://stormgamesworld.ru/programs?repo=STORM_3DS&tag=1.4.0",
                    downloadUrl = "https://stormgamesworld.ru/uploads/programs/STORM_3DS/STORM_3DS_1.4.0.apk",
                ),
            ),
            currentVersion = currentVersion,
            channel = GitHubUpdateChannel.STABLE,
            skippedVersion = null,
            preferLegacyApk = false,
        )
        assertNotNull("SGW uppercase STORM_3DS 1.4.0 release should be selected", sgwUpperCandidate)

        val relativeRejected = GitHubReleaseSelector.selectProduction(
            releases = listOf(
                makeRelease(
                    htmlUrl = "https://stormgamesworld.ru/programs?repo=STORM_3DS&tag=1.4.0",
                    downloadUrl = "/uploads/programs/storm_3ds/STORM_3DS_1.4.0.apk",
                ),
            ),
            currentVersion = currentVersion,
            channel = GitHubUpdateChannel.STABLE,
            skippedVersion = null,
            preferLegacyApk = false,
        )
        assertNull("Unnormalized relative asset url should be rejected", relativeRejected)
    }

    @Test
    fun `updater proguard, hilt context unwrap, and mirror guards are configured`() {
        val srcJava = File("src/main/java").takeIf { it.exists() } ?: File("app/src/main/java")
        val proguardFile = File("proguard-rules.pro").takeIf { it.exists() } ?: File("app/proguard-rules.pro")
        val proguardTxt = proguardFile.readText()
        assertTrue("proguard-rules.pro must keep github DTOs", "com.storm3ds.emulator.github.dtos.**" in proguardTxt)
        assertTrue("proguard-rules.pro must keep GitHubApi", "com.storm3ds.emulator.github.GitHubApi" in proguardTxt)

        val manualTxt = File(srcJava, "com/storm3ds/emulator/ui/settings/ManualUpdateChecker.kt").readText()
        assertTrue("ManualUpdateChecker must unwrap Hilt ContextWrapper", "unwrapActivity" in manualTxt)
        assertTrue("ManualUpdateChecker must use BuildConfig.VERSION_NAME fallback", "BuildConfig.VERSION_NAME" in manualTxt)
        assertFalse("ManualUpdateChecker must not contain leftover 8.6.0 version string", "8.6.0" in manualTxt)

        val installMgrTxt = File(srcJava, "com/storm3ds/emulator/github/services/GitHubUpdateInstallManager.kt").readText()
        assertTrue("GitHubUpdateInstallManager must compare origin prefixes case-insensitively", "ignoreCase = true" in installMgrTxt)

        val mirrorTxt = File(srcJava, "com/storm3ds/emulator/github/StormUpdateMirrorClient.kt").readText()
        assertTrue("StormUpdateMirrorClient must prefer lightweight releases endpoint", "STORM_GAMES_WORLD_RELEASES_API_URL" in mirrorTxt)
        assertTrue("StormUpdateMirrorClient must check hasAnyValidAsset before skipping redirect fallback", "hasAnyValidAsset" in mirrorTxt)
    }

    @Test
    fun `update apk validation policy enforces package, version, size, and signer`() {
        val installed = UpdateApkMetadata(
            packageName = "com.storm3ds.emulator",
            versionCode = 10400L,
            versionName = "1.4.0",
            signerDigests = setOf("abc123"),
        )
        val valid = UpdateApkMetadata(
            packageName = "com.storm3ds.emulator",
            versionCode = 10500L,
            versionName = "1.5.0",
            signerDigests = setOf("abc123"),
        )
        assertNull(
            UpdateApkValidationPolicy.validate(
                candidate = valid,
                installed = installed,
                expectedVersion = Version.fromString("1.5.0"),
                requireSemanticVersionMatch = true,
                expectedSize = 80_000_000L,
                actualSize = 80_000_000L,
            )
        )
        assertEquals(
            UpdateApkRejectionReason.SIGNATURE_MISMATCH,
            UpdateApkValidationPolicy.validate(
                candidate = valid.copy(signerDigests = setOf("other")),
                installed = installed,
                expectedVersion = Version.fromString("1.5.0"),
                requireSemanticVersionMatch = true,
                expectedSize = 80_000_000L,
                actualSize = 80_000_000L,
            )
        )
        assertEquals(
            UpdateApkRejectionReason.VERSION_CODE_NOT_NEWER,
            UpdateApkValidationPolicy.validate(
                candidate = valid.copy(versionCode = 10400L),
                installed = installed,
                expectedVersion = Version.fromString("1.5.0"),
                requireSemanticVersionMatch = true,
                expectedSize = 0L,
                actualSize = 80_000_000L,
            )
        )
        assertEquals(
            UpdateApkRejectionReason.SIZE_MISMATCH,
            UpdateApkValidationPolicy.validate(
                candidate = valid,
                installed = installed,
                expectedVersion = Version.fromString("1.5.0"),
                requireSemanticVersionMatch = true,
                expectedSize = 100L,
                actualSize = 200L,
            )
        )
        assertNull(
            UpdateApkValidationPolicy.validate(
                candidate = valid.copy(packageName = "com.storm3ds.emulator.nightly"),
                installed = installed.copy(packageName = "com.storm3ds.emulator.nightly"),
                expectedVersion = Version.fromString("1.5.0"),
                requireSemanticVersionMatch = false,
                expectedSize = 0L,
                actualSize = 100L,
            )
        )
    }

    @Test
    fun `http redirect helper permits safe https and relative targets while blocking downgrades`() {
        assertEquals(
            "https://objects.githubusercontent.com/file.apk",
            HttpRedirectHelper.resolveSafeRedirectUrl(
                "https://github.com/ReiKatari/STORM_3DS/releases/download/1.5.0/STORM_3DS_1.5.0.apk",
                "https://objects.githubusercontent.com/file.apk",
            )
        )
        assertEquals(
            "https://stormgamesworld.ru/uploads/programs/storm_3ds/STORM_3DS_1.5.0.apk",
            HttpRedirectHelper.resolveSafeRedirectUrl(
                "https://stormgamesworld.ru/api/programs/releases?repo=STORM_3DS",
                "/uploads/programs/storm_3ds/STORM_3DS_1.5.0.apk",
            )
        )
        var downgradeRejected = false
        try {
            HttpRedirectHelper.resolveSafeRedirectUrl(
                "https://github.com/ReiKatari/STORM_3DS",
                "http://insecure.example.com/update.apk",
            )
        } catch (_: java.io.IOException) {
            downgradeRejected = true
        }
        assertTrue("HTTPS to HTTP downgrade redirect must throw IOException", downgradeRejected)

        var badSchemeRejected = false
        try {
            HttpRedirectHelper.resolveSafeRedirectUrl(
                "https://github.com/ReiKatari/STORM_3DS",
                "file:///etc/passwd",
            )
        } catch (_: java.io.IOException) {
            badSchemeRejected = true
        }
        assertTrue("Non-http(s) redirect scheme must throw IOException", badSchemeRejected)
    }

    @Test
    fun `z3ds extension is supported in ThreeDS and CompressedRomFileProcessor`() {
        assertTrue("z3ds must be in NATIVE_3DS_EXTENSIONS", ThreeDS.isNative3dsExtension("z3ds"))
        assertTrue("z3ds must be supported", ThreeDS.isSupportedExtension("z3ds"))
        assertEquals(3, CompressedRomFileProcessor.romEntryPriority("game.z3ds"))
        assertEquals(400, ThreeDS.TOP_SCREEN_WIDTH)
        assertEquals(240, ThreeDS.TOP_SCREEN_HEIGHT)
    }

    @Test
    fun `single screen 3d depth, updater, performance, and bugfix guards hold`() {
        val srcJava = File("src/main/java").takeIf { it.exists() } ?: File("app/src/main/java")
        val srcCpp = File("src/main/cpp/jni").takeIf { it.exists() } ?: File("app/src/main/cpp/jni")
        val resDir = File("src/main/res").takeIf { it.exists() } ?: File("app/src/main/res")
        val coreDir = File("../core/src").takeIf { it.exists() } ?: File("core/src")

        // Crisp single-image 3D depth shaders (no double-image eye blur)
        val vkGyro = File(coreDir, "video_core/host_shaders/vulkan_present_gyro.frag").readText()
        val glGyro = File(coreDir, "video_core/host_shaders/opengl_present_gyro.frag").readText()
        assertFalse("vulkan_present_gyro.frag must not blend left and right eyes", "mix(left_col, right_col" in vkGyro)
        assertFalse("opengl_present_gyro.frag must not blend left and right eyes", "mix(left_col, right_col" in glGyro)
        assertTrue("vulkan_present_gyro.frag must compute EstimateSceneDepth", "EstimateSceneDepth" in vkGyro)
        assertTrue("opengl_present_gyro.frag must compute EstimateSceneDepth", "EstimateSceneDepth" in glGyro)

        val extCpp = File(srcCpp, "storm3ds_extensions.cpp").readText()
        assertTrue("setRender3dMode must request live shader update", "shader_update_requested = true" in extCpp)

        val arraysXml = File(resDir, "values/arrays.xml").readText()
        val offIdx = arraysXml.indexOf("<item>off</item>")
        val gyroIdx = arraysXml.indexOf("<item>gyro_parallax</item>")
        val sbsIdx = arraysXml.indexOf("<item>sbs_xr</item>")
        assertTrue("gyro_parallax must be ordered immediately after off in arrays.xml", offIdx in 0 until gyroIdx && gyroIdx < sbsIdx)

        // End-to-end updater checks
        val manualTxt = File(srcJava, "com/storm3ds/emulator/ui/settings/ManualUpdateChecker.kt").readText()
        assertTrue("ManualUpdateChecker must use UpdateEntryPoint", "UpdateEntryPoint" in manualTxt)
        assertTrue("ManualUpdateChecker must install via UpdateInstallManager", "updateInstallManager" in manualTxt)

        val installMgrTxt = File(srcJava, "com/storm3ds/emulator/github/services/GitHubUpdateInstallManager.kt").readText()
        assertTrue("GitHubUpdateInstallManager must inspect apkContentsSigners", "apkContentsSigners" in installMgrTxt)
        assertTrue("GitHubUpdateInstallManager must check canRequestPackageInstalls", "canRequestPackageInstalls" in installMgrTxt)

        // Performance checks
        val vkPresentTxt = File(coreDir, "video_core/renderer_vulkan/vk_present_window.cpp").readText()
        assertFalse("vk_present_window.cpp must not stall with eAllCommands barriers", "eAllCommands" in vkPresentTxt)

        val glStreamTxt = File(coreDir, "video_core/renderer_opengl/gl_stream_buffer.cpp").readText()
        assertTrue("gl_stream_buffer.cpp must support GLAD_GL_EXT_buffer_storage", "GLAD_GL_EXT_buffer_storage" in glStreamTxt)

        val vkFenceTxt = File(coreDir, "video_core/renderer_vulkan/vk_master_semaphore.cpp").readText()
        assertTrue("vk_master_semaphore.cpp must retry eErrorInitializationFailed", "eErrorInitializationFailed" in vkFenceTxt)

        val melonTxt = File(srcJava, "com/storm3ds/emulator/MelonEmulator.kt").readText()
        assertTrue("3ds_spirv_opt must default to false", """prefs.getBoolean("3ds_spirv_opt", false)""" in melonTxt)
        assertTrue("3ds_log_filter must default to *:Warning", """prefs.getString("3ds_log_filter", "*:Warning")""" in melonTxt)

        // Bugfixes
        val saveStatesTxt = File(srcJava, "com/storm3ds/emulator/impl/FileSystemSaveStatesRepository.kt").readText()
        assertTrue("FileSystemSaveStatesRepository must copy native .cst on duplicate", "copyNativeCstForSlot" in saveStatesTxt)

        val cheatsLoaderTxt = File(srcJava, "com/storm3ds/emulator/common/cheats/BundledMmjCheatsLoader.kt").readText()
        assertTrue("BundledMmjCheatsLoader must expose populateAllBundled3dsCheats", "populateAllBundled3dsCheats" in cheatsLoaderTxt)
    }

    @Test
    fun `desktop qt frontend includes bcxi z3ds storm theme hidpi scale and gyro3d`() {
        val coreDir = File("../core/src").canonicalFile.let {
            if (it.exists()) it else File("core/src")
        }
        val gameListCpp = File(coreDir, "citra_qt/game_list.cpp").readText()
        assertTrue("game_list.cpp must support bcxi", "QStringLiteral(\"bcxi\")" in gameListCpp)
        assertTrue("game_list.cpp must support z3ds", "QStringLiteral(\"z3ds\")" in gameListCpp)

        val uiSettingsCpp = File(coreDir, "citra_qt/uisettings.cpp").readText()
        assertTrue("uisettings.cpp must register storm_flagship theme", "storm_flagship" in uiSettingsCpp)

        val citraQtCpp = File(coreDir, "citra_qt/citra_qt.cpp").readText()
        assertTrue("citra_qt.cpp must use STORM 3DS branding", "QStringLiteral(\"STORM 3DS\")" in citraQtCpp)
        assertTrue("citra_qt.cpp must support ui_scale startup scaling", "ApplyConfiguredUiScaleBeforeQApp" in citraQtCpp)

        val enhCpp = File(coreDir, "citra_qt/configuration/configure_enhancements.cpp").readText()
        assertTrue("configure_enhancements.cpp must expose GyroParallax", "GyroParallax" in enhCpp)
    }

    @Test
    fun `system 3ds setup dialog uses unified STORM watermelon components without raw material controls`() {
        val srcJava = File("src/main/java").takeIf { it.exists() } ?: File("app/src/main/java")
        val setupTxt = File(srcJava, "com/storm3ds/emulator/ui/settings/System3dsSetupDialog.kt").readText()

        assertTrue("System3dsSetupDialog must use WatermelonSwitch", "WatermelonSwitch(" in setupTxt)
        assertTrue("System3dsSetupDialog must use SetupSectionCard", "SetupSectionCard" in setupTxt)
        assertTrue("System3dsSetupDialog must use SetupSectionHeader", "SetupSectionHeader" in setupTxt)
        assertTrue("System3dsSetupDialog must use WatermelonMono typography", "WatermelonMono" in setupTxt)
        assertFalse("System3dsSetupDialog must not use raw Material OutlinedButton", "OutlinedButton(" in setupTxt)
        assertFalse("System3dsSetupDialog must not use raw Material OutlinedTextField", "OutlinedTextField(" in setupTxt)
        assertFalse("System3dsSetupDialog must not hardcode KeyBadge dark hex colors", "0xFF123B26" in setupTxt)
        assertFalse("System3dsSetupDialog must not use raw Material Switch", Regex("(?<![A-Za-z0-9_])Switch\\(").containsMatchIn(setupTxt))
    }

    @Test
    fun `storm compiler core optimizations and desktop android mode 3 wiring are present`() {
        val coreDir = File("../core/src").canonicalFile.let {
            if (it.exists()) it else File("core/src")
        }
        val patchesDir = File("../core/patches").canonicalFile.let {
            if (it.exists()) it else File("core/patches")
        }
        val resRoot = File("src/main/res").takeIf { it.exists() } ?: File("app/src/main/res")
        val srcJava = File("src/main/java").takeIf { it.exists() } ?: File("app/src/main/java")

        val dynarmicCpp = File(coreDir, "core/arm/dynarmic/arm_dynarmic.cpp").readText()
        assertTrue("arm_dynarmic.cpp must configure ARM64 absolute_offset_page_table", "absolute_offset_page_table = true" in dynarmicCpp)
        assertTrue("arm_dynarmic.cpp must expose generation-coherent RO cache", "ResetRoCache" in dynarmicCpp)
        assertTrue("arm_dynarmic.cpp must configure STORM Compiler code cache tiers", "kMaxCacheMb" in dynarmicCpp)

        val shaderJitCpp = File(coreDir, "video_core/shader/shader_jit.cpp").readText()
        assertTrue("shader_jit.cpp must maintain 1-entry MRU shader cache", "last_cache_key" in shaderJitCpp)

        val vmMgrH = File(coreDir, "core/hle/kernel/vm_manager.h").readText()
        assertTrue("vm_manager.h must expose GetGeneration", "GetGeneration()" in vmMgrH)

        val patchTxt = File(patchesDir, "storm_dynarmic_compiler.patch").readText()
        assertTrue("Dynarmic patch must include ARM64 FastDispatch", "LinkTarget::FastDispatch" in patchTxt)
        assertTrue("Dynarmic patch must pass extend32 for absolute page table", "extend32" in patchTxt)
        assertTrue("Dynarmic patch must guard VerificationPass in Release", "#ifndef NDEBUG" in patchTxt)

        val prefSystem = File(resRoot, "xml/pref_system.xml").readText()
        assertTrue(
            "pref_system.xml 3ds_jit_mode must default to mode 3",
            Regex("""android:key="3ds_jit_mode"[\s\S]*?android:defaultValue="3"""").containsMatchIn(prefSystem),
        )
        val arraysXml = File(resRoot, "values/arrays.xml").readText()
        assertTrue("arrays.xml must include STORM Compiler mode 3", "<item>3</item>" in arraysXml)

        val melonTxt = File(srcJava, "com/storm3ds/emulator/MelonEmulator.kt").readText()
        assertTrue("MelonEmulator must default to STORM Compiler mode 3", "coerceIn(0, 3)" in melonTxt)

        val dbgUi = File(coreDir, "citra_qt/configuration/configure_debug.ui").readText()
        assertTrue("configure_debug.ui must expose storm_jit_mode_combo", "storm_jit_mode_combo" in dbgUi)
    }

    @Test
    fun `azahar 2127 p0 p1 ports and bcxi headless secondary surface guards are intact`() {
        val coreDir = File("../core/src").canonicalFile.let {
            if (it.exists()) it else File("core/src")
        }
        val appMain = File("src/main").takeIf { it.exists() } ?: File("app/src/main")

        val vkRenderer = File(coreDir, "video_core/renderer_vulkan/renderer_vulkan.cpp").readText()
        assertTrue("renderer_vulkan.cpp must define HasValidSecondarySurface", "HasValidSecondarySurface" in vkRenderer)
        assertTrue("renderer_vulkan.cpp must guard swapchain creation with HasValidSecondarySurface", "HasValidSecondarySurface(secondary_window)" in vkRenderer)

        val stylesXml = File(appMain, "res/values/styles.xml").readText()
        assertTrue("EmulatorTheme must define colorSurface for Material dialogs", "colorSurface" in stylesXml)

        val manifestXml = File(appMain, "AndroidManifest.xml").readText()
        assertTrue("AndroidManifest.xml must declare ACCESS_LOCAL_NETWORK", "android.permission.ACCESS_LOCAL_NETWORK" in manifestXml)
        listOf(".z3ds", ".Z3DS", ".ZCCI", ".ZCXI", ".ZCIA", ".Z3DSX", ".BCCI", ".BCXI", ".BCIA").forEach { ext ->
            assertTrue("AndroidManifest.xml must include extension $ext", ext in manifestXml)
        }

        val extCpp = File(appMain, "cpp/jni/storm3ds_extensions.cpp").readText()
        assertTrue("storm3ds_extensions.cpp must expose setAdrenoTurbo", "setAdrenoTurbo" in extCpp)
        assertTrue("storm3ds_extensions.cpp must expose areAllBciaTitlesInstalled", "areAllBciaTitlesInstalled" in extCpp)

        val melonKt = File(appMain, "java/com/storm3ds/emulator/MelonEmulator.kt").readText()
        assertTrue("MelonEmulator.kt must dispatch use_limit_frames_ahead_of_gpu", "use_limit_frames_ahead_of_gpu" in melonKt)

        val repoKt = File(appMain, "java/com/storm3ds/emulator/impl/FileSystemRomsRepository.kt").readText()
        assertTrue("FileSystemRomsRepository.kt must filter dotfiles with startsWith(\".\")", """startsWith(".")""" in repoKt)

        val parcelKt = File(appMain, "java/com/storm3ds/emulator/parcelables/RomConfigParcelable.kt").readText()
        val readMatches = Regex("""(\w+)\s*=\s*parcel\.readNullable""").findAll(parcelKt).map { it.groupValues[1] }.toList()
        val writeMatches = Regex("""dest\.writeNullable\w+\(romConfig\.(\w+)\)""").findAll(parcelKt).map { it.groupValues[1] }.toList()
        assertTrue("RomConfigParcelable must read and write symmetric nullable fields", readMatches.isNotEmpty() && readMatches == writeMatches)
    }

    @Test
    fun `ergonomic layouts, redesigned updater dialogs, pause quick controls, and qt qss are wired for 1_9_0`() {
        val coreRoot = File("../core").canonicalFile.let { if (it.exists()) it else File("core") }
        val appMain = File("src/main").takeIf { it.exists() } ?: File("app/src/main")

        val layoutProvider = File(appMain, "java/com/storm3ds/emulator/impl/layout/DefaultLayoutProvider.kt").readText()
        listOf("C_STICK", "BUTTON_ZL", "BUTTON_ZR", "BUTTON_HOME", "BUTTON_PAUSE", "BUTTON_TOGGLE_EXTRA_BUTTONS").forEach { comp ->
            assertTrue("DefaultLayoutProvider must include $comp", comp in layoutProvider)
        }

        val updateDialogs = File(appMain, "java/com/storm3ds/emulator/ui/updates/StormUpdateDialogs.kt").readText()
        assertTrue("StormUpdateDialogs must expose showStormUpdateAvailableDialog", "fun showStormUpdateAvailableDialog(" in updateDialogs)
        val romListAct = File(appMain, "java/com/storm3ds/emulator/ui/romlist/RomListActivity.kt").readText()
        assertTrue("RomListActivity must use showStormUpdateAvailableDialog", "showStormUpdateAvailableDialog(" in romListAct)
        val manualChecker = File(appMain, "java/com/storm3ds/emulator/ui/settings/ManualUpdateChecker.kt").readText()
        assertTrue("ManualUpdateChecker must use showStormUpdateAvailableDialog", "showStormUpdateAvailableDialog(" in manualChecker)

        val pauseOpt = File(appMain, "java/com/storm3ds/emulator/ui/emulator/rom/RomPauseMenuOption.kt").readText()
        assertTrue("RomPauseMenuOption must define CYCLE_LAYOUT", "CYCLE_LAYOUT(" in pauseOpt)
        assertTrue("RomPauseMenuOption must define TOGGLE_MUTE", "TOGGLE_MUTE(" in pauseOpt)
        val pauseOverlay = File(appMain, "java/com/storm3ds/emulator/ui/emulator/ui/PauseMenuOverlay.kt").readText()
        assertTrue("PauseMenuOverlay must expose onQuick3dChanged", "onQuick3dChanged" in pauseOverlay)

        val coreCmake = File(coreRoot, "CMakeLists.txt").readText()
        assertTrue(
            "core/CMakeLists.txt must enable Qt update checker by default",
            Regex("""option\(ENABLE_QT_UPDATE_CHECKER\b[^\n]*\bON\)""").containsMatchIn(coreCmake),
        )
        val qtUpdateDialog = File(coreRoot, "src/citra_qt/update_dialog.cpp").readText()
        assertTrue("Qt UpdateDialog must render markdown release notes", "setMarkdown(" in qtUpdateDialog)
        val lightQss = File(coreRoot, "dist/qt_themes/storm_light/style.qss").readText()
        listOf("QSlider", "QTextBrowser", "QMessageBox").forEach { sel ->
            assertTrue("storm_light/style.qss must style $sel", sel in lightQss)
        }
    }
}
