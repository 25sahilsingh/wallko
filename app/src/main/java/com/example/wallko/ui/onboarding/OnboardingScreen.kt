package com.example.wallko.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wallko.R
import com.example.wallko.ui.theme.WallkoShapes
import kotlinx.coroutines.launch

/**
 * First-run flow: three swipeable story pages, then one permission card at a time.
 * Every denial is fine; we just move on. Calls [onFinished] once onboardingDone has been saved.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val finished by rememberUpdatedState(onFinished)

    LaunchedEffect(state.phase) {
        if (state.phase == OnboardingPhase.FINISHED) finished()
    }

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            AnimatedContent(
                targetState = state.phase,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "onboardingPhase",
            ) { phase ->
                when (phase) {
                    OnboardingPhase.STORY -> StoryPager(
                        preparing = state.preparing,
                        onDone = viewModel::startPermissions,
                    )
                    OnboardingPhase.PERMISSIONS -> PermissionFlow(state, viewModel)
                    OnboardingPhase.FINISHED -> Box(Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun StoryPager(preparing: Boolean, onDone: () -> Unit) {
    val pagerState = rememberPagerState { STORY_PAGE_COUNT }
    val scope = rememberCoroutineScope()
    val lastPage = pagerState.currentPage == STORY_PAGE_COUNT - 1

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .heightIn(min = 48.dp),
        ) {
            if (!lastPage) {
                TextButton(onClick = onDone, enabled = !preparing, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Text(stringResource(R.string.onboarding_skip), style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            StoryPage(page, pageOffset = pagerState.offsetOf(page))
        }

        PageDots(STORY_PAGE_COUNT, pagerState.currentPage, Modifier.padding(vertical = 16.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (preparing) {
                CircularProgressIndicator(Modifier.padding(8.dp))
            } else {
                BouncyToyButton(
                    text = stringResource(if (lastPage) R.string.onboarding_lets_go else R.string.onboarding_next),
                    bounceKey = lastPage,
                    onClick = {
                        if (lastPage) onDone() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                    modifier = Modifier.widthIn(min = 200.dp),
                )
            }
        }
    }
}

/** Signed distance of [page] from the settled position, in pages. */
private fun PagerState.offsetOf(page: Int): Float = (currentPage - page) + currentPageOffsetFraction

@Composable
private fun StoryPage(page: Int, pageOffset: Float) {
    val (title, body) = when (page) {
        0 -> R.string.onboarding_story1_title to R.string.onboarding_story1_body
        1 -> R.string.onboarding_story2_title to R.string.onboarding_story2_body
        else -> R.string.onboarding_story3_title to R.string.onboarding_story3_body
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StoryArt(page, pageOffset)
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionFlow(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    val context = LocalContext.current
    // The step a system dialog was launched for; survives the activity being recreated behind the dialog.
    var pending by rememberSaveable { mutableStateOf<PermissionStep?>(null) }

    val runtimeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        pending?.let(viewModel::next)
        pending = null
    }
    val healthContract = remember(viewModel) { viewModel.healthPermissionContract() }
    val healthLauncher = rememberLauncherForActivityResult(healthContract) {
        viewModel.next(PermissionStep.HEALTH_CONNECT)
        pending = null
    }

    val step = state.currentStep ?: return

    fun allow(step: PermissionStep) {
        when (step) {
            PermissionStep.HEALTH_CONNECT -> runCatching {
                pending = step
                healthLauncher.launch(viewModel.healthPermissions)
            }.onFailure { viewModel.next(step) }

            PermissionStep.HEALTH_CONNECT_INSTALL -> {
                runCatching { context.startActivity(viewModel.installHealthConnectIntent()) }
                viewModel.next(step)
            }

            else -> runCatching {
                pending = step
                runtimeLauncher.launch(OnboardingSteps.runtimePermissions(step).toTypedArray())
            }.onFailure {
                pending = null
                viewModel.next(step)
            }
        }
    }

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            (slideInHorizontally { it / 2 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 2 } + fadeOut())
        },
        label = "permissionStep",
    ) { shown ->
        PermissionCard(
            step = shown,
            position = state.steps.indexOf(shown) + 1,
            total = state.steps.size,
            onAllow = { allow(shown) },
            onNotNow = { viewModel.next(shown) },
        )
    }
}

@Composable
private fun PermissionCard(
    step: PermissionStep,
    position: Int,
    total: Int,
    onAllow: () -> Unit,
    onNotNow: () -> Unit,
) {
    val (title, body) = when (step) {
        PermissionStep.CAMERA -> R.string.onboarding_camera_title to R.string.onboarding_camera_body
        PermissionStep.ACTIVITY_RECOGNITION -> R.string.onboarding_activity_title to R.string.onboarding_activity_body
        PermissionStep.LOCATION -> R.string.onboarding_location_title to R.string.onboarding_location_body
        PermissionStep.NOTIFICATIONS -> R.string.onboarding_notifications_title to R.string.onboarding_notifications_body
        PermissionStep.HEALTH_CONNECT -> R.string.onboarding_health_title to R.string.onboarding_health_body
        PermissionStep.HEALTH_CONNECT_INSTALL -> R.string.onboarding_health_title to R.string.onboarding_health_install_body
    }
    val allowLabel = stringResource(
        if (step == PermissionStep.HEALTH_CONNECT_INSTALL) R.string.onboarding_get_health_connect else R.string.onboarding_allow,
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.onboarding_permission_progress, position, total),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Surface(
            shape = WallkoShapes.Card,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 4.dp,
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PermissionArt(step)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(body),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                BouncyToyButton(
                    text = allowLabel,
                    onClick = onAllow,
                    bounceKey = step,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onNotNow, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.onboarding_not_now), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}
