package com.example.wallko.ui.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wallko.core.prefs.SettingsStore
import com.example.wallko.data.export.DataExporter
import com.example.wallko.data.export.ExportSummary
import com.example.wallko.data.garden.GardenRepository
import com.example.wallko.game.SpriteWorker
import com.example.wallko.ml.stylize.StyleId
import com.example.wallko.notifications.MorningNudgeScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val remindersEnabled: Boolean = true,
    val reminderMinuteOfDay: Int = 9 * 60,
    val style: StyleId = StyleId.WATERCOLOUR,
    val units: UnitSystem = UnitSystem.METRIC,
    val exporting: Boolean = false,
)

/** One-shot messages for the snackbar. */
sealed interface SettingsMessage {
    data class Exported(val summary: ExportSummary) : SettingsMessage
    data object ExportFailed : SettingsMessage
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val garden: GardenRepository,
    private val nudgeScheduler: MorningNudgeScheduler,
    private val exporter: DataExporter,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val exporting = MutableStateFlow(false)

    private val _messages = Channel<SettingsMessage>(Channel.BUFFERED)
    val messages: Flow<SettingsMessage> = _messages.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        settings.remindersEnabled,
        settings.reminderMinuteOfDay,
        settings.spriteStyleId,
        settings.units,
        exporting,
    ) { enabled, minute, style, units, busy ->
        SettingsUiState(enabled, minute, SpriteWorker.styleIdFromName(style), UnitSystem.fromStored(units), busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setRemindersEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setRemindersEnabled(enabled)
        reschedule()
    }

    fun setReminderMinuteOfDay(minute: Int) = viewModelScope.launch {
        settings.setReminderMinuteOfDay(minute.coerceIn(0, 24 * 60 - 1))
        reschedule()
    }

    /** Saves the style and re-paints every owned plant (withered too) in it. */
    fun setStyle(style: StyleId) = viewModelScope.launch {
        val stored = settings.spriteStyleId.first()
        if (stored != null && SpriteWorker.styleIdFromName(stored) == style) return@launch
        settings.setSpriteStyleId(style.name)
        val ids = garden.observeCollection().first().map { it.plant.id }
        SpriteWorker.requeueAll(context, ids, style)
    }

    fun setUnits(units: UnitSystem) = viewModelScope.launch { settings.setUnits(units.name) }

    /** File name suggested to the SAF picker, e.g. "wallko-export-2026-10-10.json". */
    fun suggestedExportName(): String = exporter.suggestedFileName()

    /** Writes the JSON export to the document the user just created. */
    fun exportTo(uri: Uri) {
        if (exporting.value) return
        exporting.value = true
        viewModelScope.launch {
            val message = try {
                SettingsMessage.Exported(exporter.exportTo(uri))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("SettingsViewModel", "export failed", e)
                SettingsMessage.ExportFailed
            } finally {
                exporting.value = false
            }
            _messages.send(message)
        }
    }

    val versionName: String by lazy {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private suspend fun reschedule() {
        try {
            nudgeScheduler.reschedule()
        } catch (e: Exception) {
            Log.w("SettingsViewModel", "reschedule failed", e)
        }
    }
}
