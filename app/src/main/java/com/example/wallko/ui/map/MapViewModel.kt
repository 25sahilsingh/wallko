package com.example.wallko.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wallko.core.geo.cellBounds
import com.example.wallko.core.geo.cellIdOf
import com.example.wallko.core.geo.haversineMeters
import com.example.wallko.data.walks.WalkRepository
import com.example.wallko.walk.WalkLiveState
import com.example.wallko.walk.WalkPhase
import com.example.wallko.walk.WalkSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MapUiState(
    val fog: FogShapes = FogShapes.EMPTY,
    /** Current walk's path (oldest first), including the latest fix. Empty when not walking. */
    val path: List<LngLat> = emptyList(),
    /** Where Drip stands: the live fix, else the end of the most recent walk. */
    val position: LngLat? = null,
    /** Where to point the camera when there is no [position]: some explored cell. */
    val home: LngLat? = null,
    val walking: Boolean = false,
    /** False until the first database emission (avoids flashing the empty state). */
    val loaded: Boolean = false,
) {
    val exploredCount: Int get() = fog.totalCells
    val cameraTarget: LngLat? get() = position ?: home
    val isEmpty: Boolean get() = loaded && exploredCount == 0 && cameraTarget == null
}

/**
 * Fires once each time the live walk's projected elixir count goes up. A new walk (different start time)
 * only sets a new baseline, so opening the map mid-walk does not replay old elixir.
 */
class ElixirPopTracker {
    private var walkKey: Long? = null
    private var last = 0

    /** @return the new elixir count if it just increased, else null. */
    fun onLive(state: WalkLiveState?): Int? {
        if (state == null) {
            walkKey = null
            return null
        }
        if (state.startedAtMillis != walkKey) {
            walkKey = state.startedAtMillis
            last = state.elixirSoFar
            return null
        }
        val now = state.elixirSoFar
        val increased = now > last && state.phase == WalkPhase.TRACKING
        last = now
        return if (increased) now else null
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    private val session: WalkSession,
    private val walkRepository: WalkRepository,
) : ViewModel() {

    /** Camera centre used to pick fog holes when there are more than [MAX_FOG_CELLS] cells. */
    private val cameraFocus = MutableStateFlow<LngLat?>(null)

    private val _elixirPops = MutableSharedFlow<Int>(extraBufferCapacity = 4)

    /** New elixir total, emitted when an elixir is earned during the live walk. */
    val elixirPops: SharedFlow<Int> = _elixirPops.asSharedFlow()

    private val lastFix: Flow<LngLat?> =
        session.live.map { s -> s?.lastFix?.let { LngLat(it.lng, it.lat) } }.distinctUntilChanged()

    private val livePath: Flow<List<LngLat>> = session.live
        .map { it?.walkId }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(emptyList())
            } else {
                walkRepository.observeWalk(id).map { w -> w?.points?.map { LngLat(it.lng, it.lat) }.orEmpty() }
            }
        }

    private val path: Flow<List<LngLat>> = combine(livePath, lastFix) { points, fix ->
        if (fix != null && points.lastOrNull() != fix) points + fix else points
    }.distinctUntilChanged()

    /** End of the most recent walk, so Drip has somewhere to stand between walks. */
    private val lastWalkEnd: Flow<LngLat?> = walkRepository.observeWalks()
        .map { walks -> walks.firstOrNull()?.id }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(null)
            } else {
                walkRepository.observeWalk(id).map { w -> w?.points?.lastOrNull()?.let { LngLat(it.lng, it.lat) } }
            }
        }
        .distinctUntilChanged()

    private val position: Flow<LngLat?> = combine(lastFix, lastWalkEnd) { fix, end -> fix ?: end }

    private val storedCells: Flow<Set<Long>> = walkRepository.observeExploredCells()
        .map { rows -> rows.mapTo(HashSet(rows.size)) { it.cellId } }

    /** Cells entered on the live walk. They are only written to the database when the walk ends. */
    private val liveCells: Flow<Set<Long>> = path
        .map { points -> points.mapTo(HashSet()) { cellIdOf(it.lat, it.lng) } }
        .distinctUntilChanged()

    private data class CellsBundle(val all: Set<Long>, val home: LngLat?)

    private val cells: Flow<CellsBundle> = combine(storedCells, liveCells) { stored, live ->
        val all = if (live.isEmpty()) stored else stored + live
        val home = all.firstOrNull()?.let { id -> runCatching { cellBounds(id).center() }.getOrNull() }
        CellsBundle(all, home)
    }

    private val fog: Flow<Pair<FogShapes, LngLat?>> = combine(cells, cameraFocus, position) { bundle, focus, pos ->
        val selected = selectFogCells(bundle.all, focus ?: pos ?: bundle.home)
        FogShapes(mergeCellRuns(selected), bundle.all.size) to bundle.home
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<MapUiState> = combine(fog, path, position, session.live) { (shapes, home), p, pos, live ->
        MapUiState(
            fog = shapes,
            path = p,
            position = pos,
            home = home,
            walking = live != null,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    init {
        val tracker = ElixirPopTracker()
        viewModelScope.launch {
            session.live.collect { state -> tracker.onLive(state)?.let { _elixirPops.tryEmit(it) } }
        }
    }

    /** Camera settled. Only matters for very large maps, so it is throttled to moves of a few km. */
    fun onCameraIdle(lat: Double, lng: Double) {
        val current = cameraFocus.value
        if (current == null || haversineMeters(current.lat, current.lng, lat, lng) > FOCUS_STEP_METERS) {
            cameraFocus.value = LngLat(lng, lat)
        }
    }

    private companion object {
        const val FOCUS_STEP_METERS = 5_000.0
    }
}
