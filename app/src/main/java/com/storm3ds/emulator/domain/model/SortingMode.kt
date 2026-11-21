package com.storm3ds.emulator.domain.model

enum class SortingMode(val defaultOrder: SortingOrder) {
    ALPHABETICALLY(SortingOrder.ASCENDING),
    RECENTLY_PLAYED(SortingOrder.DESCENDING),
    MOST_PLAYED(SortingOrder.DESCENDING),
}