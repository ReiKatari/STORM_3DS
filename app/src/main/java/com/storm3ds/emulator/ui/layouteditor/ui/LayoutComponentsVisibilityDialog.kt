package com.storm3ds.emulator.ui.layouteditor.ui



import androidx.annotation.StringRes

import androidx.compose.foundation.background

import androidx.compose.foundation.border

import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.*

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.Icon

import androidx.compose.material.Switch

import androidx.compose.material.SwitchDefaults

import androidx.compose.material.Text

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.*

import androidx.compose.runtime.Composable

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip

import androidx.compose.ui.res.stringResource

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp

import androidx.compose.ui.window.Dialog

import androidx.compose.ui.window.DialogProperties

import com.storm3ds.emulator.R

import com.storm3ds.emulator.domain.model.layout.LayoutComponent

import com.storm3ds.emulator.ui.theme.LocalWatermelonColors

import com.storm3ds.emulator.ui.theme.Manrope

import com.storm3ds.emulator.utils.getLayoutComponentName



private data class ComponentGroup(

    @StringRes val titleRes: Int,

    val components: List<LayoutComponent>,

)



private val COMPONENT_GROUPS = listOf(

    ComponentGroup(

        titleRes = R.string.component_group_screens,

        components = listOf(

            LayoutComponent.TOP_SCREEN,

            LayoutComponent.BOTTOM_SCREEN,

            LayoutComponent.HYBRID_SCREEN,

        ),

    ),

    ComponentGroup(

        titleRes = R.string.component_group_gameplay,

        components = listOf(

            LayoutComponent.DPAD,

            LayoutComponent.CIRCLE_PAD,

            LayoutComponent.C_STICK,

            LayoutComponent.BUTTONS,

            LayoutComponent.BUTTON_START,

            LayoutComponent.BUTTON_SELECT,

            LayoutComponent.BUTTON_L,

            LayoutComponent.BUTTON_R,

            LayoutComponent.BUTTON_ZL,

            LayoutComponent.BUTTON_ZR,

            LayoutComponent.BUTTON_HOME,

            LayoutComponent.BUTTON_HINGE,

        ),

    ),

    ComponentGroup(

        titleRes = R.string.component_group_emulator,

        components = listOf(

            LayoutComponent.BUTTON_PAUSE,

            LayoutComponent.BUTTON_FAST_FORWARD_TOGGLE,

            LayoutComponent.BUTTON_REWIND,

            LayoutComponent.BUTTON_QUICK_SAVE,

            LayoutComponent.BUTTON_QUICK_LOAD,

            LayoutComponent.BUTTON_RESET,

        ),

    ),

    ComponentGroup(

        titleRes = R.string.component_group_combo_special,

        components = listOf(

            LayoutComponent.BUTTON_SWAP_SCREENS,

            LayoutComponent.BUTTON_MICROPHONE_TOGGLE,

            LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT,

            LayoutComponent.BUTTON_TOGGLE_EXTRA_BUTTONS,

            LayoutComponent.BUTTON_LOCK_ROTATION,

            LayoutComponent.BUTTON_TRANSLATE,

            LayoutComponent.SLIDER_3D_DEPTH,

            LayoutComponent.BUTTON_COMBO_1,

            LayoutComponent.BUTTON_COMBO_2,

        ),

    ),

    ComponentGroup(

        titleRes = R.string.component_group_stick,

        components = listOf(

            LayoutComponent.BUTTON_TOGGLE_ANALOG_STICK,

        ),

    ),

)



@Composable

fun LayoutComponentsVisibilityDialog(

    instantiatedComponents: Set<LayoutComponent>,

    onToggleComponent: (LayoutComponent, Boolean) -> Unit,

    onDismiss: () -> Unit,

) {

    val colors = LocalWatermelonColors.current



    Dialog(

        onDismissRequest = onDismiss,

        properties = DialogProperties(usePlatformDefaultWidth = false),

    ) {

        Box(

            modifier = Modifier

                .fillMaxSize()

                .background(colors.surface.copy(alpha = 0.65f))

                .clickable(onClick = onDismiss),

            contentAlignment = Alignment.Center,

        ) {

            Box(

                modifier = Modifier

                    .widthIn(max = 480.dp)

                    .fillMaxWidth(0.92f)

                    .fillMaxHeight(0.85f)

                    .clip(RoundedCornerShape(20.dp))

                    .background(colors.surface)

                    .border(1.2.dp, colors.line, RoundedCornerShape(20.dp))

                    .clickable(enabled = false) {}

                    .padding(20.dp),

            ) {

                Column(modifier = Modifier.fillMaxSize()) {

                    // Header

                    Row(

                        verticalAlignment = Alignment.CenterVertically,

                        modifier = Modifier.fillMaxWidth(),

                    ) {

                        Icon(

                            imageVector = Icons.Filled.Extension,

                            contentDescription = null,

                            tint = colors.green,

                            modifier = Modifier.size(24.dp),

                        )

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {

                            Text(

                                text = stringResource(R.string.components_visibility_title),

                                color = colors.text,

                                fontFamily = Manrope,

                                fontSize = 18.sp,

                                fontWeight = FontWeight.Bold,

                            )

                            Text(

                                text = stringResource(R.string.ui_layout_vis_dialog_title),

                                color = colors.text3,

                                fontFamily = Manrope,

                                fontSize = 11.sp,

                            )

                        }

                        Box(

                            modifier = Modifier

                                .clip(RoundedCornerShape(8.dp))

                                .background(colors.surface2)

                                .border(1.dp, colors.green.copy(alpha = 0.6f), RoundedCornerShape(8.dp))

                                .clickable(onClick = onDismiss)

                                .padding(horizontal = 12.dp, vertical = 6.dp),

                            contentAlignment = Alignment.Center,

                        ) {

                            Text(

                                text = stringResource(R.string.ok),

                                color = colors.green,

                                fontFamily = Manrope,

                                fontSize = 13.sp,

                                fontWeight = FontWeight.Bold,

                            )

                        }

                    }



                    Spacer(Modifier.height(14.dp))

                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))

                    Spacer(Modifier.height(8.dp))



                    // Grouped Components List

                    LazyColumn(

                        verticalArrangement = Arrangement.spacedBy(6.dp),

                        modifier = Modifier

                            .fillMaxWidth()

                            .weight(1f),

                    ) {

                        COMPONENT_GROUPS.forEach { group ->

                            item(key = "header_${group.titleRes}") {

                                Row(

                                    verticalAlignment = Alignment.CenterVertically,

                                    modifier = Modifier

                                        .fillMaxWidth()

                                        .padding(top = 10.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),

                                ) {

                                    Box(

                                        modifier = Modifier

                                            .size(6.dp)

                                            .clip(CircleShape)

                                            .background(colors.green),

                                    )

                                    Spacer(Modifier.width(8.dp))

                                    Text(

                                        text = stringResource(group.titleRes),

                                        color = colors.green,

                                        fontFamily = Manrope,

                                        fontSize = 12.sp,

                                        fontWeight = FontWeight.Bold,

                                        letterSpacing = 0.5.sp,

                                    )

                                }

                            }



                            items(group.components, key = { it.name }) { component ->

                                val isChecked = instantiatedComponents.contains(component)



                                val name = when (component) {

                                    LayoutComponent.DPAD -> stringResource(R.string.ui_layout_dpad_or_stick)

                                    LayoutComponent.BUTTON_TOGGLE_ANALOG_STICK -> stringResource(R.string.ui_layout_toggle_dpad_stick)

                                    else -> stringResource(getLayoutComponentName(component))

                                }



                                val description = when (component) {

                                    LayoutComponent.TOP_SCREEN -> stringResource(R.string.ui_layout_comp_top_screen)

                                    LayoutComponent.BOTTOM_SCREEN -> stringResource(R.string.ui_layout_comp_bottom_screen)

                                    LayoutComponent.HYBRID_SCREEN -> stringResource(R.string.ui_layout_comp_hybrid_screen)

                                    LayoutComponent.DPAD -> stringResource(R.string.ui_layout_comp_dpad)

                                    LayoutComponent.BUTTONS -> stringResource(R.string.ui_layout_comp_abxy)

                                    LayoutComponent.BUTTON_START -> stringResource(R.string.ui_layout_comp_start)

                                    LayoutComponent.BUTTON_SELECT -> stringResource(R.string.ui_layout_comp_select)

                                    LayoutComponent.BUTTON_L -> stringResource(R.string.ui_layout_comp_l)

                                    LayoutComponent.BUTTON_R -> stringResource(R.string.ui_layout_comp_r)

                                    LayoutComponent.BUTTON_HINGE -> stringResource(R.string.ui_layout_comp_hinge)

                                    LayoutComponent.BUTTON_FAST_FORWARD_TOGGLE -> stringResource(R.string.ui_layout_comp_ff)

                                    LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT -> stringResource(R.string.ui_layout_comp_soft_input)

                                    LayoutComponent.BUTTON_RESET -> stringResource(R.string.ui_layout_comp_reset)

                                    LayoutComponent.BUTTON_PAUSE -> stringResource(R.string.ui_layout_comp_pause)

                                    LayoutComponent.BUTTON_SWAP_SCREENS -> stringResource(R.string.ui_layout_comp_swap)

                                    LayoutComponent.BUTTON_QUICK_SAVE -> stringResource(R.string.ui_layout_comp_qsave)

                                    LayoutComponent.BUTTON_QUICK_LOAD -> stringResource(R.string.ui_layout_comp_qload)

                                    LayoutComponent.BUTTON_REWIND -> stringResource(R.string.ui_layout_comp_rewind)

                                    LayoutComponent.BUTTON_MICROPHONE_TOGGLE -> stringResource(R.string.ui_layout_comp_mic)

                                    LayoutComponent.BUTTON_TOGGLE_EXTRA_BUTTONS -> stringResource(R.string.ui_layout_comp_extra)

                                    LayoutComponent.BUTTON_LOCK_ROTATION -> stringResource(R.string.ui_layout_comp_rot_lock)

                                    LayoutComponent.BUTTON_TRANSLATE -> stringResource(R.string.ui_layout_comp_translate)

                                    LayoutComponent.BUTTON_TOGGLE_ANALOG_STICK -> stringResource(R.string.ui_layout_comp_toggle_stick)

                                    LayoutComponent.CIRCLE_PAD -> stringResource(R.string.ui_layout_comp_circle_pad)

                                    LayoutComponent.C_STICK -> stringResource(R.string.ui_layout_comp_c_stick)

                                    LayoutComponent.SLIDER_3D_DEPTH -> stringResource(R.string.ui_layout_comp_3d_slider)

                                    LayoutComponent.BUTTON_ZL -> stringResource(R.string.ui_layout_comp_zl)

                                    LayoutComponent.BUTTON_ZR -> stringResource(R.string.ui_layout_comp_zr)

                                    LayoutComponent.BUTTON_HOME -> stringResource(R.string.ui_layout_comp_home)

                                    LayoutComponent.BUTTON_COMBO_1 -> stringResource(R.string.ui_layout_comp_combo1)

                                    LayoutComponent.BUTTON_COMBO_2 -> stringResource(R.string.ui_layout_comp_combo2)

                                }



                                Box(

                                    modifier = Modifier

                                        .fillMaxWidth()

                                        .clip(RoundedCornerShape(12.dp))

                                        .background(if (isChecked) colors.surface2 else colors.surface2.copy(alpha = 0.5f))

                                        .border(

                                            1.dp,

                                            if (isChecked) colors.green.copy(alpha = 0.35f) else colors.line,

                                            RoundedCornerShape(12.dp)

                                        )

                                        .clickable {

                                            onToggleComponent(component, !isChecked)

                                        }

                                        .padding(horizontal = 14.dp, vertical = 9.dp),

                                ) {

                                    Row(

                                        verticalAlignment = Alignment.CenterVertically,

                                        modifier = Modifier.fillMaxWidth(),

                                    ) {

                                        Icon(

                                            imageVector = when (component) {

                                                LayoutComponent.TOP_SCREEN -> Icons.Filled.Tv

                                                LayoutComponent.BOTTOM_SCREEN -> Icons.Filled.SmartDisplay

                                                LayoutComponent.HYBRID_SCREEN -> Icons.Filled.PictureInPicture

                                                LayoutComponent.DPAD -> Icons.Filled.Gamepad

                                                LayoutComponent.BUTTONS -> Icons.Filled.RadioButtonChecked

                                                LayoutComponent.BUTTON_START -> Icons.Filled.PlayArrow

                                                LayoutComponent.BUTTON_SELECT -> Icons.Filled.Menu

                                                LayoutComponent.BUTTON_L -> Icons.Filled.ChevronLeft

                                                LayoutComponent.BUTTON_R -> Icons.Filled.ChevronRight

                                                LayoutComponent.BUTTON_HINGE -> Icons.Filled.Laptop

                                                LayoutComponent.BUTTON_FAST_FORWARD_TOGGLE -> Icons.Filled.FastForward

                                                LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT -> Icons.Filled.Edit

                                                LayoutComponent.BUTTON_RESET -> Icons.Filled.RestartAlt

                                                LayoutComponent.BUTTON_PAUSE -> Icons.Filled.Pause

                                                LayoutComponent.BUTTON_SWAP_SCREENS -> Icons.Filled.SwapVert

                                                LayoutComponent.BUTTON_QUICK_SAVE -> Icons.Filled.Save

                                                LayoutComponent.BUTTON_QUICK_LOAD -> Icons.Filled.Restore

                                                LayoutComponent.BUTTON_REWIND -> Icons.Filled.FastRewind

                                                LayoutComponent.BUTTON_MICROPHONE_TOGGLE -> Icons.Filled.Mic

                                                LayoutComponent.BUTTON_TRANSLATE -> Icons.Filled.Translate

                                                LayoutComponent.BUTTON_TOGGLE_EXTRA_BUTTONS -> Icons.Filled.MoreHoriz

                                                LayoutComponent.BUTTON_LOCK_ROTATION -> Icons.Filled.ScreenLockRotation

                                                LayoutComponent.BUTTON_TOGGLE_ANALOG_STICK -> Icons.Filled.ChangeCircle

                                                LayoutComponent.CIRCLE_PAD -> Icons.Filled.SportsEsports

                                                LayoutComponent.C_STICK -> Icons.Filled.ControlCamera

                                                LayoutComponent.SLIDER_3D_DEPTH -> Icons.Filled.Tune

                                                LayoutComponent.BUTTON_ZL -> Icons.Filled.ChevronLeft

                                                LayoutComponent.BUTTON_ZR -> Icons.Filled.ChevronRight

                                                LayoutComponent.BUTTON_HOME -> Icons.Filled.Home

                                                LayoutComponent.BUTTON_COMBO_1 -> Icons.Filled.Bolt

                                                LayoutComponent.BUTTON_COMBO_2 -> Icons.Filled.Bolt

                                            },

                                            contentDescription = null,

                                            tint = if (isChecked) colors.green else colors.text3,

                                            modifier = Modifier.size(20.dp),

                                        )

                                        Spacer(Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {

                                            Text(

                                                text = name,

                                                color = if (isChecked) colors.text else colors.text3,

                                                fontFamily = Manrope,

                                                fontSize = 14.sp,

                                                fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal,

                                            )

                                            if (description != null) {

                                                Spacer(Modifier.height(2.dp))

                                                Text(

                                                    text = description,

                                                    color = colors.text3.copy(alpha = 0.75f),

                                                    fontFamily = Manrope,

                                                    fontSize = 11.sp,

                                                    lineHeight = 14.sp,

                                                )

                                            }

                                        }

                                        Spacer(Modifier.width(8.dp))

                                        Switch(

                                            checked = isChecked,

                                            onCheckedChange = { checked ->

                                                onToggleComponent(component, checked)

                                            },

                                            colors = SwitchDefaults.colors(

                                                checkedThumbColor = colors.green,

                                                checkedTrackColor = colors.green.copy(alpha = 0.5f),

                                                uncheckedThumbColor = colors.text3,

                                                uncheckedTrackColor = colors.surface,

                                            ),

                                        )

                                    }

                                }

                            }

                        }

                    }

                }

            }

        }

    }

}

