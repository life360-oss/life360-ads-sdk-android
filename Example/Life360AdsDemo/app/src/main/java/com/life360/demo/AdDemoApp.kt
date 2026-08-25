package com.life360.demo

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val DEMO_AD_FORMATS = AdConfiguration.values().toList()

// Hardcoded rather than theme-driven: the demo is pinned to a fixed light look regardless of the
// device's dynamic color scheme, to match the iOS counterpart's flat white bars and system-blue tint.
private val BAR_BACKGROUND = Color.White
private val TAB_BAR_TRACK = Color(0xFFEDEDED)
private val TAB_SELECTED_TINT = Color(0xFF3478F6)
private val TAB_UNSELECTED_TINT = Color(0xFF1C1C1E)

/**
 * Three ad formats behind a floating tab bar, each in its own scrolling feed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdDemoApp(
    controllers: Map<AdConfiguration, AdSlotController>,
    sdkReady: Boolean,
) {
    var selected by remember { mutableStateOf(AdConfiguration.BANNER) }

    // Hoisted above the tab switch so a slot left scrolled off screen is still there on return.
    val scrollStates = remember { DEMO_AD_FORMATS.associateWith { ScrollState(initial = 0) } }
    val controller = controllers.getValue(selected)

    // Requested on first visit rather than all three up front, so each slot's bid request and OM
    // session start from a moment you can point at.
    LaunchedEffect(selected, sdkReady) {
        if (sdkReady) controller.load()
    }

    Scaffold(
        topBar = { DemoTopBar(controller, sdkReady) },
        bottomBar = { FormatTabBar(selected, onSelect = { selected = it }) },
        containerColor = Color.White,
    ) { innerPadding ->
        AdFeed(
            controller = controller,
            scrollState = scrollStates.getValue(selected),
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoTopBar(controller: AdSlotController, sdkReady: Boolean) {
    TopAppBar(
        modifier = Modifier.shadow(elevation = 4.dp),
        title = {
            Text(
                text = controller.config.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        actions = {
            IconButton(onClick = controller::reload, enabled = sdkReady) {
                Icon(Icons.Filled.Refresh, contentDescription = "Reload ad")
            }
        },
        colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = BAR_BACKGROUND),
    )
}

/**
 * A floating, pill-shaped tab bar with a rounded highlight behind the selected item — the closest
 * Material3 approximation of the iOS tab bar's default "Liquid Glass" floating capsule style.
 */
@Composable
private fun FormatTabBar(selected: AdConfiguration, onSelect: (AdConfiguration) -> Unit) {
    Surface(
        color = TAB_BAR_TRACK,
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            DEMO_AD_FORMATS.forEach { format ->
                TabItem(
                    format = format,
                    isSelected = format == selected,
                    onClick = { onSelect(format) },
                )
            }
        }
    }
}

@Composable
private fun RowScope.TabItem(
    format: AdConfiguration,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (isSelected) TAB_SELECTED_TINT else TAB_UNSELECTED_TINT
    val background = if (isSelected) BAR_BACKGROUND else Color.Transparent
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(format.icon, contentDescription = format.title, tint = tint)
        Text(text = format.title, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}
