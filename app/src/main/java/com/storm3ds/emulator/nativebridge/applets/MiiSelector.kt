package com.storm3ds.emulator.nativebridge.applets

import android.widget.ArrayAdapter
import androidx.annotation.Keep
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.storm3ds.emulator.nativebridge.NativeLibrary
import java.io.Serializable

@Keep
object MiiSelector {
    @JvmField
    var data: MiiSelectorData = MiiSelectorData(0L, 0)
    val finishLock = Object()

    private fun executeImpl(config: MiiSelectorConfig) {
        val activity = NativeLibrary.sEmulationActivity.get()
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            data = MiiSelectorData(0L, config.initiallySelectedMiiIndex.toInt().coerceAtLeast(0))
            synchronized(finishLock) { finishLock.notifyAll() }
            return
        }
        data = MiiSelectorData(0L, 0)
        val items = config.miiNames
        val adapter = ArrayAdapter(activity, android.R.layout.select_dialog_singlechoice, items)
        val initialIdx = config.initiallySelectedMiiIndex.toInt().coerceIn(0, (items.size - 1).coerceAtLeast(0))
        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(config.title ?: "Select Mii")
            .setSingleChoiceItems(adapter, initialIdx) { dialog, which ->
                data = MiiSelectorData(0L, which)
                dialog.dismiss()
                synchronized(finishLock) { finishLock.notifyAll() }
            }
            .setOnCancelListener {
                data = MiiSelectorData(if (config.enableCancelButton) 1L else 0L, initialIdx)
                synchronized(finishLock) { finishLock.notifyAll() }
            }
        if (config.enableCancelButton) {
            builder.setNegativeButton(android.R.string.cancel) { dialog, _ ->
                data = MiiSelectorData(1L, initialIdx)
                dialog.dismiss()
                synchronized(finishLock) { finishLock.notifyAll() }
            }
        }
        builder.show()
    }

    @Keep
    @JvmStatic
    fun execute(config: MiiSelectorConfig): MiiSelectorData {
        val activity = NativeLibrary.sEmulationActivity.get()
            ?: return MiiSelectorData(0L, 0)
        activity.runOnUiThread { executeImpl(config) }
        synchronized(finishLock) {
            runCatching { finishLock.wait() }
        }
        return data
    }

    @Keep
    class MiiSelectorConfig : Serializable {
        @JvmField var enableCancelButton: Boolean = false
        @JvmField var title: String? = null
        @JvmField var initiallySelectedMiiIndex: Long = 0
        @JvmField var miiNames: Array<String> = emptyArray()
    }

    @Keep
    class MiiSelectorData(@JvmField var returnCode: Long, @JvmField var index: Int)
}
