package com.storm3ds.emulator.nativebridge.model

import android.net.Uri
import android.provider.DocumentsContract

class CheapDocument(val filename: String, val mimeType: String, val uri: Uri) {
    val isDirectory: Boolean
        get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
}
