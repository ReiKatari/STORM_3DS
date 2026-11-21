package com.storm3ds.emulator.impl.image

import android.graphics.Bitmap
import android.net.Uri

interface BitmapLoader {
    fun loadAsBitmap(imageUri: Uri): Bitmap?
}