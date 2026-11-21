package com.storm3ds.emulator.ui.emulator.model

import com.storm3ds.emulator.domain.model.Input

sealed class ConnectedControllersState {
    data object NoControllers : ConnectedControllersState()
    data class ControllersConnected(val assignedInputs: List<Input>) : ConnectedControllersState()
}