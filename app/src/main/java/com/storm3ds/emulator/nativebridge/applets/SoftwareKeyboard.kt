package com.storm3ds.emulator.nativebridge.applets

import android.text.InputFilter
import android.text.Spanned
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.Keep
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.storm3ds.emulator.R
import com.storm3ds.emulator.nativebridge.NativeBridgeHost
import com.storm3ds.emulator.nativebridge.NativeLibrary
import com.storm3ds.emulator.nativebridge.utils.Log
import java.io.Serializable

@Keep
object SoftwareKeyboard {
    @JvmField
    var data: KeyboardData = KeyboardData(0, "")
    val finishLock = Object()

    private fun executeImpl(config: KeyboardConfig) {
        val activity = NativeLibrary.sEmulationActivity.get()
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            data = KeyboardData(2, "")
            synchronized(finishLock) { finishLock.notifyAll() }
            return
        }
        val editText = EditText(activity).apply {
            hint = config.hintText ?: ""
            isSingleLine = !config.multilineMode
            val filters = mutableListOf<InputFilter>(Filter())
            if (config.maxTextLength > 0) {
                filters.add(InputFilter.LengthFilter(config.maxTextLength))
            }
            this.filters = filters.toTypedArray()
        }
        val btns = config.buttonText
        val okLabel = btns.lastOrNull()?.takeIf { it.isNotBlank() } ?: activity.getString(android.R.string.ok)
        val cancelLabel = btns.firstOrNull()?.takeIf { it.isNotBlank() } ?: activity.getString(android.R.string.cancel)

        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.software_keyboard)
            .setView(editText)
            .setPositiveButton(okLabel, null)
            .setOnCancelListener {
                data = KeyboardData(0, "")
                synchronized(finishLock) { finishLock.notifyAll() }
            }
        if (config.buttonConfig == ButtonConfig.DUAL || config.buttonConfig == ButtonConfig.TRIPLE) {
            builder.setNegativeButton(cancelLabel) { _, _ ->
                data = KeyboardData(0, "")
                synchronized(finishLock) { finishLock.notifyAll() }
            }
        }
        if (config.buttonConfig == ButtonConfig.TRIPLE && btns.size >= 2) {
            builder.setNeutralButton(btns[1].ifBlank { "?" }) { _, _ ->
                data = KeyboardData(1, "")
                synchronized(finishLock) { finishLock.notifyAll() }
            }
        }
        val dialog = builder.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val txt = editText.text?.toString().orEmpty()
            val err = runCatching { ValidateInput(txt) }.getOrDefault(ValidationError.None)
            if (err != ValidationError.None) {
                editText.error = err.name
                showError(err.name)
                return@setOnClickListener
            }
            val confirmButtonIdx = when (config.buttonConfig) {
                ButtonConfig.SINGLE -> 0
                ButtonConfig.DUAL -> 1
                ButtonConfig.TRIPLE -> 2
                else -> 0
            }
            data = KeyboardData(confirmButtonIdx, txt)
            dialog.dismiss()
            synchronized(finishLock) { finishLock.notifyAll() }
        }
    }

    @Keep
    @JvmStatic
    fun execute(config: KeyboardConfig): KeyboardData {
        if (config.buttonConfig == ButtonConfig.NONE) {
            Log.error("Unexpected button config None")
            return KeyboardData(0, "")
        }
        val activity = NativeLibrary.sEmulationActivity.get() ?: return KeyboardData(0, "")
        activity.runOnUiThread { executeImpl(config) }
        synchronized(finishLock) {
            runCatching { finishLock.wait() }
        }
        return data
    }

    @Keep
    @JvmStatic
    fun showError(error: String) {
        val activity = NativeLibrary.sEmulationActivity.get()
        if (activity != null) {
            activity.runOnUiThread {
                Toast.makeText(activity, error, Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.error("SoftwareKeyboard error: $error")
        }
    }

    @Suppress("FunctionName")
    private external fun ValidateFilters(text: String): ValidationError

    @Suppress("FunctionName")
    external fun ValidateInput(text: String): ValidationError

    interface ButtonConfig {
        companion object {
            const val SINGLE = 0
            const val DUAL = 1
            const val TRIPLE = 2
            const val NONE = 3
        }
    }

    @Keep
    enum class ValidationError {
        None,
        ButtonOutOfRange,
        MaxDigitsExceeded,
        AtSignNotAllowed,
        PercentNotAllowed,
        BackslashNotAllowed,
        ProfanityNotAllowed,
        CallbackFailed,
        FixedLengthRequired,
        MaxLengthExceeded,
        BlankInputNotAllowed,
        EmptyInputNotAllowed
    }

    @Keep
    class KeyboardConfig : Serializable {
        @JvmField var buttonConfig: Int = 0
        @JvmField var maxTextLength: Int = 0
        @JvmField var multilineMode: Boolean = false
        @JvmField var hintText: String? = null
        @JvmField var buttonText: Array<String> = emptyArray()
    }

    @Keep
    class KeyboardData(@JvmField var button: Int, @JvmField var text: String)

    class Filter : InputFilter {
        override fun filter(
            source: CharSequence,
            start: Int,
            end: Int,
            dest: Spanned,
            dstart: Int,
            dend: Int
        ): CharSequence? {
            val text = StringBuilder(dest)
                .replace(dstart, dend, source.subSequence(start, end).toString())
                .toString()
            return if (runCatching { ValidateFilters(text) }.getOrDefault(ValidationError.None) == ValidationError.None) {
                null
            } else {
                dest.subSequence(dstart, dend)
            }
        }
    }
}
