package com.life360.demo

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.util.Locale

// A fixed window of callbacks, so the panel's height doesn't shift as events arrive.
private const val VISIBLE_EVENTS = 5

// Material3 1.0.1 (kept in step with Kotlin 1.8.0's Compose compiler) predates the tonal
// surfaceContainer* roles, so surfaceVariant stands in for "a shade off background."
private val DEMO_AD_FORMATS = DemoAdFormat.values().toList()

/**
 * Three ad formats behind a bottom tab bar, each in its own scrolling feed.
 *
 * The status panel is pinned outside the scroll container on purpose: the callbacks worth watching
 * fire *while* the slot is moving through the viewport, so they have to stay readable when the ad
 * itself is off screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdDemoApp(
    controllers: Map<DemoAdFormat, AdSlotController>,
    sdkReady: Boolean,
) {
    var selected by remember { mutableStateOf(DemoAdFormat.BANNER) }

    // Hoisted above the tab switch so a slot left scrolled off screen is still there on return.
    val scrollStates = remember { DEMO_AD_FORMATS.associateWith { ScrollState(initial = 0) } }
    val controller = controllers.getValue(selected)

    // Requested on first visit rather than all three up front, so each slot's bid request and OM
    // session start from a moment you can point at.
    LaunchedEffect(selected, sdkReady) {
        if (sdkReady) controller.load()
    }

    Scaffold(
        topBar = { SlotStatusPanel(controller, sdkReady) },
        bottomBar = { FormatTabBar(selected, onSelect = { selected = it }) },
    ) { innerPadding ->
        AdFeed(
            controller = controller,
            scrollState = scrollStates.getValue(selected),
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun SlotStatusPanel(controller: AdSlotController, sdkReady: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${controller.format.title} ad",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = controller::reload, enabled = sdkReady) {
                    Text("Reload")
                }
            }
            Text(
                text = describeStatus(controller.state, sdkReady),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EventLogLines(controller.events)
        }
    }
}

private fun describeStatus(state: AdSlotState, sdkReady: Boolean): String = when {
    !sdkReady -> "Initializing ads SDK…"
    state is AdSlotState.Idle -> "Idle"
    state is AdSlotState.Loading -> "Requesting ad…"
    state is AdSlotState.Loaded -> listOfNotNull(
        "Loaded",
        state.demandSource,
        state.creativeSize,
    ).joinToString(" · ")
    state is AdSlotState.Failed -> "No ad · ${state.message ?: "unknown error"}"
    else -> "Idle"
}

@Composable
private fun EventLogLines(log: AdEventLog) {
    val visible = log.recent.takeLast(VISIBLE_EVENTS)
    Column(modifier = Modifier.heightIn(min = (VISIBLE_EVENTS * 15).dp)) {
        if (visible.isEmpty()) {
            EventLine("(no SDK callbacks yet)")
            return@Column
        }
        visible.forEach { entry ->
            EventLine(
                String.format(
                    Locale.US,
                    "+%5.2fs  %s%s",
                    entry.elapsedMs / 1000f,
                    entry.name,
                    entry.detail?.let { " — $it" } ?: "",
                )
            )
        }
    }
}

@Composable
private fun EventLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FormatTabBar(selected: DemoAdFormat, onSelect: (DemoAdFormat) -> Unit) {
    NavigationBar {
        DEMO_AD_FORMATS.forEach { format ->
            NavigationBarItem(
                selected = format == selected,
                onClick = { onSelect(format) },
                icon = { Text(format.glyph, style = MaterialTheme.typography.titleMedium) },
                label = { Text(format.title) },
            )
        }
    }
}
