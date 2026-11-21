package com.storm3ds.emulator.ui.cheats.model

sealed class CheatsScreenUiState<T> {
    class Loading<T> : CheatsScreenUiState<T>()
    data class Ready<T>(val data: T) : CheatsScreenUiState<T>()
}