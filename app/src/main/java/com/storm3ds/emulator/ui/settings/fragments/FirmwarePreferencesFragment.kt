package com.storm3ds.emulator.ui.settings.fragments

import android.os.Bundle
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.SeekBarPreference
import com.storm3ds.emulator.NativeCoreLoader
import com.storm3ds.emulator.R
import com.storm3ds.emulator.nativebridge.utils.SystemSaveGame
import com.storm3ds.emulator.ui.settings.PreferenceFragmentTitleProvider
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FirmwarePreferencesFragment : BasePreferenceFragment(), PreferenceFragmentTitleProvider {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_internal_firmware_settings, rootKey)

        val coreLoaded = runCatching {
            NativeCoreLoader.load()
            com.storm3ds.emulator.nativebridge.NativeLibrary.setUserDirectory(
                com.storm3ds.emulator.nativebridge.NativeBridgeHost.resolveUserDirectoryPath(),
            )
            SystemSaveGame.load()
            true
        }.getOrDefault(false)

        val nicknamePref = findPreference<EditTextPreference>("firmware_settings_nickname")?.apply {
            summaryProvider = null
        }
        val birthdayPref = findPreference<Preference>("firmware_settings_birthday")
        val coinsPref = findPreference<SeekBarPreference>("3ds_play_coins")
        val macPref = findPreference<Preference>("internal_mac_address")
        val regenMacPref = findPreference<Preference>("internal_randomize_mac")
        val regenIdPref = findPreference<Preference>("3ds_regenerate_console_id")

        if (coreLoaded) {
            runCatching {
                val prefs = preferenceManager.sharedPreferences
                val prefName = prefs?.getString("firmware_settings_nickname", null)
                val nandName = SystemSaveGame.getUsername()
                val effectiveName = SystemSaveGame.resolveEffectiveUsername(prefName, nandName)
                if (effectiveName != nandName) {
                    SystemSaveGame.setUsername(effectiveName)
                    SystemSaveGame.save()
                }
                if (prefName.isNullOrBlank() || prefName.equals("AZAHAR", ignoreCase = true)) {
                    prefs?.edit()?.putString("firmware_settings_nickname", effectiveName)?.apply()
                }
                nicknamePref?.text = effectiveName
                nicknamePref?.summary = effectiveName
                val bday = SystemSaveGame.getBirthday()
                if (bday.size >= 2 && bday[0] > 0 && bday[1] > 0) {
                    val bdayStr = "%02d/%02d".format(bday[1].toInt(), bday[0].toInt())
                    birthdayPref?.summary = bdayStr
                    prefs?.edit()?.putString("firmware_settings_birthday", bdayStr)?.apply()
                }
                val curCoins = SystemSaveGame.getPlayCoins().coerceIn(0, 300)
                coinsPref?.value = curCoins
                macPref?.summary = SystemSaveGame.getMac().ifBlank { getString(R.string.not_set) }
            }
        } else {
            val fallbackName = SystemSaveGame.sanitizeUsername(
                preferenceManager.sharedPreferences?.getString("firmware_settings_nickname", null)
            )
            nicknamePref?.text = fallbackName
            nicknamePref?.summary = fallbackName
            macPref?.summary = preferenceManager.sharedPreferences?.getString("internal_mac_address", null) ?: getString(R.string.not_set)
        }

        nicknamePref?.setOnBindEditTextListener { editText ->
            editText.filters = arrayOf(android.text.InputFilter.LengthFilter(SystemSaveGame.MAX_USERNAME_UTF16_LEN))
        }

        nicknamePref?.setOnPreferenceChangeListener { _, newValue ->
            val raw = (newValue as? String).orEmpty().trim()
            if (raw.isEmpty()) return@setOnPreferenceChangeListener false
            val sanitized = SystemSaveGame.sanitizeUsername(raw)
            if (coreLoaded) {
                runCatching {
                    SystemSaveGame.setUsername(sanitized)
                    SystemSaveGame.save()
                }
            }
            nicknamePref.text = sanitized
            nicknamePref.summary = sanitized
            preferenceManager.sharedPreferences?.edit()?.putString("firmware_settings_nickname", sanitized)?.apply()
            false
        }

        birthdayPref?.setOnPreferenceChangeListener { _, newValue ->
            val str = (newValue as? String).orEmpty()
            val parts = str.split("/")
            if (parts.size == 2) {
                val first = parts[0].toIntOrNull()
                val second = parts[1].toIntOrNull()
                if (first != null && second != null && coreLoaded) {
                    val month = (if (second in 1..12) second else first).coerceIn(1, 12).toShort()
                    val day = (if (second in 1..12) first else second).coerceIn(1, 31).toShort()
                    runCatching {
                        SystemSaveGame.setBirthday(month, day)
                        SystemSaveGame.save()
                    }
                }
            }
            birthdayPref.summary = str
            true
        }

        coinsPref?.setOnPreferenceChangeListener { _, newValue ->
            val coins = (newValue as? Int ?: 0).coerceIn(0, 300)
            if (coreLoaded) {
                runCatching {
                    SystemSaveGame.setPlayCoins(coins)
                    SystemSaveGame.save()
                }
            }
            true
        }

        regenMacPref?.setOnPreferenceClickListener {
            if (coreLoaded) {
                runCatching {
                    SystemSaveGame.regenerateMac()
                    SystemSaveGame.save()
                    val newMac = SystemSaveGame.getMac()
                    macPref?.summary = newMac
                    preferenceManager.sharedPreferences?.edit()?.putString("internal_mac_address", newMac)?.apply()
                    Toast.makeText(requireContext(), getString(R.string.v780_mac_generated, newMac), Toast.LENGTH_SHORT).show()
                }
            }
            true
        }

        regenIdPref?.setOnPreferenceClickListener {
            if (coreLoaded) {
                runCatching {
                    SystemSaveGame.regenerateConsoleId()
                    SystemSaveGame.save()
                    Toast.makeText(requireContext(), R.string.pref_3ds_console_id_regenerated, Toast.LENGTH_SHORT).show()
                }
            }
            true
        }
    }

    override fun getTitle() = getString(R.string.pref_3ds_system_profile_title)
}
