package com.storm3ds.emulator.ui.common.views

import android.app.Dialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.storm3ds.emulator.domain.model.Input
import com.storm3ds.emulator.ui.settings.dialogs.SettingsDialogScaffold
import com.storm3ds.emulator.ui.settings.dialogs.showSettingsComposeDialog
import com.storm3ds.emulator.ui.theme.LocalWatermelonColors
import com.storm3ds.emulator.ui.theme.SpaceGrotesk
import com.storm3ds.emulator.ui.theme.WatermelonMono

const val PREF_COMBO_KEY_PREFIX = "combo_key_slot_"

fun loadComboKeyInputs(context: Context, slotIndex: Int): List<Input> {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    val raw = prefs.getString("$PREF_COMBO_KEY_PREFIX$slotIndex", null) ?: return emptyList()
    return raw.split(",")
        .mapNotNull { token ->
            val trimmed = token.trim()
            if (trimmed.isEmpty()) null else Input.entries.firstOrNull { it.name == trimmed }
        }
        .distinct()
        .take(4)
}

fun saveComboKeyInputs(context: Context, slotIndex: Int, inputs: Collection<Input>) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    val serialized = inputs.distinct().take(4).joinToString(",") { it.name }
    prefs.edit().putString("$PREF_COMBO_KEY_PREFIX$slotIndex", serialized).apply()
}

private fun availableCombo3dsInputs(): List<Input> {
    val base = mutableListOf(
        Input.A,
        Input.B,
        Input.X,
        Input.Y,
        Input.L,
        Input.R,
        Input.START,
        Input.SELECT,
    )
    Input.entries.firstOrNull { it.name == "ZL" }?.let { base.add(it) }
    Input.entries.firstOrNull { it.name == "ZR" }?.let { base.add(it) }
    return base
}

/**
 * Clean Compose dialog helper allowing multi-select of 2-4 3DS inputs for a combo slot
 * and persisting the selection via SharedPreferences.
 */
fun showComboKeyConfigDialog(
    context: Context,
    slotIndex: Int,
    currentInputs: Collection<Input> = loadComboKeyInputs(context, slotIndex),
    onSave: (List<Input>) -> Unit = {},
): Dialog {
    val options = availableCombo3dsInputs()
    val initial = currentInputs.filter { it in options }.distinct().take(4)

    return showSettingsComposeDialog(context) { dismiss ->
        ComboKeyConfigContent(
            slotIndex = slotIndex,
            options = options,
            initialSelection = initial,
            onSave = { chosen ->
                saveComboKeyInputs(context, slotIndex, chosen)
                onSave(chosen)
                dismiss()
            },
            onDismiss = dismiss,
        )
    }
}

@Composable
private fun ComboKeyConfigContent(
    slotIndex: Int,
    options: List<Input>,
    initialSelection: List<Input>,
    onSave: (List<Input>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalWatermelonColors.current
    val selectedList = remember { mutableStateListOf<Input>().apply { addAll(initialSelection) } }
    val isValid = selectedList.size in 2..4

    SettingsDialogScaffold(
        title = "Combo Key C$slotIndex",
        subtitle = "Select 2-4 inputs to trigger simultaneously",
        icon = Icons.Filled.Bolt,
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Live combo preview banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface2)
                    .border(
                        width = 1.dp,
                        color = if (isValid) colors.green.copy(alpha = 0.65f) else colors.line,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (selectedList.isEmpty()) "None selected" else selectedList.joinToString(" + ") { it.name },
                    color = if (isValid) colors.green else colors.text2,
                    fontFamily = WatermelonMono,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${selectedList.size}/4",
                    color = if (isValid) colors.green else colors.red,
                    fontFamily = WatermelonMono,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            // 2-column multi-select grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.chunked(2).forEach { rowInputs ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowInputs.forEach { input ->
                            val isChecked = input in selectedList
                            val canToggleOn = selectedList.size < 4
                            val shape = RoundedCornerShape(12.dp)

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clip(shape)
                                    .background(if (isChecked) colors.greenDim else colors.surface2)
                                    .border(
                                        width = if (isChecked) 1.5.dp else 1.dp,
                                        color = if (isChecked) colors.green.copy(alpha = 0.75f) else colors.line,
                                        shape = shape,
                                    )
                                    .clickable(enabled = isChecked || canToggleOn) {
                                        if (isChecked) {
                                            selectedList.remove(input)
                                        } else if (canToggleOn) {
                                            selectedList.add(input)
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked && !isChecked && canToggleOn) {
                                            selectedList.add(input)
                                        } else if (!checked) {
                                            selectedList.remove(input)
                                        }
                                    },
                                    enabled = isChecked || canToggleOn,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = colors.green,
                                        uncheckedColor = colors.text3,
                                        checkmarkColor = colors.bg,
                                    ),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = input.name,
                                    color = if (isChecked) colors.text else if (canToggleOn) colors.text else colors.text3,
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 14.sp,
                                    fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (rowInputs.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            // Action bar: Clear & Save
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface2)
                        .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                        .clickable { selectedList.clear() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Clear",
                        color = colors.text2,
                        fontFamily = SpaceGrotesk,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(2f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isValid) colors.green else colors.surface3)
                        .clickable(enabled = isValid) {
                            onSave(selectedList.toList())
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isValid) "Save Combo" else "Pick 2-4 Keys",
                        color = if (isValid) colors.bg else colors.text3,
                        fontFamily = SpaceGrotesk,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
