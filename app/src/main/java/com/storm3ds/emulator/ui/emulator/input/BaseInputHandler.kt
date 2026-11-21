package com.storm3ds.emulator.ui.emulator.input

import android.view.View.OnTouchListener

abstract class BaseInputHandler(protected var inputListener: IInputListener) : OnTouchListener