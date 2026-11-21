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

    @Keep
    @JvmStatic
    fun openFilePicker(): String? {
        val cb = NativeBridgeHost.openImagePickerCallback ?: return null
        NativeBridgeHost.emulationActivity.get()?.runOnUiThread { cb.invoke() } ?: return null
        synchronized(filePickerLock) {
            runCatching { filePickerLock.wait(15_000L) }
        }
        return filePickerPath
    }

    @JvmStatic
    fun onFilePickerResult(result: String) {
        filePickerPath = result
        synchronized(filePickerLock) { filePickerLock.notifyAll() }
    }

    @Keep
    @JvmStatic
    fun loadImageFromFile(uri: String?, width: Int, height: Int): Bitmap? {
        if (uri.isNullOrBlank()) return null
        return runCatching {
            val ctx = NativeBridgeHost.appContext
            val raw = ctx.contentResolver.openInputStream(Uri.parse(uri))?.use {
                BitmapFactory.decodeStream(it)
            } ?: return null
            Bitmap.createScaledBitmap(raw, width.coerceAtLeast(1), height.coerceAtLeast(1), true)
        }.getOrNull()
    }
}
