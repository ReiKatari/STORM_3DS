package com.storm3ds.emulator

import com.storm3ds.emulator.common.cheats.BundledMmjCheatsLoader
import com.storm3ds.emulator.domain.model.Input
import com.storm3ds.emulator.domain.model.ThreeDS
import com.storm3ds.emulator.domain.model.layout.LayoutComponent
import com.storm3ds.emulator.impl.emulator.EmulatorMessageQueue
import com.storm3ds.emulator.nativebridge.NativeLibrary
import com.storm3ds.emulator.ui.common.views.ButtonColorStyle
import com.storm3ds.emulator.ui.emulator.skin.ConsoleSkinTheme
import com.storm3ds.emulator.ui.romdetails.ui.parseDiscoveredLanRooms
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
}
