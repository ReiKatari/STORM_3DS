package com.storm3ds.emulator.migrations

import com.storm3ds.emulator.domain.repositories.RomsRepository

class Migration16to17(private val romsRepository: RomsRepository) : Migration {
    override val from = 16
    override val to = 17

    override fun migrate() {
        romsRepository.invalidateRoms()
    }
}