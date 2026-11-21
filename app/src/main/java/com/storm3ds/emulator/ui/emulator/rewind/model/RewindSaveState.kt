package com.storm3ds.emulator.ui.emulator.rewind.model

import android.graphics.Bitmap
import com.storm3ds.emulator.utils.DsScreenshotConverter
import java.nio.ByteBuffer

class RewindSaveState(
    val buffer: ByteBuffer,
    val bufferContentSize: Long,
    val screenshotBuffer: ByteBuffer,
    val frame: Int,
) {
    val screenshot: Bitmap get() = DsScreenshotConverter.fromByteBufferToBitmap(screenshotBuffer)
}