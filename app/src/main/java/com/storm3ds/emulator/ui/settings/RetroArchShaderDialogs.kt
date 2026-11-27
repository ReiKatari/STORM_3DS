package com.storm3ds.emulator.ui.settings

import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.storm3ds.emulator.MelonEmulator
import com.storm3ds.emulator.R
import java.io.File
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.roundToInt

object RetroArchShaderDialogs {

    fun parseParameterMap(raw: String?): LinkedHashMap<String, Float> {
        val out = linkedMapOf<String, Float>()
        if (raw.isNullOrBlank()) return out
        raw.lineSequence()
            .flatMap { it.split(',', ';').asSequence() }
            .forEach { token ->
                val parts = token.split('=', limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim().toFloatOrNull()
                    if (key.isNotEmpty() && value != null && value.isFinite()) {
                        out[key] = value
                    }
                }
            }
        return out
    }

    fun serializeParameterMap(map: Map<String, Float>): String {
        if (map.isEmpty()) return ""
        return map.entries.joinToString("\n") { (key, value) ->
            "$key=${String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')}"
        }
    }

    fun resolveInstalledPresetRoots(context: Context): List<File> {
        val roots = mutableListOf<File>()
        runCatching { Environment.getExternalStorageDirectory() }.getOrNull()?.let { ext ->
            roots += File(ext, "STORM 3DS/shaders/retroarch-shaders/installed")
            roots += File(ext, "STORM 3DS/shaders/retroarch-shaders/current")
        }
        roots += File(context.filesDir, "retroarch-shaders/installed")
        roots += File(context.filesDir, "retroarch-shaders/current")
        return roots.filter { it.isDirectory }
    }

    fun resolvePresetFilePath(context: Context, rawPresetPath: String?): String? {
        val cleaned = rawPresetPath
            ?.trim()
            ?.removePrefix("internal:")
            ?.removePrefix("folder:")
            ?.trimStart('/')
            ?.takeIf { it.isNotBlank() && it != "none" }
            ?: return null

        val direct = File(rawPresetPath.trim())
        if (direct.isFile) {
            return direct.absolutePath
        }
        for (root in resolveInstalledPresetRoots(context)) {
            val candidate = File(root, cleaned)
            if (candidate.isFile) {
                return candidate.absolutePath
            }
        }
        return null
    }

    fun listInstalledPresetRelativePaths(context: Context): List<String> {
        val installedRoots = mutableListOf<File>()
        runCatching { Environment.getExternalStorageDirectory() }.getOrNull()?.let { ext ->
            installedRoots += File(ext, "STORM 3DS/shaders/retroarch-shaders/installed")
        }
        installedRoots += File(context.filesDir, "retroarch-shaders/installed")
        val root = installedRoots.firstOrNull { dir ->
            dir.isDirectory && dir.walkTopDown().maxDepth(4).any { it.isFile && it.extension.equals("slangp", ignoreCase = true) }
        } ?: return emptyList()

        return root.walkTopDown()
            .maxDepth(4)
            .filter { it.isFile && it.extension.equals("slangp", ignoreCase = true) }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .sorted()
            .toList()
    }

    fun showPresetPickerDialog(
        context: Context,
        currentSelection: String?,
        allowUseGlobal: Boolean,
        onSelected: (String?) -> Unit,
    ) {
        val appCtx = context.applicationContext
        val mainHandler = Handler(Looper.getMainLooper())
        thread(start = true, name = "ShaderPresetScan") {
            val presets = listInstalledPresetRelativePaths(appCtx)
            mainHandler.post {
                val activity = context as? android.app.Activity
                if (activity != null && (activity.isFinishing || activity.isDestroyed)) {
                    return@post
                }
                val labels = mutableListOf<String>()
                val values = mutableListOf<String?>()

                if (allowUseGlobal) {
                    labels += context.getString(R.string.use_global_preference)
                    values += null
                }
                labels += context.getString(R.string.off)
                values += "none"

                presets.forEach { rel ->
                    labels += rel.removeSuffix(".slangp")
                    values += rel
                }

                labels += context.getString(R.string.retroarch_shader_manual_path)
                val manualIndex = labels.lastIndex

                val cleanCurrent = currentSelection?.takeUnless { it.isBlank() }
                val checkedIndex = values.indexOfFirst { it == cleanCurrent }.coerceAtLeast(0)

                AlertDialog.Builder(context)
                    .setTitle(R.string.video_retroarch_shader_preset_title)
                    .setSingleChoiceItems(labels.toTypedArray(), checkedIndex) { dialog, which ->
                        dialog.dismiss()
                        if (which == manualIndex) {
                            showManualTextDialog(
                                context = context,
                                titleRes = R.string.video_retroarch_shader_preset_title,
                                initialText = currentSelection.orEmpty(),
                                onConfirm = { entered -> onSelected(entered?.ifBlank { null }) },
                            )
                        } else {
                            onSelected(values[which])
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    fun showShaderParametersDialog(
        context: Context,
        rawPresetPath: String?,
        currentParamsText: String?,
        onLiveChanged: ((String) -> Unit)? = null,
        onSaved: (String) -> Unit,
    ) {
        val resolvedPath = resolvePresetFilePath(context, rawPresetPath)
        if (resolvedPath == null) {
            showManualTextDialog(
                context = context,
                titleRes = R.string.video_retroarch_shader_parameters_title,
                initialText = currentParamsText.orEmpty(),
                onConfirm = { text -> onSaved(text.orEmpty()) },
            )
            return
        }

        val density = context.resources.displayMetrics.density
        val pad = (16 * density).roundToInt()

        val rootContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad / 2)
        }
        val loadingRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ProgressBar(context))
            addView(
                TextView(context).apply {
                    text = context.getString(R.string.info_loading)
                    setPadding((12 * density).roundToInt(), 0, 0, 0)
                },
            )
        }
        rootContainer.addView(loadingRow)

        val currentValues = parseParameterMap(currentParamsText)
        var loadedParams: List<MelonEmulator.RetroArchShaderParam> = emptyList()

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.video_retroarch_shader_parameters_title)
            .setView(rootContainer)
            .setPositiveButton(R.string.ok) { _, _ ->
                onSaved(serializeParameterMap(currentValues))
            }
            .setNeutralButton(R.string.retroarch_shader_edit_text) { _, _ ->
                showManualTextDialog(
                    context = context,
                    titleRes = R.string.video_retroarch_shader_parameters_title,
                    initialText = serializeParameterMap(currentValues).ifBlank { currentParamsText.orEmpty() },
                    onConfirm = { text -> onSaved(text.orEmpty()) },
                )
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                onLiveChanged?.invoke(currentParamsText.orEmpty())
            }
            .create()

        dialog.show()

        thread(name = "RetroArchParamQuery", isDaemon = true) {
            val params = MelonEmulator.queryRetroArchShaderParameters(resolvedPath)
            rootContainer.post {
                if (!dialog.isShowing) return@post
                loadedParams = params
                rootContainer.removeAllViews()

                if (params.isEmpty()) {
                    rootContainer.addView(
                        TextView(context).apply {
                            text = context.getString(R.string.retroarch_shader_no_params)
                            setPadding(0, 0, 0, (8 * density).roundToInt())
                        },
                    )
                    val editor = EditText(context).apply {
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                        setText(currentParamsText.orEmpty())
                    }
                    rootContainer.addView(editor)
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                        val raw = editor.text.toString().trim()
                        onLiveChanged?.invoke(raw)
                        onSaved(raw)
                        dialog.dismiss()
                    }
                    return@post
                }

                val scroll = ScrollView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        minOf(
                            (context.resources.displayMetrics.heightPixels * 0.55f).roundToInt(),
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ),
                    )
                }
                val listLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                }
                scroll.addView(listLayout)

                val rowUpdaters = mutableListOf<() -> Unit>()
                val resetAllBtn = Button(context).apply {
                    text = context.getString(R.string.retroarch_shader_reset_params)
                    setOnClickListener {
                        currentValues.clear()
                        rowUpdaters.forEach { it() }
                        onLiveChanged?.invoke("")
                    }
                }
                rootContainer.addView(resetAllBtn)
                rootContainer.addView(scroll)

                params.forEach { param ->
                    val span = (param.maximum - param.minimum).coerceAtLeast(param.step)
                    val steps = (span / param.step).roundToInt().coerceIn(1, 2000)

                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(0, (8 * density).roundToInt(), 0, (8 * density).roundToInt())
                    }
                    val header = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    val titleView = TextView(context).apply {
                        text = param.description
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    val valueView = TextView(context).apply {
                        setPadding((8 * density).roundToInt(), 0, 0, 0)
                    }
                    header.addView(titleView)
                    header.addView(valueView)

                    val seekBar = SeekBar(context).apply {
                        max = steps
                    }

                    fun updateUiFromValue() {
                        val effectiveVal = currentValues[param.name] ?: param.initial
                        val clamped = effectiveVal.coerceIn(param.minimum, param.maximum)
                        val prog = (((clamped - param.minimum) / span) * steps).roundToInt().coerceIn(0, steps)
                        seekBar.progress = prog
                        valueView.text = formatParamValue(clamped, param.step)
                    }
                    updateUiFromValue()
                    rowUpdaters += ::updateUiFromValue

                    seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                            if (!fromUser) return
                            val rawVal = param.minimum + (progress.toFloat() / steps) * span
                            val snapped = (param.minimum + ((rawVal - param.minimum) / param.step).roundToInt() * param.step)
                                .coerceIn(param.minimum, param.maximum)
                            valueView.text = formatParamValue(snapped, param.step)
                            currentValues[param.name] = snapped
                            onLiveChanged?.invoke(serializeParameterMap(currentValues))
                        }

                        override fun onStartTrackingTouch(sb: SeekBar?) {}
                        override fun onStopTrackingTouch(sb: SeekBar?) {}
                    })

                    row.addView(header)
                    row.addView(seekBar)
                    listLayout.addView(row)
                }

                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val result = if (loadedParams.isNotEmpty()) serializeParameterMap(currentValues) else currentParamsText.orEmpty()
                    onLiveChanged?.invoke(result)
                    onSaved(result)
                    dialog.dismiss()
                }
            }
        }
    }

    private fun formatParamValue(value: Float, step: Float): String {
        val decimals = when {
            step >= 1f -> 0
            step >= 0.1f -> 1
            step >= 0.01f -> 2
            else -> 3
        }
        return String.format(Locale.US, "%.${decimals}f", value)
    }

    private fun showManualTextDialog(
        context: Context,
        titleRes: Int,
        initialText: String,
        onConfirm: (String?) -> Unit,
    ) {
        val density = context.resources.displayMetrics.density
        val pad = (16 * density).roundToInt()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
        }
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(initialText)
            setSelection(text.length)
        }
        container.addView(input)

        AlertDialog.Builder(context)
            .setTitle(titleRes)
            .setView(container)
            .setPositiveButton(R.string.ok) { _, _ ->
                onConfirm(input.text.toString().trim().ifBlank { null })
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
