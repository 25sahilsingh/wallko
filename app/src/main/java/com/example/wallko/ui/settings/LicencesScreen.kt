package com.example.wallko.ui.settings

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.example.wallko.R
import com.example.wallko.ui.theme.WallkoColors
import com.example.wallko.ui.theme.WallkoShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "About & licences": app version, a short blurb and every credited component with its full
 * licence text (from assets/licences/) in expandable rows. Shown inside SettingsScreen, but
 * self-contained so it can also be given its own navigation route.
 */
@Composable
fun LicencesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = remember(context) { appVersionName(context) }
    var expanded by rememberSaveable { mutableStateOf(setOf<Int>()) }
    val haptics = LocalHapticFeedback.current

    LazyColumn(modifier.fillMaxSize()) {
        item {
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
                    stringResource(R.string.licences_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }

        item {
            Surface(
                shape = WallkoShapes.Card,
                color = WallkoColors.Grass.copy(alpha = 0.18f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.licences_app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = WallkoColors.Berry,
                            modifier = Modifier.size(MaterialTheme.typography.titleLarge.iconSize()),
                        )
                    }
                    if (version.isNotEmpty()) {
                        Text(
                            stringResource(R.string.licences_version, version),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.licences_made_with_love), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item {
            Text(
                stringResource(R.string.licences_thanks),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            )
        }

        itemsIndexed(LicencesCatalog.entries, key = { i, _ -> i }) { i, entry ->
            val isOpen = i in expanded
            LicenceRow(
                entry = entry,
                expanded = isOpen,
                onToggle = {
                    haptics.performHapticFeedback(if (isOpen) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                    expanded = if (isOpen) expanded - i else expanded + i
                },
            )
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun LicenceRow(entry: LicenceEntry, expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    val stateText = stringResource(if (expanded) R.string.licences_expanded else R.string.licences_collapsed)
    val actionText = stringResource(if (expanded) R.string.licences_collapse_cd else R.string.licences_expand_cd)
    Surface(
        shape = WallkoShapes.Card,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(WallkoShapes.Card)
                    .clickable(role = Role.Button, onClick = onToggle)
                    .semantics(mergeDescendants = true) {
                        stateDescription = stateText
                        onClick(label = actionText) { onToggle(); true }
                    }
                    .heightIn(min = 56.dp)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(entry.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                    Text(entry.detail, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        entry.licence,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier
                        .size(MaterialTheme.typography.titleMedium.iconSize())
                        .rotate(rotation),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        entry.copyright,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    LicenceBody(LicencesCatalog.assetPath(entry))
                }
            }
        }
    }
}

@Composable
private fun LicenceBody(assetPath: String) {
    val context = LocalContext.current
    val loading = stringResource(R.string.licences_loading)
    val missing = stringResource(R.string.licences_missing)
    val text by produceState<String?>(initialValue = null, assetPath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { LicenceText.reflow(it.readText()) }
            }.getOrNull()
        } ?: missing
    }
    SelectionContainer {
        Text(
            text ?: loading,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
        )
    }
}

private fun appVersionName(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
} catch (e: Exception) {
    ""
}
