package com.example.wallko.ui.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.wallko.R
import com.example.wallko.ml.stylize.StyleId
import com.example.wallko.ui.components.ToyButton
import com.example.wallko.ui.theme.WallkoColors
import com.example.wallko.ui.theme.WallkoShapes
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Settings, reached from the Garden top bar. "About & licences" opens [LicencesScreen] as an
 * internal sub-screen (system back returns here), so no extra navigation route is needed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var showLicences by rememberSaveable { mutableStateOf(false) }

    fun toast(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) viewModel.exportTo(uri) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            when (message) {
                is SettingsMessage.Exported -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    snackbar.showSnackbar(
                        context.getString(R.string.settings_export_done, message.summary.plants, message.summary.walks),
                    )
                }
                SettingsMessage.ExportFailed -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                    snackbar.showSnackbar(context.getString(R.string.settings_export_failed))
                }
            }
        }
    }

    if (showLicences) {
        BackHandler { showLicences = false }
        LicencesScreen(onBack = { showLicences = false }, modifier = modifier)
        return
    }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                }
                Text(
                    stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.semantics { heading() },
                )
            }

            // Reminders
            SettingsCard(Icons.Rounded.Notifications, stringResource(R.string.settings_reminders_header)) {
                val onOff = stringResource(
                    if (state.remindersEnabled) R.string.settings_reminders_on else R.string.settings_reminders_off,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .toggleable(
                            value = state.remindersEnabled,
                            role = Role.Switch,
                            onValueChange = { on ->
                                haptics.performHapticFeedback(
                                    if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                                )
                                viewModel.setRemindersEnabled(on)
                            },
                        )
                        .semantics { stateDescription = onOff }
                        .heightIn(min = 48.dp)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.settings_reminders_label),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    // Row owns the click + semantics; the switch is visual only.
                    Switch(
                        checked = state.remindersEnabled,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(checkedTrackColor = WallkoColors.Grass),
                    )
                }
                Spacer(Modifier.height(4.dp))
                val time = formatMinute(state.reminderMinuteOfDay)
                val timeCd = stringResource(R.string.settings_reminder_time_cd, time)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = state.remindersEnabled, role = Role.Button) {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            showTimePicker = true
                        }
                        .semantics(mergeDescendants = true) { contentDescription = timeCd }
                        .heightIn(min = 48.dp)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.settings_reminder_time),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        time,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (state.remindersEnabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Garden style
            SettingsCard(Icons.Rounded.Palette, stringResource(R.string.settings_garden_style_header)) {
                Text(stringResource(R.string.settings_style_hint), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StyleId.entries.forEach { style ->
                        StyleChoice(
                            style = style,
                            selected = state.style == style,
                            onClick = {
                                if (state.style != style) {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    toast(context.getString(R.string.settings_style_repainting))
                                }
                                viewModel.setStyle(style)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Units
            SettingsCard(Icons.Rounded.Straighten, stringResource(R.string.settings_units_header)) {
                Text(stringResource(R.string.settings_units_hint), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    UnitSystem.entries.forEachIndexed { i, u ->
                        SegmentedButton(
                            selected = state.units == u,
                            onClick = {
                                if (state.units != u) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                viewModel.setUnits(u)
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, UnitSystem.entries.size),
                        ) {
                            Text(
                                stringResource(
                                    if (u == UnitSystem.METRIC) R.string.settings_units_metric else R.string.settings_units_imperial,
                                ),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }

            // Your data
            SettingsCard(Icons.Rounded.Download, stringResource(R.string.settings_data_header)) {
                Text(stringResource(R.string.settings_data_hint), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                val noPicker = stringResource(R.string.settings_export_no_picker)
                ToyButton(
                    text = stringResource(if (state.exporting) R.string.settings_export_working else R.string.settings_export),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        try {
                            exportLauncher.launch(viewModel.suggestedExportName())
                        } catch (e: ActivityNotFoundException) {
                            toast(noPicker)
                        }
                    },
                    icon = Icons.Rounded.Download,
                    enabled = !state.exporting,
                    containerColor = WallkoColors.Sky,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // About
            SettingsCard(Icons.Rounded.Info, stringResource(R.string.settings_about_header)) {
                Text(stringResource(R.string.settings_about_body), style = MaterialTheme.typography.bodyMedium)
                if (viewModel.versionName.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_version, viewModel.versionName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button) { showLicences = true }
                        .heightIn(min = 48.dp)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.settings_about_licences),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(MaterialTheme.typography.titleSmall.iconSize()),
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    if (showTimePicker) {
        ReminderTimeDialog(
            initialMinute = state.reminderMinuteOfDay,
            onDismiss = { showTimePicker = false },
            onConfirm = {
                showTimePicker = false
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                viewModel.setReminderMinuteOfDay(it)
            },
        )
    }
}

/** Icon size that tracks the text style (and so the user's font scale). */
@Composable
internal fun TextStyle.iconSize(): Dp = with(LocalDensity.current) {
    when {
        lineHeight.isSp -> lineHeight.toDp()
        fontSize.isSp -> fontSize.toDp() * 1.4f
        else -> 24.dp
    }
}

@Composable
private fun SettingsCard(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    Surface(
        shape = WallkoShapes.Card,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            val titleStyle = MaterialTheme.typography.titleMedium
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) { heading() },
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(titleStyle.iconSize()),
                )
                Spacer(Modifier.width(8.dp))
                Text(title, style = titleStyle, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun StyleChoice(style: StyleId, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(
        when (style) {
            StyleId.WATERCOLOUR -> R.string.settings_style_watercolour
            StyleId.FLAT_PASTEL -> R.string.settings_style_flat_pastel
            StyleId.CRAYON -> R.string.settings_style_crayon
        },
    )
    Surface(
        shape = WallkoShapes.Card,
        border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) WallkoColors.Grass else MaterialTheme.colorScheme.outlineVariant),
        color = if (selected) WallkoColors.Grass.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
        modifier = modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(
                model = "file:///android_asset/${style.assetPath}",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(18.dp)),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(initialMinute: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val context = LocalContext.current
    val picker = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = android.text.format.DateFormat.is24HourFormat(context),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_reminder_time_pick)) },
        text = { TimePicker(state = picker) },
        confirmButton = {
            TextButton(onClick = { onConfirm(picker.hour * 60 + picker.minute) }) {
                Text(stringResource(R.string.settings_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

private fun formatMinute(minuteOfDay: Int): String =
    LocalTime.of(minuteOfDay / 60 % 24, minuteOfDay % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
