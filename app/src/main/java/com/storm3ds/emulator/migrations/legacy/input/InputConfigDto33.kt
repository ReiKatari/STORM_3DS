package com.storm3ds.emulator.migrations.legacy.input

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.storm3ds.emulator.domain.model.Input

@Serializable
data class InputConfigDto33(
    @SerialName("a") val input: Input,
    @SerialName("b") val key: Int,
)