package com.storm3ds.emulator.extensions

fun <T> MutableList<T>.removeFirst(predicate: (T) -> Boolean): T? {
    val itemIndex = indexOfFirst(predicate)
    return if (itemIndex >= 0) {
        removeAt(itemIndex)
    } else {
        null
    }
}