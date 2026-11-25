package com.storm3ds.emulator.nativebridge.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.Keep
import com.storm3ds.emulator.nativebridge.NativeBridgeHost

@Keep
object StillImageCameraHelper {
    private val filePickerLock = Object()
    @Volatile
    private var filePickerPath: String? = null
    @Volatile
    private var filePickerCompleted: Boolean = false

    @Keep
    @JvmStatic
    fun openFilePicker(): String? {
        val cb = NativeBridgeHost.openImagePickerCallback ?: return null
        val activity = NativeBridgeHost.emulationActivity.get() ?: return null
        synchronized(filePickerLock) {
            filePickerPath = null
            filePickerCompleted = false
        }
        activity.runOnUiThread { cb.invoke() }
        synchronized(filePickerLock) {
            val deadline = System.currentTimeMillis() + 120_000L
            while (!filePickerCompleted) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0L) {
                    break
                }
                runCatching { filePickerLock.wait(remaining) }
            }
        }
        return filePickerPath
    }

    @JvmStatic
    fun onFilePickerResult(result: String?) {
        synchronized(filePickerLock) {
            filePickerPath = result?.takeIf { it.isNotBlank() }
            filePickerCompleted = true
            filePickerLock.notifyAll()
        }
    }

    @Keep
    @JvmStatic
    fun loadImageFromFile(uri: String?, width: Int, height: Int): Bitmap? {
        if (uri.isNullOrBlank()) return null
        return runCatching {
            val ctx = NativeBridgeHost.appContext
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = ctx.contentResolver.openInputStream(Uri.parse(uri))?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return null
            val argbRaw = if (raw.config == Bitmap.Config.ARGB_8888) {
                raw
            } else {
                raw.copy(Bitmap.Config.ARGB_8888, false).also { raw.recycle() }
            }
            val targetW = width.coerceAtLeast(1)
            val targetH = height.coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(argbRaw, targetW, targetH, true)
            if (scaled !== argbRaw) {
                argbRaw.recycle()
            }
            if (scaled.config == Bitmap.Config.ARGB_8888) {
                scaled
            } else {
                scaled.copy(Bitmap.Config.ARGB_8888, false).also { scaled.recycle() }
            }
        }.getOrNull()
    }
}
