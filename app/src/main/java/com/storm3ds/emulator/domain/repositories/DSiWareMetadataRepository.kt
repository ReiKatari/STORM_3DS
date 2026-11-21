package com.storm3ds.emulator.domain.repositories

interface DSiWareMetadataRepository {
    suspend fun getDSiWareTitleMetadata(categoryId: UInt, titleId: UInt): ByteArray
}