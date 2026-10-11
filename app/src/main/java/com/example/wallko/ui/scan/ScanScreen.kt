package com.example.wallko.ui.scan

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.wallko.R
import com.example.wallko.core.db.Rarity
import com.example.wallko.ui.card.PlantCardSheet
import com.example.wallko.ui.components.DripExpression
import com.example.wallko.ui.components.DripMascot
import com.example.wallko.ui.components.EmptyState
import com.example.wallko.ui.components.LeafConfidence
import com.example.wallko.ui.components.RarityRibbon
import com.example.wallko.ui.components.ToyButton
import com.example.wallko.ui.components.confidenceLeaves
import com.example.wallko.ui.theme.WallkoColors
import com.example.wallko.ui.theme.WallkoShapes
import java.io.File

/** True when the system "Remove animations" / animator duration scale 0 is set. Read once per composition entry. */
@Composable
private fun rememberScanReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Where the camera-permission conversation is. */
private enum class CameraAsk { EXPLAIN, DENIED, DENIED_FOREVER }

private const val CAMERA = Manifest.permission.CAMERA

private fun hasCamera(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, CAMERA) == PackageManager.PERMISSION_GRANTED

/** Scan tab: camera permission → live viewfinder → top-3 guesses → new card (or "already in your garden"). */
@Composable
fun ScanScreen(
    modifier: Modifier = Modifier,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var granted by remember { mutableStateOf(hasCamera(context)) }
    var ask by rememberSaveable { mutableStateOf(CameraAsk.EXPLAIN) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) {
            val canAskAgain = activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, CAMERA) } ?: true
            ask = if (canAskAgain) CameraAsk.DENIED else CameraAsk.DENIED_FOREVER
        }
    }
    // Catches a grant made in system Settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = hasCamera(context) }

    if (granted) {
        ScanCamera(viewModel, modifier)
    } else {
        CameraPermissionAsk(
            ask = ask,
            onAllow = { launcher.launch(CAMERA) },
            onOpenSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    // Nothing sensible to do; the bubble already explains where the switch is.
                }
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun CameraPermissionAsk(
    ask: CameraAsk,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (ask) {
            CameraAsk.EXPLAIN -> EmptyState(
                message = stringResource(R.string.scan_permission_bubble),
                expression = DripExpression.CURIOUS,
                actionLabel = stringResource(R.string.scan_permission_allow),
                onAction = onAllow,
            )
            CameraAsk.DENIED -> EmptyState(
                message = stringResource(R.string.scan_permission_denied_bubble),
                expression = DripExpression.WORRIED,
                actionLabel = stringResource(R.string.scan_permission_try_again),
                onAction = onAllow,
            )
            CameraAsk.DENIED_FOREVER -> EmptyState(
                message = stringResource(R.string.scan_permission_forever_bubble),
                expression = DripExpression.SLEEPY,
                actionLabel = stringResource(R.string.scan_permission_open_settings),
                onAction = onOpenSettings,
            )
        }
    }
}

@Composable
private fun ScanCamera(viewModel: ScanViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    val cameraError by viewModel.cameraError.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val reducedMotion = rememberScanReducedMotion()

    LaunchedEffect(lifecycleOwner) { viewModel.bindToCamera(lifecycleOwner) }

    val frozenPhoto: String? = when (val s = state) {
        is ScanUiState.Busy -> s.photoPath
        is ScanUiState.Guesses -> s.result.photoPath
        else -> null
    }

    Box(modifier.fillMaxSize().background(WallkoColors.Ink)) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(12.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.Black),
        ) {
            val request = surfaceRequest
            val viewfinderDescription = stringResource(R.string.scan_viewfinder_description)
            if (request != null) {
                CameraXViewfinder(
                    surfaceRequest = request,
                    modifier = Modifier.fillMaxSize().semantics { contentDescription = viewfinderDescription },
                )
            } else if (!cameraError) {
                Text(
                    stringResource(R.string.scan_starting_camera),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (frozenPhoto != null) {
                AsyncImage(
                    model = File(frozenPhoto),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            ViewfinderOverlay(sweeping = state == ScanUiState.Live && !reducedMotion, modifier = Modifier.fillMaxSize())
        }

        if (cameraError) {
            EmptyState(
                message = stringResource(R.string.scan_error_camera),
                expression = DripExpression.WORRIED,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (state == ScanUiState.Live && !cameraError) {
            HintBubble(
                stringResource(R.string.scan_hint),
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 32.dp, start = 24.dp, end = 24.dp),
            )
            CaptureButton(
                enabled = surfaceRequest != null,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    viewModel.capture()
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
            )
        }

        (state as? ScanUiState.Busy)?.let { BusyBubble(it.stage, Modifier.align(Alignment.Center)) }
    }

    when (val s = state) {
        is ScanUiState.Guesses -> GuessSheet(
            result = s.result,
            onSelect = viewModel::select,
            onConfirm = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                viewModel.confirm()
            },
            onRetake = viewModel::reset,
        )
        is ScanUiState.NewCard -> PlantCardSheet(
            plantId = s.plantId,
            onDismiss = viewModel::reset,
            isNewCard = true,
            bonusDrops = s.bonusDrops,
        )
        is ScanUiState.AlreadyInGarden -> PlantCardSheet(
            plantId = s.plantId,
            onDismiss = viewModel::reset,
            message = stringResource(R.string.scan_already_in_garden, s.name),
        )
        is ScanUiState.Failed -> AlertDialog(
            onDismissRequest = viewModel::dismissError,
            icon = { DripMascot(DripExpression.WORRIED, size = 72.dp) },
            text = {
                Text(stringResource(s.message), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.scan_ok)) }
            },
            shape = WallkoShapes.Card,
        )
        else -> Unit
    }
}

/** Soft rounded corner brackets plus a slow shimmer band sweeping top to bottom. */
@Composable
private fun ViewfinderOverlay(sweeping: Boolean, modifier: Modifier = Modifier) {
    val sweep = if (sweeping) {
        rememberInfiniteTransition(label = "scanShimmer").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2_800, easing = LinearEasing)),
            label = "scanSweep",
        )
    } else null

    Canvas(modifier) {
        val inset = 28.dp.toPx()
        val arm = 44.dp.toPx()
        val radius = 24.dp.toPx()
        val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
        val left = inset
        val top = inset
        val right = size.width - inset
        val bottom = size.height - inset
        val bracket = Color.White.copy(alpha = 0.9f)

        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            // An L with a rounded elbow at (x, y); dx/dy = ±1 point the arms inward.
            val path = Path().apply {
                moveTo(x, y + dy * arm)
                lineTo(x, y + dy * radius)
                quadraticTo(x, y, x + dx * radius, y)
                lineTo(x + dx * arm, y)
            }
            drawPath(path, bracket, style = stroke)
        }
        corner(left, top, 1f, 1f)
        corner(right, top, -1f, 1f)
        corner(left, bottom, 1f, -1f)
        corner(right, bottom, -1f, -1f)

        val p = sweep?.value ?: return@Canvas
        val band = size.height * 0.22f
        val y = -band + p * (size.height + band)
        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.5f to Color.White.copy(alpha = 0.28f),
                1f to Color.Transparent,
                startY = y,
                endY = y + band,
            ),
            topLeft = Offset(left, y.coerceAtLeast(top)),
            size = Size(right - left, (y + band).coerceAtMost(bottom) - y.coerceAtLeast(top)).let {
                Size(it.width, it.height.coerceAtLeast(0f))
            },
            cornerRadius = CornerRadius(radius, radius),
        )
    }
}

@Composable
private fun HintBubble(text: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = WallkoShapes.Bubble, color = WallkoColors.Cream.copy(alpha = 0.92f), shadowElevation = 2.dp) {
        Text(
            text,
            color = WallkoColors.Ink,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun CaptureButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.scan_capture)
    val waiting = stringResource(R.string.scan_capture_disabled)
    Box(
        modifier
            .size(84.dp)
            .clip(CircleShape)
            .border(6.dp, Color.White, CircleShape)
            .padding(10.dp)
            .clip(CircleShape)
            .background(if (enabled) WallkoColors.Grass else WallkoColors.Grass.copy(alpha = 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics {
                contentDescription = description
                if (!enabled) stateDescription = waiting
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = WallkoColors.Ink, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun BusyBubble(stage: BusyStage, modifier: Modifier = Modifier) {
    Surface(
        modifier.padding(horizontal = 24.dp).semantics { liveRegion = LiveRegionMode.Polite },
        shape = WallkoShapes.Card,
        color = WallkoColors.Cream,
        contentColor = WallkoColors.Ink,
        shadowElevation = 6.dp,
    ) {
        Column(
            Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DripMascot(DripExpression.CURIOUS, size = 72.dp)
            Text(
                stringResource(stage.message),
                color = WallkoColors.Ink,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            CircularProgressIndicator(color = WallkoColors.Grass, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuessSheet(
    result: ScanResultUi,
    onSelect: (Int) -> Unit,
    onConfirm: () -> Unit,
    onRetake: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onRetake,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = WallkoShapes.Sheet,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AsyncImage(
                    model = File(result.photoPath),
                    contentDescription = stringResource(R.string.scan_result_photo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)),
                )
                Text(
                    stringResource(
                        when {
                            result.guesses.isEmpty() -> R.string.scan_result_title_none
                            result.confident -> R.string.scan_result_title_confident
                            else -> R.string.scan_result_title_unsure
                        },
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            if (!result.confident || result.guesses.isEmpty()) UnsureTips()

            if (result.guesses.isEmpty()) {
                Text(stringResource(R.string.scan_no_guesses), style = MaterialTheme.typography.titleMedium)
            } else {
                if (!result.confident) {
                    Text(
                        stringResource(R.string.scan_result_pick),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                result.guesses.forEachIndexed { i, guess ->
                    GuessRow(guess, selected = result.selected == i, onClick = { onSelect(i) })
                }
                ToyButton(
                    text = stringResource(R.string.scan_result_confirm),
                    onClick = onConfirm,
                    enabled = result.selected != null,
                    icon = Icons.Rounded.CheckCircle,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ToyButton(
                text = stringResource(R.string.scan_result_retake),
                onClick = onRetake,
                icon = Icons.Rounded.Refresh,
                containerColor = WallkoColors.Sky,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun UnsureTips() {
    // Opaque pastel + fixed Ink text so it reads the same in light and dark theme.
    Surface(shape = WallkoShapes.Card, color = WallkoColors.PeachSoft, contentColor = WallkoColors.Ink, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            DripMascot(DripExpression.WORRIED, size = 56.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.scan_result_unsure), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                listOf(R.string.scan_tip_closer, R.string.scan_tip_leaves, R.string.scan_tip_light).forEach {
                    Text(stringResource(R.string.scan_tip_bullet, stringResource(it)), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun GuessRow(guess: GuessUi, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) WallkoColors.GrassDeep else MaterialTheme.colorScheme.outlineVariant
    val description = listOfNotNull(
        guess.title,
        guess.subtitle,
        stringResource(R.string.scan_guess_confidence, confidenceLeaves(guess.score)),
        guess.rarity?.let { stringResource(R.string.scan_guess_rarity, stringResource(it.labelRes())) },
        if (guess.inGarden) stringResource(R.string.scan_in_garden_chip) else null,
    ).joinToString(", ")
    Surface(
        shape = WallkoShapes.Card,
        color = if (selected) WallkoColors.Grass.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clip(WallkoShapes.Card)
            .border(if (selected) 3.dp else 1.dp, borderColor, WallkoShapes.Card)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            // One clean announcement, e.g. "Rose, Rosa rubiginosa, 4 of 5 leaves confidence, Rare rarity".
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(guess.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                guess.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    guess.rarity?.let { RarityRibbon(it, compact = true) }
                    if (guess.inGarden) {
                        Surface(shape = CircleShape, color = WallkoColors.Sky.copy(alpha = 0.4f)) {
                            Text(
                                stringResource(R.string.scan_in_garden_chip),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
            LeafConfidence(score = guess.score, showWord = true)
        }
    }
}

private fun Rarity.labelRes(): Int = when (this) {
    Rarity.COMMON -> R.string.scan_rarity_common
    Rarity.UNCOMMON -> R.string.scan_rarity_uncommon
    Rarity.RARE -> R.string.scan_rarity_rare
    Rarity.EPIC -> R.string.scan_rarity_epic
    Rarity.LEGENDARY -> R.string.scan_rarity_legendary
}
