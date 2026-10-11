package com.example.wallko

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.wallko.core.prefs.SettingsStore
import com.example.wallko.ui.navigation.WallkoNavHost
import com.example.wallko.ui.onboarding.OnboardingScreen
import com.example.wallko.ui.theme.WallkoTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var appStartup: AppStartup
    @Inject lateinit var settingsStore: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { appStartup.onForeground() }
        }
        setContent {
            WallkoTheme {
                // null until DataStore answers, so neither screen flashes on launch.
                val done by remember { settingsStore.onboardingDone.map<Boolean, Boolean?> { it } }
                    .collectAsStateWithLifecycle(null)
                var openScan by rememberSaveable { mutableStateOf(false) }
                when (done) {
                    null -> Unit
                    false -> OnboardingScreen(onFinished = { openScan = true })
                    true -> WallkoNavHost(openScanFirst = openScan)
                }
            }
        }
    }
}
