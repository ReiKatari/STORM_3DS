package com.storm3ds.emulator.nativebridge

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.view.Surface
import androidx.annotation.Keep
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.storm3ds.emulator.BuildConfig
import com.storm3ds.emulator.R
import com.storm3ds.emulator.nativebridge.model.GameMediaType
import com.storm3ds.emulator.nativebridge.utils.BuildUtil
import com.storm3ds.emulator.nativebridge.utils.FileUtil
import com.storm3ds.emulator.nativebridge.utils.GraphicsUtil
import com.storm3ds.emulator.nativebridge.utils.Log
import com.storm3ds.emulator.nativebridge.utils.NetPlayManager
import com.storm3ds.emulator.nativebridge.utils.RemovableStorageHelper
import java.io.File
import java.lang.ref.WeakReference
import java.util.Date

@Keep
object NativeLibrary {
    const val TOUCHSCREEN_DEVICE = "Touchscreen"
    const val SAVESTATE_SLOT_COUNT = 11
    const val QUICKSAVE_SLOT = 0
    const val REQUEST_CODE_NATIVE_CAMERA = 800
    const val REQUEST_CODE_NATIVE_MIC = 900

    @JvmField
    var sEmulationActivity = WeakReference<FragmentActivity?>(null)
    @Volatile
    var onCoreExitRequested: ((CoreError) -> Unit)? = null
    @Volatile
    var onPermissionNeeded: ((String, Int) -> Unit)? = null

    private var alertResult = false
    val alertLock = Object()
    private var coreErrorAlertResult = false
    private val coreErrorAlertLock = Object()
    private val cameraPermissionLock = Object()
    private var cameraPermissionGranted = false
    private val micPermissionLock = Object()
    private var micPermissionGranted = false

    @Volatile
    var loaded: Boolean = false
        private set

    init {
        ensureLoaded()
    }

    fun ensureLoaded(): Boolean {
        if (loaded) return true
        return synchronized(this) {
            if (!loaded) {
                loaded = runCatching {
                    System.loadLibrary("storm3ds")
                    true
                }.getOrElse { ex ->
                    android.util.Log.e("NativeLibrary", "Failed loading libstorm3ds.so", ex)
                    false
                }
            }
            loaded
        }
    }

    // --- Core Input & Touch ---
    external fun onGamePadEvent(device: String, button: Int, action: Int): Boolean
    external fun onGamePadMoveEvent(device: String, axis: Int, xAxis: Float, yAxis: Float): Boolean
    external fun onGamePadAxisEvent(device: String?, axisId: Int, axisVal: Float): Boolean
    external fun onTouchEvent(xAxis: Float, yAxis: Float, pressed: Boolean): Boolean
    external fun onTouchMoved(xAxis: Float, yAxis: Float)
    external fun onSecondaryTouchEvent(xAxis: Float, yAxis: Float, pressed: Boolean): Boolean
    external fun onSecondaryTouchMoved(xAxis: Float, yAxis: Float)

    // --- Core Configuration, ROMs, Surfaces, Lifecycle ---
    external fun reloadSettings()
    external fun getTitleId(filename: String): Long
    external fun getIsSystemTitle(path: String): Boolean
    external fun setUserDirectory(directory: String)
    private external fun getInstalledGamePathsImpl(): Array<String?>
    external fun createConfigFile()
    external fun createLogFile()
    external fun logUserDirectory(directory: String)
    external fun setInsertedCartridge(path: String)
    external fun run(path: String)
    external fun surfaceChanged(surf: Surface)
    external fun surfaceDestroyed()
    external fun secondarySurfaceChanged(secondary_surface: Surface)
    external fun secondarySurfaceDestroyed()
    external fun unPauseEmulation()
    external fun pauseEmulation()
    external fun stopEmulation()
    external fun isRunning(): Boolean
    external fun getRunningTitleId(): Long
    external fun getPerfStats(): DoubleArray
    external fun updateFramebuffer(isPortrait: Boolean)
    external fun swapScreens(swapScreens: Boolean, rotation: Int)
    external fun notifyOrientationChange(layoutOption: Int, rotation: Int, isPortrait: Boolean)
    external fun initializeGpuDriver(
        hookLibDir: String?,
        customDriverDir: String?,
        customDriverName: String?,
        fileRedirectDir: String?
    )
    external fun queryVulkanGpuInfoJson(): String
    external fun isVulkanSupported(): Boolean
    external fun areKeysAvailable(): Boolean
    external fun getHomeMenuPath(region: Int): String
    external fun getSystemTitleIds(systemType: Int, region: Int): LongArray
    external fun areSystemTitlesInstalled(): BooleanArray
    external fun uninstallSystemFiles(old3DS: Boolean)
    external fun isFullConsoleLinked(): Boolean
    external fun unlinkConsole()
    external fun setTemporaryFrameLimit(speed: Double)
    external fun disableTemporaryFrameLimit()
    external fun playTimeManagerInit()
    external fun playTimeManagerStart(titleId: Long)
    external fun playTimeManagerStop()
    external fun playTimeManagerGetPlayTime(titleId: Long): Long
    external fun playTimeManagerGetCurrentTitleId(): Long
    private external fun uninstallTitle(titleId: Long, mediaType: Int): Boolean
    external fun nativeFileExists(path: String): Boolean
    external fun deleteOpenGLShaderCache(titleId: Long)
    external fun deleteVulkanShaderCache(titleId: Long)
    external fun addDefaultSetting(key: String, value: String)
    external fun reloadCameraDevices()
    external fun loadAmiibo(path: String?): Boolean
    external fun removeAmiibo()
    external fun getSavestateInfo(): Array<SaveStateInfo>?
    external fun saveState(slot: Int)
    external fun loadState(slot: Int)
    external fun saveStateSync(slot: Int): Boolean
    external fun loadStateSync(slot: Int): Boolean
    external fun stepSingleFrame(): Boolean
    external fun logDeviceInfo()
    private external fun compressFileNative(inputPath: String?, outputPath: String): Int
    private external fun decompressFileNative(inputPath: String?, outputPath: String): Int
    external fun getRecommendedExtension(inputPath: String?, shouldCompress: Boolean): String
    external fun initMultiplayer()

    // --- STORM 3DS Custom Native Extensions ---
    external fun searchMemoryRegion(
        startAddr: Long,
        stopAddr: Long,
        valueType: Int,
        searchType: Int,
        scanType: Int,
        value: Long
    ): IntArray
    external fun getSearchResults(): IntArray
    external fun resetSearchResults()
    external fun readMemoryValue(vaddr: Long, sizeBytes: Int): Long
    external fun writeMemoryValue(vaddr: Long, sizeBytes: Int, value: Long)

    external fun setRewindBudgetMb(budgetMb: Int)
    external fun clearRewindBuffer()
    external fun captureRewindSnapshot(): Boolean
    external fun rewindStepsBack(stepsBack: Int): Boolean
    external fun getRewindTimelineTimestamps(): LongArray
    external fun getRewindMemoryBytes(): Long

    external fun setGyro3dParams(tiltX: Float, tiltY: Float, strength: Float)
    external fun setFactor3d(factor: Int)
    external fun setRender3dMode(mode: Int)
    external fun setDisableRightEyeRender(enabled: Boolean)
    external fun setLibrashaderPreset(enabled: Boolean, presetPath: String)
    external fun setSpeedhacks(
        fmvHack: Boolean,
        skipSlowDraw: Boolean,
        skipTextureCopy: Boolean,
        accurateMul: Boolean,
        audioStretching: Boolean,
        separableShader: Boolean
    )
    external fun setSpirvOptimization(mode: Int, validation: Boolean, legalization: Boolean)
    external fun setCustomScreenLayout(
        isPortrait: Boolean,
        topX: Int,
        topY: Int,
        topW: Int,
        topH: Int,
        botX: Int,
        botY: Int,
        botW: Int,
        botH: Int,
        secondLayerOpacity: Int
    )
    external fun getAesKeyStatusMask(): Int
    external fun createBundleNative(outPath: String, mainFile: String, deps: Array<String>): Int
    external fun setLogFilterAndRegex(filter: String, regex: String)
    external fun setStormJitOptions(jitMode: Int, codeCacheMb: Int)
    external fun setMicrophoneEnabled(enabled: Boolean)
    external fun setMicrophoneInputType(inputType: Int)
    external fun setAudioMuted(muted: Boolean)
    external fun installCiaSimple(path: String): Int
    external fun getCiaTitleId(path: String): Long
    external fun areAllBciaTitlesInstalled(path: String): Boolean
    external fun setAdrenoTurbo(enabled: Boolean): Boolean
    external fun findInstalledTitlePath(titleId: Long): String

    data class InstalledGame(val path: String, val mediaType: GameMediaType)

    fun getInstalledGamePaths(): Array<InstalledGame> {
        val games = runCatching { getInstalledGamePathsImpl() }.getOrElse { return emptyArray() }
        return games.mapNotNull { entry ->
            entry?.let {
                val sep = it.lastIndexOf('|')
                if (sep == -1) return@mapNotNull null
                val path = it.substring(0, sep)
                val mediaType = GameMediaType.fromInt(it.substring(sep + 1).toIntOrNull() ?: 0)
                InstalledGame(path, mediaType ?: GameMediaType.UNKNOWN)
            }
        }.toTypedArray()
    }

    fun uninstallTitle(titleId: Long, mediaType: GameMediaType): Boolean =
        uninstallTitle(titleId, mediaType.value)

    fun compressFile(inputPath: String?, outputPath: String): CompressStatus =
        CompressStatus.fromValue(compressFileNative(inputPath, outputPath))

    fun decompressFile(inputPath: String?, outputPath: String): CompressStatus =
        CompressStatus.fromValue(decompressFileNative(inputPath, outputPath))

    fun loadStateIfAvailable(slot: Int): Boolean {
        val states = getSavestateInfo() ?: return false
        if (states.any { it.slot == slot }) {
            return loadStateSync(slot)
        }
        return false
    }

    fun setEmulationActivity(activity: FragmentActivity?) {
        sEmulationActivity = WeakReference(activity)
    }

    fun clearEmulationActivity(activity: FragmentActivity? = null) {
        if (activity == null || sEmulationActivity.get() === activity) {
            sEmulationActivity.clear()
        }
    }

    fun resolveNativeOrContentPath(uri: Uri): String {
        if (uri.scheme == "file") {
            val path = uri.path.orEmpty()
            return if (path.isNotBlank()) "!$path" else uri.toString()
        }
        val uriStr = uri.toString()
        val direct = File(uriStr)
        if (direct.exists() && direct.canRead()) {
            return "!${direct.absolutePath}"
        }
        val native = runCatching { getNativePath(uri) }.getOrDefault("")
        if (native.isNotBlank()) {
            val f = File(native)
            if (f.exists() && f.canRead()) {
                return "!${f.absolutePath}"
            }
        }
        return uriStr
    }

    fun cameraPermissionResult(granted: Boolean) {
        cameraPermissionGranted = granted
        synchronized(cameraPermissionLock) { cameraPermissionLock.notifyAll() }
    }

    fun micPermissionResult(granted: Boolean) {
        micPermissionGranted = granted
        synchronized(micPermissionLock) { micPermissionLock.notifyAll() }
    }

    // --- Callbacks invoked from C++ JNI (id_cache.cpp & android_utils.cpp) ---
    @Keep
    @JvmStatic
    fun onCoreError(error: CoreError?, details: String): Boolean {
        val activity = sEmulationActivity.get() ?: return false
        activity.runOnUiThread {
            val themedContext = ContextThemeWrapper(activity, R.style.AppTheme)
            val builder = MaterialAlertDialogBuilder(themedContext)
                .setTitle(activity.getString(error?.stringRes ?: R.string.fatal_error))
                .setMessage(details.ifBlank { activity.getString(R.string.fatal_error_message) })
                .setPositiveButton(R.string.continue_button) { _, _ ->
                    coreErrorAlertResult = true
                    synchronized(coreErrorAlertLock) { coreErrorAlertLock.notifyAll() }
                }
                .setNegativeButton(R.string.abort_button) { _, _ ->
                    coreErrorAlertResult = false
                    synchronized(coreErrorAlertLock) { coreErrorAlertLock.notifyAll() }
                }
                .setCancelable(false)
            if (error == CoreError.ErrorShaderCacheCrash) {
                builder.setNeutralButton(R.string.pref_3ds_delete_shader_cache) { _, _ ->
                    runCatching {
                        val tid = getRunningTitleId()
                        if (tid != 0L) {
                            deleteVulkanShaderCache(tid)
                            deleteOpenGLShaderCache(tid)
                        }
                    }
                    coreErrorAlertResult = false
                    synchronized(coreErrorAlertLock) { coreErrorAlertLock.notifyAll() }
                }
            }
            builder.show()
        }
        synchronized(coreErrorAlertLock) {
            runCatching { coreErrorAlertLock.wait() }
        }
        return coreErrorAlertResult
    }

    @Keep
    @JvmStatic
    fun isPortraitMode(): Boolean {
        val ctx = NativeBridgeHost.appContext
        return ctx.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    }

    @Keep
    @JvmStatic
    fun displayAlertMsg(title: String, message: String, yesNo: Boolean): Boolean {
        val activity = sEmulationActivity.get() ?: return false
        var result = false
        activity.runOnUiThread {
            val builder = MaterialAlertDialogBuilder(ContextThemeWrapper(activity, R.style.AppTheme))
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
            if (yesNo) {
                alertResult = false
                builder.setPositiveButton(android.R.string.ok) { _, _ ->
                    alertResult = true
                    synchronized(alertLock) { alertLock.notifyAll() }
                }.setNegativeButton(android.R.string.cancel) { _, _ ->
                    alertResult = false
                    synchronized(alertLock) { alertLock.notifyAll() }
                }
            } else {
                builder.setPositiveButton(android.R.string.ok) { _, _ ->
                    synchronized(alertLock) { alertLock.notifyAll() }
                }
            }
            builder.show()
        }
        synchronized(alertLock) {
            runCatching { alertLock.wait() }
        }
        if (yesNo) result = alertResult
        return result
    }

    @Keep
    @JvmStatic
    fun exitEmulationActivity(resultCode: Int) {
        val err = CoreError.fromInt(resultCode)
        val activity = sEmulationActivity.get()
        if (err == CoreError.ShutdownRequested) {
            activity?.runOnUiThread { activity.finish() }
            return
        }
        onCoreExitRequested?.invoke(err)
        activity?.runOnUiThread {
            MaterialAlertDialogBuilder(ContextThemeWrapper(activity, R.style.AppTheme))
                .setTitle(R.string.loader_error_generic_title)
                .setMessage(activity.getString(err.stringRes))
                .setPositiveButton(android.R.string.ok) { _, _ -> activity.finish() }
                .setCancelable(false)
                .show()
        }
    }

    @Keep
    @JvmStatic
    fun requestCameraPermission(): Boolean {
        val activity = sEmulationActivity.get() ?: return false
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            return true
        }
        activity.runOnUiThread {
            activity.requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CODE_NATIVE_CAMERA)
        }
        synchronized(cameraPermissionLock) { runCatching { cameraPermissionLock.wait() } }
        return cameraPermissionGranted
    }

    @Keep
    @JvmStatic
    fun requestMicPermission(): Boolean {
        val activity = sEmulationActivity.get() ?: return false
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            return true
        }
        activity.runOnUiThread {
            activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE_NATIVE_MIC)
        }
        synchronized(micPermissionLock) { runCatching { micPermissionLock.wait() } }
        return micPermissionGranted
    }

    @Keep
    @JvmStatic
    fun addNetPlayMessage(type: Int, message: String) {
        NativeBridgeHost.netPlayMessageCallback?.invoke(type, message)
        NetPlayManager.addNetPlayMessage(type, message)
    }

    @Keep
    @JvmStatic
    fun clearChat() {
        NetPlayManager.clearChat()
    }

    @Keep
    @JvmStatic
    fun onCompressProgress(total: Long, current: Long) {
        NativeBridgeHost.compressProgressCallback?.invoke(total, current)
    }

    @Keep
    @JvmStatic
    fun createFile(directory: String, filename: String): Boolean =
        if (FileUtil.isNativePath(directory)) {
            runCatching { NativeBridgeHost.documentsTree.createFile(directory, filename) }.getOrDefault(false)
        } else {
            FileUtil.createFile(directory, filename) != null
        }

    @Keep
    @JvmStatic
    fun createDir(directory: String, directoryName: String): Boolean =
        if (FileUtil.isNativePath(directory)) {
            runCatching { NativeBridgeHost.documentsTree.createDir(directory, directoryName) }.getOrDefault(false)
        } else {
            FileUtil.createDir(directory, directoryName) != null
        }

    @Keep
    @JvmStatic
    fun openContentUri(path: String, openMode: String): Int =
        if (FileUtil.isNativePath(path)) {
            runCatching { NativeBridgeHost.documentsTree.openContentUri(path, openMode) }.getOrDefault(-1)
        } else {
            FileUtil.openContentUri(path, openMode)
        }

    @Keep
    @JvmStatic
    fun getFilesName(path: String): Array<String?> =
        if (FileUtil.isNativePath(path)) {
            runCatching { NativeBridgeHost.documentsTree.getFilesName(path) }.getOrDefault(emptyArray())
        } else {
            FileUtil.getFilesName(path)
        }

    @Keep
    @JvmStatic
    fun getNativePath(uri: Uri): String {
        BuildUtil.assertNotGooglePlay()
        val uriString = uri.toString()
        if (!uriString.contains(":")) return uriString
        if (uri.scheme == "file") return uri.path ?: ""
        val pathSegment = uri.lastPathSegment ?: return ""
        val virtualPath = pathSegment.substringAfter(":")
        if (pathSegment.startsWith("primary:")) {
            return Environment.getExternalStorageDirectory().absolutePath + "/" + virtualPath
        }
        val context = NativeBridgeHost.appContext
        if (uriString.startsWith("content://com.android.providers.downloads.documents/document/msf")) {
            var fileName: String? = null
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) fileName = c.getString(0)
            }
            if (fileName == null) return ""
            return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath + "/" + fileName
        }
        val storageId = pathSegment.substringBefore(":")
        val removable = RemovableStorageHelper.getRemovableStoragePath(context, storageId) ?: return ""
        return "$removable/$virtualPath"
    }

    @Keep
    @JvmStatic
    fun getUserDirectory(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(NativeBridgeHost.appContext)
        val saved = prefs.getString("CITRA_DIRECTORY", "")
        if (!saved.isNullOrBlank()) {
            val resolved = getNativePath(saved.toUri())
            if (resolved.isNotBlank()) return resolved
        }
        return NativeBridgeHost.resolveUserDirectoryPath()
    }

    @Keep
    @JvmStatic
    fun getSize(path: String): Long =
        if (FileUtil.isNativePath(path)) {
            runCatching { NativeBridgeHost.documentsTree.getFileSize(path) }.getOrDefault(0L)
        } else {
            FileUtil.getFileSize(path)
        }

    @Keep
    @JvmStatic
    fun getBuildFlavor(): String = "vanilla"

    @Keep
    @JvmStatic
    fun isUsingAngleForOpenGL(): Boolean = GraphicsUtil.isUsingAngleForOpenGL()

    @Keep
    @JvmStatic
    fun fileExists(path: String): Boolean =
        if (FileUtil.isNativePath(path)) {
            File(path).exists() || runCatching { NativeBridgeHost.documentsTree.exists(path) }.getOrDefault(false)
        } else {
            FileUtil.exists(path)
        }

    @Keep
    @JvmStatic
    fun isDirectory(path: String): Boolean =
        if (FileUtil.isNativePath(path)) {
            File(path).isDirectory || runCatching { NativeBridgeHost.documentsTree.isDirectory(path) }.getOrDefault(false)
        } else {
            FileUtil.isDirectory(path)
        }

    @Keep
    @JvmStatic
    fun copyFile(sourcePath: String, destinationParentPath: String, destinationFilename: String): Boolean =
        if (FileUtil.isNativePath(sourcePath) && FileUtil.isNativePath(destinationParentPath)) {
            runCatching { NativeBridgeHost.documentsTree.copyFile(sourcePath, destinationParentPath, destinationFilename) }.getOrDefault(false)
        } else {
            FileUtil.copyFile(Uri.parse(sourcePath), Uri.parse(destinationParentPath), destinationFilename)
        }

    @Keep
    @JvmStatic
    fun renameFile(path: String, destinationFilename: String): Boolean =
        if (FileUtil.isNativePath(path)) {
            runCatching { NativeBridgeHost.documentsTree.renameFile(path, destinationFilename) }.getOrDefault(false)
        } else {
            FileUtil.renameFile(path, destinationFilename)
        }

    @Keep
    @JvmStatic
    fun updateDocumentLocation(sourcePath: String, destinationPath: String): Boolean =
        NativeBridgeHost.documentsTree.updateDocumentLocation(sourcePath, destinationPath)

    @Keep
    @JvmStatic
    fun moveFile(filename: String, sourceDirPath: String, destinationDirPath: String): Boolean =
        if (FileUtil.isNativePath(sourceDirPath)) {
            runCatching { NativeBridgeHost.documentsTree.moveFile(filename, sourceDirPath, destinationDirPath) }.getOrDefault(false)
        } else {
            FileUtil.moveFile(filename, sourceDirPath, destinationDirPath)
        }

    @Keep
    @JvmStatic
    fun deleteDocument(path: String): Boolean =
        if (FileUtil.isNativePath(path)) {
            runCatching { NativeBridgeHost.documentsTree.deleteDocument(path) }.getOrDefault(false)
        } else {
            FileUtil.deleteDocument(path)
        }

    @Keep
    enum class CoreError(val value: Int, @StringRes val stringRes: Int) {
        Success(0, R.string.core_error_success),
        ErrorNotInitialized(1, R.string.core_error_not_initialized),
        ErrorGetLoader(2, R.string.core_error_get_loader),
        ErrorSystemMode(3, R.string.core_error_system_mode),
        ErrorLoader(4, R.string.core_error_loader),
        ErrorLoaderErrorEncrypted(5, R.string.core_error_loader_encrypted),
        ErrorLoaderErrorInvalidFormat(6, R.string.core_error_loader_invalid_format),
        ErrorLoaderErrorGBATitle(7, R.string.core_error_loader_gba_title),
        ErrorLoaderErrorPatches(8, R.string.core_error_loader_error_patches),
        ErrorLoaderErrorPatchesInvalidTitle(9, R.string.core_error_loader_patches_invalid_title),
        ErrorSystemFiles(10, R.string.core_error_system_files),
        ErrorSavestate(11, R.string.core_error_savestate),
        ErrorArticDisconnected(12, R.string.core_error_artic_disconnected),
        ErrorN3DSApplication(13, R.string.core_error_n3ds_application),
        ErrorCoreExceptionRaised(14, R.string.core_error_core_exception_raised),
        ErrorSavestateBuildMismatch(15, R.string.core_error_savestate_build_mismatch),
        ShutdownRequested(16, R.string.core_error_shutdown_requested),
        ErrorShaderCacheCrash(17, R.string.core_error_shader_cache_crash),
        ErrorUnknown(18, R.string.core_error_unknown);

        companion object {
            fun fromInt(value: Int): CoreError = entries.find { it.value == value } ?: ErrorUnknown
        }
    }

    @Keep
    enum class InstallStatus {
        Success,
        ErrorFailedToOpenFile,
        ErrorFileNotFound,
        ErrorAborted,
        ErrorInvalid,
        ErrorEncrypted,
        ErrorCannotCheckBundle,
        Cancelled
    }

    @Keep
    class SaveStateInfo {
        @JvmField var slot: Int = 0
        @JvmField var time: Date? = null
    }

    enum class CompressStatus(val value: Int) {
        SUCCESS(0),
        COMPRESS_UNSUPPORTED(1),
        COMPRESS_ALREADY_COMPRESSED(2),
        COMPRESS_FAILED(3),
        DECOMPRESS_UNSUPPORTED(4),
        DECOMPRESS_NOT_COMPRESSED(5),
        DECOMPRESS_FAILED(6),
        INSTALLED_APPLICATION(7);

        companion object {
            fun fromValue(value: Int): CompressStatus =
                entries.firstOrNull { it.value == value } ?: COMPRESS_FAILED
        }
    }

    object ButtonType {
        const val BUTTON_A = 700
        const val BUTTON_B = 701
        const val BUTTON_X = 702
        const val BUTTON_Y = 703
        const val BUTTON_START = 704
        const val BUTTON_SELECT = 705
        const val BUTTON_HOME = 706
        const val BUTTON_ZL = 707
        const val BUTTON_ZR = 708
        const val DPAD_UP = 709
        const val DPAD_DOWN = 710
        const val DPAD_LEFT = 711
        const val DPAD_RIGHT = 712
        const val STICK_LEFT = 713
        const val STICK_LEFT_UP = 714
        const val STICK_LEFT_DOWN = 715
        const val STICK_LEFT_LEFT = 716
        const val STICK_LEFT_RIGHT = 717
        const val STICK_C = 718
        const val STICK_C_UP = 719
        const val STICK_C_DOWN = 720
        const val STICK_C_LEFT = 771
        const val STICK_C_RIGHT = 772
        const val TRIGGER_L = 773
        const val TRIGGER_R = 774
        const val DPAD = 780
        const val BUTTON_DEBUG = 781
        const val BUTTON_GPIO14 = 782
    }

    object ButtonState {
        const val RELEASED = 0
        const val PRESSED = 1
    }

    enum class StereoMode(val nativeValue: Int) {
        OFF(0),
        SIDE_BY_SIDE(1),
        SIDE_BY_SIDE_FULL(2),
        ANAGLYPH(3),
        INTERLACED(4),
        REVERSE_INTERLACED(5),
        CARDBOARD_VR(6),
        GYRO_PARALLAX(7);
    }
}
