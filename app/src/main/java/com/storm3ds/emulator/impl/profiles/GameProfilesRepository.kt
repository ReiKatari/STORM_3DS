package com.storm3ds.emulator.impl.profiles

import android.content.Context
import com.storm3ds.emulator.nativebridge.NativeLibrary
import com.storm3ds.emulator.nativebridge.features.settings.SettingKeys
import org.json.JSONObject

data class GameProfile(
    val titleIdPrefix: String,
    val title: String,
    val renderer: String,
    val resolutionScale: Int,
    val fmvHack: Boolean,
    val skipSlowDraw: Boolean,
    val skipTextureCopy: Boolean,
    val accurateMul: Boolean,
    val spirvOpt: Boolean,
    val audioStretching: Boolean,
    val depth3d: Int,
    val notes: String,
)

object GameProfilesRepository {
    private var cachedProfiles: List<GameProfile>? = null

    fun loadAll(context: Context): List<GameProfile> {
        cachedProfiles?.let { return it }
        val parsed = runCatching {
            val raw = context.assets.open("profiles/game_profiles.json").bufferedReader().use { it.readText() }
            val arr = JSONObject(raw).optJSONArray("profiles") ?: return@runCatching emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        GameProfile(
                            titleIdPrefix = o.optString("titleIdPrefix", "").uppercase(),
                            title = o.optString("title", ""),
                            renderer = o.optString("renderer", "VULKAN"),
                            resolutionScale = o.optInt("resolutionScale", 2),
                            fmvHack = o.optBoolean("fmvHack", false),
                            skipSlowDraw = o.optBoolean("skipSlowDraw", false),
                            skipTextureCopy = o.optBoolean("skipTextureCopy", false),
                            accurateMul = o.optBoolean("accurateMul", false),
                            spirvOpt = o.optBoolean("spirvOpt", true),
                            audioStretching = o.optBoolean("audioStretching", true),
                            depth3d = o.optInt("depth3d", 0),
                            notes = o.optString("notes", ""),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
        cachedProfiles = parsed
        return parsed
    }

    fun findForTitleId(context: Context, titleIdHex: String): GameProfile? {
        val norm = titleIdHex.trim().uppercase().removePrefix("0X").padStart(16, '0')
        if (norm == "0000000000000000") return null
        return loadAll(context).firstOrNull { profile ->
            profile.titleIdPrefix.isNotEmpty() && norm.startsWith(profile.titleIdPrefix)
        }
    }

    fun applyProfile(profile: GameProfile) {
        NativeLibrary.addDefaultSetting(
            SettingKeys.graphics_api(),
            if (profile.renderer.equals("VULKAN", ignoreCase = true)) "2" else "1"
        )
        NativeLibrary.addDefaultSetting(SettingKeys.resolution_factor(), profile.resolutionScale.coerceIn(1, 10).toString())
        NativeLibrary.setSpeedhacks(
            fmvHack = profile.fmvHack,
            skipSlowDraw = profile.skipSlowDraw,
            skipTextureCopy = profile.skipTextureCopy,
            accurateMul = profile.accurateMul,
            audioStretching = profile.audioStretching,
            separableShader = false,
        )
        NativeLibrary.setSpirvOptimization(if (profile.spirvOpt) 1 else 0, false, false)
        NativeLibrary.setFactor3d(profile.depth3d.coerceIn(0, 100))
    }
}
