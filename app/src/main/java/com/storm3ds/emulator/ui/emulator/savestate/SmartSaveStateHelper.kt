package com.storm3ds.emulator.ui.emulator.savestate

import com.storm3ds.emulator.R
import android.content.Context
import android.content.SharedPreferences

object SmartSaveStateHelper {
    private const val PREFS_NAME = "smart_savestate_bookmarks"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getCustomName(context: Context, romChecksum: String, slot: Int): String? {
        val key = "${romChecksum}_slot_${slot}_name"
        return getPrefs(context).getString(key, null)
    }

    fun setCustomName(context: Context, romChecksum: String, slot: Int, name: String?) {
        val key = "${romChecksum}_slot_${slot}_name"
        getPrefs(context).edit().apply {
            if (name.isNullOrBlank()) remove(key) else putString(key, name.trim())
            apply()
        }
    }

    fun generateAutoBookmarkTag(gameTitle: String?, slot: Int, context: Context? = null): String {
        val title = gameTitle?.lowercase() ?: ""
        fun tr(resId: Int, fallback: String): String = context?.getString(resId) ?: fallback
        return when {
            title.contains("pokemon") || title.contains("heartgold") || title.contains("soulsilver") -> when (slot % 4) {
                0 -> tr(R.string.ui_bookmark_quick_save, "⭐ Quick Save")
                1 -> tr(R.string.ui_bookmark_pokemon_center, "🏛️ PokeCenter / League")
                2 -> tr(R.string.ui_bookmark_pokemon_gym, "⚔️ Gym Leader Battle")
                else -> tr(R.string.ui_bookmark_pokemon_route, "🌲 Route / Exploration")
            }
            title.contains("mario") -> when (slot % 4) {
                0 -> tr(R.string.ui_bookmark_quick_save, "⭐ Quick Save")
                1 -> tr(R.string.ui_bookmark_mario_bowser, "🏰 Bowser's Castle")
                2 -> tr(R.string.ui_bookmark_mario_world, "🍄 Challenge World")
                else -> tr(R.string.ui_bookmark_mario_checkpoint, "🚩 Checkpoint")
            }
            title.contains("zelda") -> when (slot % 4) {
                0 -> tr(R.string.ui_bookmark_quick_save, "⭐ Quick Save")
                1 -> tr(R.string.ui_bookmark_zelda_boss, "⚔️ Dungeon Boss")
                2 -> tr(R.string.ui_bookmark_zelda_temple, "🗝️ Temple / Puzzle")
                else -> tr(R.string.ui_bookmark_zelda_sea, "⛵ Sea Voyage")
            }
            title.contains("batman") -> when (slot % 4) {
                0 -> tr(R.string.ui_bookmark_quick_save, "⭐ Quick Save")
                1 -> tr(R.string.ui_bookmark_batman_boss, "🦇 Gotham: Boss Fight")
                2 -> tr(R.string.ui_bookmark_batman_lair, "🌃 Villain's Lair")
                else -> tr(R.string.ui_bookmark_batman_patrol, "⚡ Night Patrol")
            }
            title.contains("castlevania") -> when (slot % 4) {
                0 -> tr(R.string.ui_bookmark_quick_save, "⭐ Quick Save")
                1 -> tr(R.string.ui_bookmark_castlevania_throne, "🩸 Throne Room / Castle")
                2 -> tr(R.string.ui_bookmark_castlevania_gate, "🦇 Gates of Immortality")
                else -> tr(R.string.ui_bookmark_castlevania_secret, "🗝️ Secret Room")
            }
            else -> when (slot % 3) {
                0 -> tr(R.string.ui_bookmark_quick_save, "⭐ Quick Save")
                1 -> tr(R.string.ui_bookmark_generic_boss, "⚔️ Boss Battle")
                else -> tr(R.string.ui_bookmark_generic_story, "📍 Story Point")
            }
        }
    }
}
