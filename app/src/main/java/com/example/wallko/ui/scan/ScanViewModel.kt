package com.example.wallko.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import androidx.annotation.StringRes
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wallko.R
import com.example.wallko.core.db.Rarity
import com.example.wallko.core.time.Clock
import com.example.wallko.data.garden.GardenRepository
import com.example.wallko.data.plants.PlantRepository
import com.example.wallko.game.FoundPlace
import com.example.wallko.game.ScanToCardResult
import com.example.wallko.game.ScanToCardUseCase
import com.example.wallko.ml.classifier.PlantClassifier
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** One of the top-3 guesses, enriched with catalogue data for the sheet. */
data class GuessUi(
    val outputIndex: Int,
    val title: String,
    val subtitle: String?,
    val score: Float,
    val rarity: Rarity?,
    val inGarden: Boolean,
)

data class ScanResultUi(
    val photoPath: String,
    val guesses: List<GuessUi>,
    val confident: Boolean,
    val selected: Int?,
)

enum class BusyStage(@param:StringRes val message: Int) {
    CAPTURING(R.string.scan_capturing),
    IDENTIFYING(R.string.scan_identifying),
    PLANTING(R.string.scan_planting),
}

sealed interface ScanUiState {
    /** Live camera, ready to capture. */
    data object Live : ScanUiState

    /** [photoPath] (when known) is shown frozen behind the progress bubble. */
    data class Busy(val stage: BusyStage, val photoPath: String? = null) : ScanUiState

    data class Guesses(val result: ScanResultUi) : ScanUiState

    data class NewCard(val plantId: Long, val bonusDrops: Int) : ScanUiState

    data class AlreadyInGarden(val plantId: Long, val name: String) : ScanUiState

    /** [backTo] is where "OK" returns (the guesses, so planting can be retried); null = live camera. */
    data class Failed(@param:StringRes val message: Int, val backTo: Guesses? = null) : ScanUiState
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val classifier: PlantClassifier,
    private val plants: PlantRepository,
    private val garden: GardenRepository,
    private val scanToCard: ScanToCardUseCase,
    private val places: FoundPlaceProvider,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Live)
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    /** True when the camera couldn't be opened; the screen shows a friendly message instead of the viewfinder. */
    private val _cameraError = MutableStateFlow(false)
    val cameraError: StateFlow<Boolean> = _cameraError.asStateFlow()

    private val captureExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "wallko-capture").apply { isDaemon = true }
    }

    private val preview: Preview = Preview.Builder().build().apply {
        setSurfaceProvider { request -> _surfaceRequest.value = request }
    }

    private val imageCapture: ImageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(ScanMath.MAX_PHOTO_EDGE, ScanMath.MAX_PHOTO_EDGE * 3 / 4),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                    ),
                )
                .build(),
        )
        .build()

    private var placeLookup: Deferred<FoundPlace>? = null
    private var work: Job? = null

    /** Photo saved for the current attempt that no card owns yet; deleted on retake. */
    private var pendingPhoto: File? = null

    /**
     * Binds preview + capture to [lifecycleOwner] and suspends until cancelled, then unbinds.
     * Call from a LaunchedEffect (main thread) once the camera permission is granted.
     */
    suspend fun bindToCamera(lifecycleOwner: LifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(appContext)
        try {
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            // No back camera, or the camera is busy / disabled by policy.
            Log.w(TAG, "Binding the camera failed", e)
            _cameraError.value = true
            return
        }
        _cameraError.value = false
        try {
            awaitCancellation()
        } finally {
            provider.unbind(preview, imageCapture)
            _surfaceRequest.value = null
        }
    }

    fun capture() {
        if (_state.value != ScanUiState.Live || work?.isActive == true) return
        work = viewModelScope.launch {
            _state.value = ScanUiState.Busy(BusyStage.CAPTURING)
            // Location runs alongside identification and is awaited (briefly) only on confirm.
            placeLookup = viewModelScope.async(start = CoroutineStart.DEFAULT) { places.lookup() }

            val bitmap: Bitmap = try {
                imageCapture.takeUprightBitmap(captureExecutor)
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                Log.w(TAG, "Capture failed", e)
                _state.value = ScanUiState.Failed(R.string.scan_error_capture)
                return@launch
            }

            val photo = try {
                savePhotoJpeg(appContext.filesDir, bitmap, clock.now().toEpochMilli())
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                Log.w(TAG, "Saving photo failed", e)
                _state.value = ScanUiState.Failed(R.string.scan_error_capture)
                return@launch
            }
            pendingPhoto = photo
            _state.value = ScanUiState.Busy(BusyStage.IDENTIFYING, photo.absolutePath)

            val result = try {
                val classification = classifier.classify(bitmap)
                val guesses = classification.top3.map { g ->
                    val speciesIndex = plants.labelIndexToSpeciesIndex(g.outputIndex)
                    val species = speciesIndex?.let { plants.byIndex(it) }
                    val common = species?.common ?: g.common
                    val latin = species?.latin ?: g.latin
                    GuessUi(
                        outputIndex = g.outputIndex,
                        title = ScanMath.guessTitle(common, latin),
                        subtitle = ScanMath.guessSubtitle(common, latin),
                        score = g.score,
                        rarity = species?.rarity,
                        inGarden = speciesIndex != null && garden.isInGarden(speciesIndex),
                    )
                }
                ScanResultUi(
                    photoPath = photo.absolutePath,
                    guesses = guesses,
                    confident = classification.confident,
                    selected = ScanMath.initialSelection(guesses.size, classification.confident),
                )
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                Log.w(TAG, "Classification failed", e)
                discardPendingPhoto()
                _state.value = ScanUiState.Failed(R.string.scan_error_identify)
                return@launch
            }
            _state.value = ScanUiState.Guesses(result)
        }
    }

    fun select(index: Int) {
        _state.update { s ->
            if (s is ScanUiState.Guesses && index in s.result.guesses.indices) {
                ScanUiState.Guesses(s.result.copy(selected = index))
            } else s
        }
    }

    fun confirm() {
        val current = _state.value as? ScanUiState.Guesses ?: return
        val index = current.result.selected ?: return
        val guess = current.result.guesses.getOrNull(index) ?: return
        val photo = pendingPhoto ?: return
        if (work?.isActive == true) return
        work = viewModelScope.launch {
            _state.value = ScanUiState.Busy(BusyStage.PLANTING, photo.absolutePath)
            val place = withTimeoutOrNull(PLACE_WAIT_MS) { placeLookup?.await() } ?: FoundPlace.Unknown
            val outcome = try {
                scanToCard.confirm(guess.outputIndex, photo.absolutePath, place)
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                Log.w(TAG, "Planting failed", e)
                // "OK" goes back to the guesses so the player can retry without re-shooting.
                _state.value = ScanUiState.Failed(R.string.scan_error_save, backTo = current)
                return@launch
            }
            _state.value = when (outcome) {
                is ScanToCardResult.NewCard -> {
                    pendingPhoto = null // the card owns it now
                    ScanUiState.NewCard(outcome.plantId, outcome.bonusDrops)
                }
                is ScanToCardResult.AlreadyInGarden -> {
                    discardPendingPhoto()
                    ScanUiState.AlreadyInGarden(outcome.plantId, outcome.displayName)
                }
                is ScanToCardResult.UnknownSpecies -> {
                    discardPendingPhoto()
                    ScanUiState.Failed(R.string.scan_error_unknown_species)
                }
            }
            placeLookup = null
        }
    }

    /** Acknowledges an error: back to the guesses when possible, else the live camera. */
    fun dismissError() {
        val s = _state.value as? ScanUiState.Failed ?: return
        val back = s.backTo
        if (back != null && pendingPhoto != null) _state.value = back else reset()
    }

    /** Back to the live camera (retake, sheet dismissed, error acknowledged, card closed). */
    fun reset() {
        work?.cancel()
        placeLookup?.cancel()
        placeLookup = null
        discardPendingPhoto() // waits for the cancelled work to settle before deciding
        work = null
        _state.value = ScanUiState.Live
    }

    private fun discardPendingPhoto() {
        val file = pendingPhoto ?: return
        pendingPhoto = null
        deleteUnlessOwned(file, after = work)
    }

    /**
     * Deletes [file] unless an owned plant points at it. Runs outside [viewModelScope] (so it survives
     * [onCleared]) and first waits for [after]: a cancelled confirm may still be finishing its NonCancellable
     * planting, and the check must see that row.
     */
    private fun deleteUnlessOwned(file: File, after: Job?) {
        cleanupScope.launch {
            try {
                after?.join()
                if (!garden.isPhotoReferenced(file.absolutePath)) file.delete()
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                Log.w(TAG, "Photo cleanup skipped", e) // keeping a stray file beats deleting a card's photo
            }
        }
    }

    override fun onCleared() {
        pendingPhoto?.let { f -> deleteUnlessOwned(f, after = work) }
        pendingPhoto = null
        captureExecutor.shutdown()
        super.onCleared()
    }

    private companion object {
        const val TAG = "ScanViewModel"
        /** Outlives any one ViewModel so photo cleanup can finish after [onCleared]. */
        val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        /** How long confirm waits for the found-at location before planting without it. */
        const val PLACE_WAIT_MS = 2_500L
    }
}
