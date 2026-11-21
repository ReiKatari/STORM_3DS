package com.storm3ds.emulator.impl.romprocessors

import android.content.Context
import com.storm3ds.emulator.common.romprocessors.RarRomFileProcessor
import com.storm3ds.emulator.common.romprocessors.RomFileProcessor
import com.storm3ds.emulator.common.romprocessors.SevenZRomFileProcessor
import com.storm3ds.emulator.common.romprocessors.ThreeDsRomFileProcessor
import com.storm3ds.emulator.common.romprocessors.ZipRomFileProcessor
import com.storm3ds.emulator.common.uridelegates.UriHandler
import com.storm3ds.emulator.domain.model.ThreeDS
import com.storm3ds.emulator.impl.NdsRomCache

class Api24RomFileProcessorFactory(
    context: Context,
    uriHandler: UriHandler,
    ndsRomCache: NdsRomCache,
) : BaseRomFileProcessorFactory(context) {

    private val prefixProcessorMap: Map<String, RomFileProcessor>

    init {
        val threeDsProcessor = ThreeDsRomFileProcessor(context, uriHandler)
        val zipProcessor = ZipRomFileProcessor(context, uriHandler, ndsRomCache)
        val map = HashMap<String, RomFileProcessor>()
        for (ext in ThreeDS.NATIVE_3DS_EXTENSIONS) {
            map[ext] = threeDsProcessor
        }
        map["zip"] = zipProcessor
        map["7z"] = SevenZRomFileProcessor(context, uriHandler, ndsRomCache)
        map["rar"] = RarRomFileProcessor(context, uriHandler, ndsRomCache)
        prefixProcessorMap = map
    }

    override fun getRomFileProcessorForFileExtension(extension: String): RomFileProcessor? =
        prefixProcessorMap[extension.lowercase()]
}
