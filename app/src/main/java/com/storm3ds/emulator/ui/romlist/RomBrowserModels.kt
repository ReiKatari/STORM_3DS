package com.storm3ds.emulator.ui.romlist

import com.storm3ds.emulator.domain.model.RomFilter
import com.storm3ds.emulator.domain.model.RomViewMode
import com.storm3ds.emulator.domain.model.SortingMode
import com.storm3ds.emulator.domain.model.SortingOrder
import com.storm3ds.emulator.domain.model.rom.Rom

sealed class RomBrowserEntry {
    data class Folder(
        val docId: String,
        val name: String,
        val relativePath: String,
        val isRoot: Boolean
    ) : RomBrowserEntry()

    data class RomItem(
        val rom: Rom
    ) : RomBrowserEntry()
}

data class RomBrowserUiState(
    val entries: List<RomBrowserEntry>,
    val breadcrumbs: List<String>,
    val canNavigateUp: Boolean,
    val isSearchActive: Boolean,
    val isAtVirtualRoot: Boolean,
    val searchQuery: String = "",
    val viewMode: RomViewMode = RomViewMode.GRID,
    val filter: RomFilter = RomFilter.ALL,
    val sortingMode: SortingMode = SortingMode.ALPHABETICALLY,
    val sortingOrder: SortingOrder = SortingOrder.ASCENDING,
    val continuePlaying: List<Rom> = emptyList(),
    val quickResumeHasSaveState: Boolean = false,
    val quickResumeSaveTimestamp: Long? = null,
    val alphabetIndex: Map<Char, Int> = emptyMap(),
)
