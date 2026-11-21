package com.storm3ds.emulator.domain.model.retroachievements.exception

class RAGameNotExist(gameHash: String) : Exception("There is no game for hash $gameHash")