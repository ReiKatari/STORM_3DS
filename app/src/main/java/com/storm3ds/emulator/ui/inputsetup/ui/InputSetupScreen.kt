package com.storm3ds.emulator.ui.inputsetup.ui

import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.preference.PreferenceManager
import com.storm3ds.emulator.R
import com.storm3ds.emulator.domain.model.Input
import com.storm3ds.emulator.domain.model.InputConfig
import com.storm3ds.emulator.domain.model.Slot2AnalogMapping
import com.storm3ds.emulator.ui.common.MelonPreviewSet
import com.storm3ds.emulator.ui.common.WatermelonScreenScaffold
import com.storm3ds.emulator.ui.inputsetup.InputSetupViewModel
import com.storm3ds.emulator.ui.settings.dialogs.ButtonSwatchColors
import com.storm3ds.emulator.ui.settings.dialogs.resolveButtonSwatches
import com.storm3ds.emulator.ui.theme.MelonTheme
import com.storm3ds.emulator.ui.theme.SpaceGrotesk
import com.storm3ds.emulator.ui.theme.WatermelonMono
import com.storm3ds.emulator.ui.theme.watermelon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun InputSetupScreen(
    viewModel: InputSetupViewModel,
    onBackClick: () -> Unit,
    onAutoMapClick: () -> Unit = {},
    onClearAllClick: () -> Unit = {},
) {
    val inputConfig by viewModel.inputConfiguration.collectAsStateWithLifecycle()
    val inputUnderConfiguration by viewModel.inputUnderAssignment.collectAsStateWithLifecycle()
    val slot2AnalogMapping by viewModel.slot2AnalogMapping.collectAsStateWithLifecycle()
    val slot2AxisUnderConfiguration by viewModel.slot2AxisUnderAssignment.collectAsStateWithLifecycle()
    val onInputAssignedEvent = viewModel.onInputAssignedEvent

    InputSetupScreenContent(
        inputConfig = inputConfig,
        inputUnderConfiguration = inputUnderConfiguration,
        slot2AnalogMapping = slot2AnalogMapping,
        slot2AxisUnderConfiguration = slot2AxisUnderConfiguration,
        onInputAssignedEvent = onInputAssignedEvent,
        onInputClick = viewModel::startInputAssignment,
        onClearInputClick = viewModel::clearInputAssignment,
        onSlot2AxisXClick = { viewModel.startSlot2AxisAssignment(InputSetupViewModel.Slot2AnalogAxisTarget.X) },
        onSlot2AxisYClick = { viewModel.startSlot2AxisAssignment(InputSetupViewModel.Slot2AnalogAxisTarget.Y) },
        onSlot2InvertXChanged = viewModel::setSlot2InvertX,
        onSlot2InvertYChanged = viewModel::setSlot2InvertY,
        onDeadzoneChanged = viewModel::setSlot2Deadzone,
        onUseDeviceFilterChanged = viewModel::setSlot2UseDeviceFilter,
        onCancelInputConfiguration = viewModel::stopAnyAssignment,
        onBackClick = onBackClick,
        onAutoMapClick = onAutoMapClick,
        onClearAllClick = onClearAllClick,
    )
}

private val FACE_AND_DPAD_INPUTS = listOf(
    Input.A,
    Input.B,
    Input.X,
    Input.Y,
    Input.UP,
    Input.DOWN,
    Input.LEFT,
    Input.RIGHT,
)

private val SHOULDER_AND_SYSTEM_INPUTS = listOf(
    Input.L,
    Input.R,
    Input.ZL,
    Input.ZR,
    Input.START,
    Input.SELECT,
    Input.HOME,
    Input.HINGE,
)

private val CONSOLE_INPUT_ORDER = FACE_AND_DPAD_INPUTS + SHOULDER_AND_SYSTEM_INPUTS
private val CONSOLE_INPUTS = CONSOLE_INPUT_ORDER.toSet()

@Suppress("UNUSED_PARAMETER")
@Composable
private fun InputSetupScreenContent(
    inputConfig: List<InputConfig>,
    inputUnderConfiguration: Input?,
    slot2AnalogMapping: Slot2AnalogMapping,
    slot2AxisUnderConfiguration: InputSetupViewModel.Slot2AnalogAxisTarget?,
    onInputAssignedEvent: Flow<Input>,
    onInputClick: (Input) -> Unit,
    onClearInputClick: (Input) -> Unit,
    onSlot2AxisXClick: () -> Unit,
    onSlot2AxisYClick: () -> Unit,
    onSlot2InvertXChanged: (Boolean) -> Unit,
    onSlot2InvertYChanged: (Boolean) -> Unit,
    onDeadzoneChanged: (Float) -> Unit,
    onUseDeviceFilterChanged: (Boolean) -> Unit,
    onCancelInputConfiguration: () -> Unit,
    onBackClick: () -> Unit,
    onAutoMapClick: () -> Unit = {},
    onClearAllClick: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    val activeThemeKey = remember(context) {
        runCatching {
            PreferenceManager.getDefaultSharedPreferences(context)
                .getString("button_color_theme", "cyber_cyan") ?: "cyber_cyan"
        }.getOrDefault("cyber_cyan")
    }
    val themeKeys = stringArrayResource(R.array.button_color_theme_values)
    val themeNames = stringArrayResource(R.array.button_color_theme_options)
    val activeThemeLabel = remember(activeThemeKey, themeKeys, themeNames) {
        val idx = themeKeys.indexOf(activeThemeKey)
        if (idx >= 0 && idx < themeNames.size) themeNames[idx] else activeThemeKey
    }
    val swatches = remember(activeThemeKey) { resolveButtonSwatches(activeThemeKey) }

    BackHandler(enabled = inputUnderConfiguration != null) {
        onCancelInputConfiguration()
    }
    LaunchedEffect(Unit) {
        onInputAssignedEvent.collect {
            focusManager.moveFocus(focusDirection = FocusDirection.Down)
        }
    }

    val configByInput = remember(inputConfig) { inputConfig.associateBy { it.input } }
    val faceAndDpadConfig = remember(configByInput) {
        FACE_AND_DPAD_INPUTS.mapNotNull { configByInput[it] }
    }
    val shoulderAndSystemConfig = remember(configByInput) {
        SHOULDER_AND_SYSTEM_INPUTS.mapNotNull { configByInput[it] }
    }
    val hotkeyConfig = remember(inputConfig) {
        inputConfig.filter { it.input !in CONSOLE_INPUTS && getInputNameStringRes(it.input) != null }
    }
    val mappedConsoleCount = remember(faceAndDpadConfig, shoulderAndSystemConfig) {
        (faceAndDpadConfig + shoulderAndSystemConfig).count { it.hasKeyAssigned() }
    }
    val totalConsoleCount = faceAndDpadConfig.size + shoulderAndSystemConfig.size

    WatermelonScreenScaffold(
        title = stringResource(R.string.key_mapping),
        onBack = onBackClick,
        actions = {
            var menuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.v780_input_auto_map_title),
                        tint = watermelon.text,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(onClick = {
                        menuExpanded = false
                        onAutoMapClick()
                    }) {
                        Text(stringResource(R.string.v780_input_auto_map_title))
                    }
                    DropdownMenuItem(onClick = {
                        menuExpanded = false
                        onClearAllClick()
                    }) {
                        Text(stringResource(R.string.v780_input_clear_all_title))
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().consumeWindowInsets(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 12.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    ThreeDsControllerDeckCard(
                        configByInput = configByInput,
                        swatches = swatches,
                        activeThemeLabel = activeThemeLabel,
                        mappedCount = mappedConsoleCount,
                        totalCount = totalConsoleCount,
                        onAutoMapClick = onAutoMapClick,
                        onClearAllClick = onClearAllClick,
                    )
                }

                item {
                    Spacer(Modifier.height(4.dp))
                    SectionHeader(title = stringResource(R.string.input_group_face_dpad))
                }
                items(items = faceAndDpadConfig, key = { it.input }) { config ->
                    InputMappingCard(
                        config = config,
                        swatches = swatches,
                        isBeingConfigured = config.input == inputUnderConfiguration,
                        onClick = { onInputClick(config.input) },
                        onClearClick = { onClearInputClick(config.input) },
                    )
                }

                item {
                    Spacer(Modifier.height(6.dp))
                    SectionHeader(title = stringResource(R.string.input_group_shoulders_system))
                }
                items(items = shoulderAndSystemConfig, key = { it.input }) { config ->
                    InputMappingCard(
                        config = config,
                        swatches = swatches,
                        isBeingConfigured = config.input == inputUnderConfiguration,
                        onClick = { onInputClick(config.input) },
                        onClearClick = { onClearInputClick(config.input) },
                    )
                }

                item {
                    Spacer(Modifier.height(6.dp))
                    SectionHeader(title = stringResource(R.string.input_3ds_analog_sticks_title))
                }
                item {
                    AnalogSticksInfoCard(swatches = swatches)
                }

                item {
                    Spacer(Modifier.height(6.dp))
                    SectionHeader(title = stringResource(R.string.input_group_hotkeys))
                }
                items(items = hotkeyConfig, key = { it.input }) { config ->
                    InputMappingCard(
                        config = config,
                        swatches = swatches,
                        isBeingConfigured = config.input == inputUnderConfiguration,
                        onClick = { onInputClick(config.input) },
                        onClearClick = { onClearInputClick(config.input) },
                    )
                }
            }

            if (inputUnderConfiguration != null) {
                WaitingForInputOverlay(
                    targetName = getInputName(inputUnderConfiguration) ?: inputUnderConfiguration.name,
                    swatches = swatches,
                    onCancel = onCancelInputConfiguration,
                )
            }
        }
    }
}

@Composable
private fun ThreeDsControllerDeckCard(
    configByInput: Map<Input, InputConfig>,
    swatches: ButtonSwatchColors,
    activeThemeLabel: String,
    mappedCount: Int,
    totalCount: Int,
    onAutoMapClick: () -> Unit,
    onClearAllClick: () -> Unit,
) {
    val colors = watermelon
    val shape = RoundedCornerShape(16.dp)
    val isBound = { input: Input -> configByInput[input]?.hasKeyAssigned() == true }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(colors.surface2, colors.surface.copy(alpha = 0.96f)),
                ),
            )
            .border(1.2.dp, swatches.glow.copy(alpha = 0.55f), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(swatches.glow.copy(alpha = 0.16f))
                        .border(1.dp, swatches.glow.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = swatches.glow,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.input_group_console),
                        color = colors.text,
                        fontFamily = SpaceGrotesk,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = activeThemeLabel,
                        color = swatches.glow,
                        fontFamily = SpaceGrotesk,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(swatches.glow.copy(alpha = 0.16f))
                    .border(1.dp, swatches.glow.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "$mappedCount / $totalCount",
                    color = swatches.glow,
                    fontFamily = WatermelonMono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Top shoulder deck preview (ZL / L ... R / ZR)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DeckMiniPill("ZL", isBound(Input.ZL), swatches.glow)
                DeckMiniPill("L", isBound(Input.L), swatches.glow)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DeckMiniPill("R", isBound(Input.R), swatches.glow)
                DeckMiniPill("ZR", isBound(Input.ZR), swatches.glow)
            }
        }

        // Main 3DS Deck: Left (Circle Pad + D-Pad) | Center (SELECT/HOME/START) | Right (ABXY + C-Stick)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Left: Circle Pad + D-Pad
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Circle Pad ring
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(Color(0xFF334155), Color(0xFF0F172A))))
                        .border(1.5.dp, swatches.glow.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B))
                            .border(1.dp, Color(0xFF64748B), CircleShape),
                    )
                }

                // D-Pad schematic
                Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                    DeckDpadDir("▲", Modifier.align(Alignment.TopCenter), isBound(Input.UP), swatches.glow)
                    DeckDpadDir("▼", Modifier.align(Alignment.BottomCenter), isBound(Input.DOWN), swatches.glow)
                    DeckDpadDir("◀", Modifier.align(Alignment.CenterStart), isBound(Input.LEFT), swatches.glow)
                    DeckDpadDir("▶", Modifier.align(Alignment.CenterEnd), isBound(Input.RIGHT), swatches.glow)
                }
            }

            // Center system strip: SELECT / HOME / START
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                DeckMiniPill("HOME", isBound(Input.HOME), swatches.glow)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    DeckMiniPill("SEL", isBound(Input.SELECT), swatches.glow)
                    DeckMiniPill("STA", isBound(Input.START), swatches.glow)
                }
            }

            // Right: ABXY Glossy Diamond + C-Stick
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // C-Stick nub
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF334155))
                        .border(1.dp, swatches.glow.copy(alpha = 0.65f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("C", color = Color(0xFFE2E8F0), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }

                // ABXY diamond
                Box(modifier = Modifier.size(62.dp)) {
                    MiniFaceCap("X", swatches.capX, swatches.glow, isBound(Input.X), 21.dp, Modifier.align(Alignment.TopCenter))
                    MiniFaceCap("B", swatches.capB, swatches.glow, isBound(Input.B), 21.dp, Modifier.align(Alignment.BottomCenter))
                    MiniFaceCap("Y", swatches.capY, swatches.glow, isBound(Input.Y), 21.dp, Modifier.align(Alignment.CenterStart))
                    MiniFaceCap("A", swatches.capA, swatches.glow, isBound(Input.A), 21.dp, Modifier.align(Alignment.CenterEnd))
                }
            }
        }

        // Quick action bar: Auto-Map Gamepad | Clear All
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(swatches.glow.copy(alpha = 0.16f))
                    .border(1.dp, swatches.glow.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                    .clickable(onClick = onAutoMapClick)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Default.SportsEsports, contentDescription = null, tint = swatches.glow, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.v780_input_auto_map_title),
                    color = colors.text,
                    fontFamily = SpaceGrotesk,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface3)
                    .border(1.dp, colors.line, RoundedCornerShape(10.dp))
                    .clickable(onClick = onClearAllClick)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = colors.text2, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    text = stringResource(R.string.v780_input_clear_all_title),
                    color = colors.text2,
                    fontFamily = SpaceGrotesk,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DeckMiniPill(label: String, bound: Boolean, glow: Color) {
    val colors = watermelon
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (bound) glow.copy(alpha = 0.2f) else colors.surface3.copy(alpha = 0.6f))
            .border(1.dp, if (bound) glow else colors.line, RoundedCornerShape(7.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (bound) colors.text else colors.text3,
            fontFamily = SpaceGrotesk,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DeckDpadDir(glyph: String, modifier: Modifier, bound: Boolean, glow: Color) {
    val colors = watermelon
    Box(
        modifier = modifier
            .size(15.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (bound) glow.copy(alpha = 0.22f) else colors.surface3)
            .border(0.8.dp, if (bound) glow else colors.line, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = if (bound) glow else colors.text3, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MiniFaceCap(
    letter: String,
    capColors: Pair<Color, Color>,
    glow: Color,
    bound: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val labelColor = if (capColors.first.luminance() > 0.65f) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(capColors.first, capColors.second)))
            .border(
                width = if (bound) 1.5.dp else 1.dp,
                color = if (bound) glow else glow.copy(alpha = 0.35f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            color = labelColor.copy(alpha = if (bound) 1f else 0.72f),
            fontFamily = SpaceGrotesk,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AnalogSticksInfoCard(swatches: ButtonSwatchColors) {
    val colors = watermelon
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface2)
            .border(1.dp, swatches.glow.copy(alpha = 0.35f), shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFF334155), Color(0xFF0F172A))))
                    .border(1.2.dp, swatches.glow.copy(alpha = 0.75f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("LS", color = colors.text, fontFamily = SpaceGrotesk, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFF334155), Color(0xFF0F172A))))
                    .border(1.2.dp, swatches.glow.copy(alpha = 0.75f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("RS", color = colors.text, fontFamily = SpaceGrotesk, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            text = stringResource(R.string.input_3ds_analog_sticks_summary),
            color = colors.text2,
            style = MaterialTheme.typography.caption,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    val colors = watermelon
    Text(
        text = title.uppercase(),
        color = colors.green,
        fontFamily = SpaceGrotesk,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InputMappingCard(
    config: InputConfig,
    swatches: ButtonSwatchColors,
    isBeingConfigured: Boolean,
    onClick: () -> Unit,
    onClearClick: () -> Unit,
) {
    val (main, clear) = remember { FocusRequester.createRefs() }
    val colors = watermelon
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(14.dp)
    val assignments = remember(config.assignment, config.altAssignment) {
        listOf(config.assignment, config.altAssignment).filter { it != InputConfig.Assignment.None }
    }
    val hasBinding = assignments.isNotEmpty()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    isBeingConfigured -> swatches.glow.copy(alpha = 0.14f)
                    isFocused -> colors.surface3
                    else -> colors.surface2
                },
            )
            .border(
                width = if (isBeingConfigured || isFocused) 1.6.dp else 1.dp,
                color = when {
                    isBeingConfigured || isFocused -> swatches.glow
                    hasBinding -> swatches.glow.copy(alpha = 0.42f)
                    else -> colors.line
                },
                shape = shape,
            )
            .focusRequester(main)
            .focusProperties { end = if (hasBinding) clear else FocusRequester.Default }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(start = 12.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
    ) {
        ThreeDsButtonCapGlyph(
            input = config.input,
            swatches = swatches,
            isActive = hasBinding || isBeingConfigured,
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = getInputName(config.input) ?: config.input.name,
                color = colors.text,
                fontFamily = SpaceGrotesk,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (isBeingConfigured) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(swatches.glow.copy(alpha = 0.2f))
                        .border(1.dp, swatches.glow, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = stringResource(R.string.press_any_button),
                        color = swatches.glow,
                        fontFamily = SpaceGrotesk,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else if (assignments.isEmpty()) {
                Text(
                    text = stringResource(R.string.not_set),
                    color = colors.text3,
                    fontSize = 12.sp,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    assignments.forEach { assignment ->
                        PhysicalKeycapBadge(
                            label = formatAssignmentLabel(assignment),
                            glow = swatches.glow,
                        )
                    }
                }
            }
        }

        if (hasBinding) {
            IconButton(
                modifier = Modifier
                    .size(34.dp)
                    .focusRequester(clear)
                    .focusProperties { start = main },
                onClick = onClearClick,
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(colors.surface3),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.clear),
                        tint = colors.text2,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ThreeDsButtonCapGlyph(
    input: Input,
    swatches: ButtonSwatchColors,
    isActive: Boolean,
) {
    val colors = watermelon
    when (input) {
        Input.A, Input.B, Input.X, Input.Y -> {
            val pair = when (input) {
                Input.A -> swatches.capA
                Input.B -> swatches.capB
                Input.X -> swatches.capX
                Input.Y -> swatches.capY
                else -> swatches.capA
            }
            val labelColor = if (pair.first.luminance() > 0.65f) Color(0xFF0F172A) else Color(0xFFF8FAFC)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(pair.first, pair.second)))
                    .border(
                        width = if (isActive) 1.6.dp else 1.dp,
                        color = if (isActive) swatches.glow else swatches.glow.copy(alpha = 0.45f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = input.name,
                    color = labelColor,
                    fontFamily = SpaceGrotesk,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Input.UP, Input.DOWN, Input.LEFT, Input.RIGHT -> {
            val glyph = when (input) {
                Input.UP -> "▲"
                Input.DOWN -> "▼"
                Input.LEFT -> "◀"
                Input.RIGHT -> "▶"
                else -> "•"
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF2B3448), Color(0xFF131824))))
                    .border(1.3.dp, if (isActive) swatches.glow else colors.line, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = glyph,
                    color = if (isActive) swatches.glow else colors.text2,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Input.L, Input.R, Input.ZL, Input.ZR -> {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF364156), Color(0xFF171D2B))))
                    .border(1.3.dp, if (isActive) swatches.glow else colors.line, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = input.name,
                    color = if (isActive) swatches.glow else colors.text,
                    fontFamily = SpaceGrotesk,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Input.HOME -> {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFF253042), Color(0xFF121824))))
                    .border(1.3.dp, if (isActive) swatches.glow else colors.line, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = null,
                    tint = if (isActive) swatches.glow else colors.text2,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Input.START, Input.SELECT, Input.HINGE -> {
            val tag = when (input) {
                Input.START -> "ST"
                Input.SELECT -> "SL"
                Input.HINGE -> "3D"
                else -> input.name.take(2)
            }
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.verticalGradient(listOf(Color(0xFF263145), Color(0xFF121824))))
                    .border(1.2.dp, if (isActive) swatches.glow else colors.line, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tag,
                    color = if (isActive) swatches.glow else colors.text2,
                    fontFamily = SpaceGrotesk,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        else -> {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isActive) swatches.glow.copy(alpha = 0.16f) else colors.surface3,
                    )
                    .border(
                        width = 1.dp,
                        color = if (isActive) swatches.glow.copy(alpha = 0.7f) else colors.line,
                        shape = RoundedCornerShape(10.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.SportsEsports,
                    contentDescription = null,
                    tint = if (isActive) swatches.glow else colors.text2,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun PhysicalKeycapBadge(label: String, glow: Color) {
    val colors = watermelon
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0A0F1A))
            .border(1.dp, glow.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(bottom = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF243044), Color(0xFF141C2B))))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = colors.text,
                fontFamily = WatermelonMono,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatAssignmentLabel(assignment: InputConfig.Assignment): String {
    return when (assignment) {
        is InputConfig.Assignment.Key -> {
            KeyEvent.keyCodeToString(assignment.keyCode)
                .removePrefix("KEYCODE_")
                .replace("BUTTON_", "BTN ")
                .replace("_", " ")
                .trim()
        }
        is InputConfig.Assignment.Axis -> {
            val axisPrettyName = MotionEvent.axisToString(assignment.axisCode)
                .removePrefix("AXIS_")
                .replace("_", " ")
                .trim()
            val sign = if (assignment.direction == InputConfig.Assignment.Axis.Direction.NEGATIVE) "-" else "+"
            "$axisPrettyName$sign"
        }
        is InputConfig.Assignment.Chord -> {
            val modStr = KeyEvent.keyCodeToString(assignment.modifierKeyCode)
                .removePrefix("KEYCODE_")
                .replace("BUTTON_", "BTN ")
                .replace("_", " ")
                .trim()
            val trigStr = KeyEvent.keyCodeToString(assignment.triggerKeyCode)
                .removePrefix("KEYCODE_")
                .replace("BUTTON_", "BTN ")
                .replace("_", " ")
                .trim()
            "$modStr + $trigStr"
        }
        InputConfig.Assignment.None -> ""
    }
}

@Composable
private fun WaitingForInputOverlay(
    targetName: String,
    swatches: ButtonSwatchColors,
    onCancel: () -> Unit,
) {
    val colors = watermelon
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(enabled = true, onClick = { }),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surface2)
                .border(1.5.dp, swatches.glow, RoundedCornerShape(18.dp))
                .padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = targetName,
                color = swatches.glow,
                fontFamily = SpaceGrotesk,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.waiting_for_input),
                color = colors.text,
                style = MaterialTheme.typography.body1,
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onCancel) {
                Text(stringResource(android.R.string.cancel), color = swatches.glow)
            }
        }
    }
}

private fun getInputNameStringRes(input: Input): Int? {
    return when (input) {
        Input.A -> R.string.input_a
        Input.B -> R.string.input_b
        Input.X -> R.string.input_x
        Input.Y -> R.string.input_y
        Input.LEFT -> R.string.input_left
        Input.RIGHT -> R.string.input_right
        Input.UP -> R.string.input_up
        Input.DOWN -> R.string.input_down
        Input.L -> R.string.input_l
        Input.R -> R.string.input_r
        Input.ZL -> R.string.input_zl
        Input.ZR -> R.string.input_zr
        Input.START -> R.string.input_start
        Input.SELECT -> R.string.input_select
        Input.HOME -> R.string.input_home
        Input.HINGE -> R.string.input_lid
        Input.PAUSE -> R.string.input_pause
        Input.FAST_FORWARD -> R.string.input_fast_forward
        Input.HOLD_FAST_FORWARD -> R.string.input_hold_fast_forward
        Input.MICROPHONE -> R.string.input_microphone
        Input.RESET -> R.string.input_reset
        Input.SWAP_SCREENS -> R.string.input_swap_screens
        Input.CYCLE_LAYOUT -> R.string.input_cycle_layout
        Input.QUICK_SAVE -> R.string.input_quick_save
        Input.QUICK_LOAD -> R.string.input_quick_load
        Input.REWIND -> R.string.rewind
        Input.TRANSLATE -> R.string.input_translate
        Input.LOCK_ROTATION -> R.string.input_lock_rotation
        Input.TOGGLE_EXTRA_BUTTONS -> R.string.input_toggle_extra_buttons
        Input.TOGGLE_ANALOG_STICK -> R.string.input_toggle_analog_stick
        Input.SLOW_MOTION -> R.string.input_slow_motion
        Input.FRAME_ADVANCE -> R.string.input_frame_advance
        Input.TAKE_SCREENSHOT -> R.string.input_take_screenshot
        Input.TOGGLE_HARDWARE_TURBO -> R.string.input_toggle_hardware_turbo
        Input.COMBO_1 -> R.string.input_combo_1
        Input.COMBO_2 -> R.string.input_combo_2
        else -> null
    }
}

@Composable
private fun getInputName(input: Input): String? {
    val resource = getInputNameStringRes(input) ?: return null
    return stringResource(resource)
}

@MelonPreviewSet
@Composable
private fun PreviewInputSetupScreen() {
    MelonTheme {
        InputSetupScreenContent(
            inputConfig = listOf(
                InputConfig(Input.A, InputConfig.Assignment.Key(null, KeyEvent.KEYCODE_BUTTON_A)),
                InputConfig(Input.B, InputConfig.Assignment.Key(null, KeyEvent.KEYCODE_BUTTON_B)),
                InputConfig(Input.X, InputConfig.Assignment.Key(null, KeyEvent.KEYCODE_BUTTON_X)),
                InputConfig(Input.Y, InputConfig.Assignment.Key(null, KeyEvent.KEYCODE_BUTTON_Y)),
                InputConfig(Input.UP, InputConfig.Assignment.Key(null, KeyEvent.KEYCODE_DPAD_UP)),
                InputConfig(Input.DOWN, InputConfig.Assignment.Key(null, KeyEvent.KEYCODE_DPAD_DOWN)),
                InputConfig(Input.LEFT, InputConfig.Assignment.Key(null, KeyEvent.KEYCODE_DPAD_LEFT)),
                InputConfig(Input.RIGHT, InputConfig.Assignment.Key(null, KeyEvent.KEYCODE_DPAD_RIGHT)),
            ),
            slot2AnalogMapping = Slot2AnalogMapping(),
            inputUnderConfiguration = null,
            slot2AxisUnderConfiguration = null,
            onInputAssignedEvent = emptyFlow(),
            onInputClick = { },
            onClearInputClick = { },
            onSlot2AxisXClick = { },
            onSlot2AxisYClick = { },
            onSlot2InvertXChanged = { },
            onSlot2InvertYChanged = { },
            onDeadzoneChanged = { },
            onUseDeviceFilterChanged = { },
            onCancelInputConfiguration = { },
            onBackClick = { },
        )
    }
}
