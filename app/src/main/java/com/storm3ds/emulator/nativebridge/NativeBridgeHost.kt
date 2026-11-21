package com.storm3ds.emulator.nativebridge

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.fragment.app.FragmentActivity
import com.storm3ds.emulator.MelonDSApplication
import com.storm3ds.emulator.nativebridge.utils.DocumentsTree
import java.io.File
import java.lang.ref.WeakReference

object NativeBridgeHost {
    val documentsTree = DocumentsTree()
    @Volatile
    var compressProgressCallback: ((Long, Long) -> Unit)? = null
    @Volatile
    var shaderProgressCallback: ((String, Int, Int, String) -> Unit)? = null
    @Volatile
    var onEmulationStartedCallback: (() -> Unit)? = null
    @Volatile
    var openImagePickerCallback: (() -> Unit)? = null
    @Volatile
    var netPlayMessageCallback: ((Int, String) -> Unit)? = null

    val appContext: Context
        get() = MelonDSApplication.appContext
            ?: error("Application context not initialized yet")

    var emulationActivity: WeakReference<FragmentActivity?>
        get() = NativeLibrary.sEmulationActivity
        set(value) {
            NativeLibrary.sEmulationActivity = value
        }

    fun resolveUserDirectoryPath(): String {
        val ext = Environment.getExternalStorageDirectory()
        val candidate = File(ext, "STORM 3DS")
        if (candidate.exists() || candidate.mkdirs()) {
            val probe = File(candidate, ".probe")
            val ok = runCatching {
                probe.writeText("ok")
                probe.delete()
                true
            }.getOrDefault(false)
            if (ok) {
                ensureSubdirs(candidate)
                return candidate.absolutePath
            }
        }
        val appDir = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "STORM 3DS")
        appDir.mkdirs()
        ensureSubdirs(appDir)
        return appDir.absolutePath
    }

    private fun ensureSubdirs(root: File) {
        for (sub in listOf(
            "config", "sysdata", "sdmc", "nand", "states", "cheats",
            "load/mods", "load/textures", "shaders", "log", "amiibo", "screenshots", "saves"
        )) {
            File(root, sub).mkdirs()
        }
    }

    fun getDocumentsRootUri(): Uri? {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(appContext)
        val raw = prefs.getString("CITRA_DIRECTORY", null) ?: return null
        return runCatching { Uri.parse(raw) }.getOrNull()
    }
}
