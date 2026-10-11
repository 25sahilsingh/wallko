package com.example.wallko.ui.onboarding

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wallko.core.prefs.SettingsStore
import com.example.wallko.walk.HealthConnectStatus
import com.example.wallko.walk.StepSync
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OnboardingPhase { STORY, PERMISSIONS, FINISHED }

data class OnboardingUiState(
    val phase: OnboardingPhase = OnboardingPhase.STORY,
    /** Permission cards still to show, computed when the story ends. */
    val steps: List<PermissionStep> = emptyList(),
    val stepIndex: Int = 0,
    /** True while working out which steps are needed (Health Connect check is async). */
    val preparing: Boolean = false,
) {
    val currentStep: PermissionStep? get() = steps.getOrNull(stepIndex)
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    private val stepSync: StepSync,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    val healthPermissions: Set<String> get() = stepSync.healthPermissions

    fun healthPermissionContract(): ActivityResultContract<Set<String>, Set<String>> = stepSync.permissionContract()

    fun installHealthConnectIntent(): Intent = stepSync.installHealthConnectIntent()

    /** Story finished or skipped: work out which permission cards are still needed. */
    fun startPermissions() {
        if (_state.value.phase != OnboardingPhase.STORY || _state.value.preparing) return
        _state.update { it.copy(preparing = true) }
        viewModelScope.launch {
            val sdk = Build.VERSION.SDK_INT
            val granted = OnboardingSteps.candidatePermissions(sdk).filter {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }.toSet()
            val healthStatus = runCatching { stepSync.healthConnectStatus() }
                .getOrDefault(HealthConnectStatus.UNAVAILABLE)
            val healthGranted = runCatching { stepSync.hasHealthPermission() }.getOrDefault(false)
            val steps = OnboardingSteps.permissionSteps(sdk, granted, healthStatus, healthGranted)
            if (steps.isEmpty()) {
                finish()
            } else {
                _state.update { it.copy(phase = OnboardingPhase.PERMISSIONS, steps = steps, stepIndex = 0, preparing = false) }
            }
        }
    }

    /** Called after "Allow" returns (granted or not) and after "Not now". Denials are fine: just move on. */
    fun next(from: PermissionStep) {
        val s = _state.value
        // Ignore stale / double callbacks for a card that's already gone.
        if (s.phase != OnboardingPhase.PERMISSIONS || s.currentStep != from) return
        if (s.stepIndex + 1 >= s.steps.size) finish() else _state.update { it.copy(stepIndex = it.stepIndex + 1) }
    }

    private fun finish() {
        viewModelScope.launch {
            settings.setOnboardingDone(true)
            _state.update { it.copy(phase = OnboardingPhase.FINISHED, preparing = false) }
        }
    }
}
