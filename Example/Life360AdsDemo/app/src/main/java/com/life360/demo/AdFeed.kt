package com.life360.demo

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.life360.ads.api.rendering.BannerView
import com.life360.ui.theme.Life360AdsDemoTheme

// Muted grey rather than a theme colour, since the page is pinned to white in either theme.
private val PLACEHOLDER_TEXT_COLOR = Color(0xFF666666)

/**
 * A blank white page carrying one ad slot, centred at rest with empty runway above and below it.
 *
 * Deliberately a `verticalScroll` column rather than a `LazyColumn`: a lazy list disposes items once
 * they leave the viewport, which would detach the [BannerView] and end its Open Measurement session.
 * That turns every scroll into a teardown-and-rebuild and measures view recycling instead of
 * viewability. Keeping the slot composed means the ad view stays attached and only its on-screen
 * exposure changes — which is what the OM SDK and the SDK's visibility trackers actually observe.
 */
@Composable
fun AdFeed(
    controller: AdSlotController,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        val slotHeight = controller.format.slotHeight

        // The leading runway is exactly the gap that centres the slot at scroll 0, so the ad is
        // already on screen when the tab opens. The trailing runway is a full viewport taller than
        // the slot, which is what makes it possible to scroll the ad clear of the top edge and back
        // — the transition the viewability trackers are here to report.
        val leadingRunway = ((maxHeight - slotHeight) / 2).coerceAtLeast(0.dp)
        val trailingRunway = maxHeight + slotHeight

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            Spacer(Modifier.height(leadingRunway))
            AdSlot(controller)
            Spacer(Modifier.height(trailingRunway))
        }
    }
}

@Composable
private fun AdSlot(controller: AdSlotController) {
    val slotHeight = controller.format.slotHeight
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(slotHeight),
        contentAlignment = Alignment.Center,
    ) {
        // Drawn under the ad view so it shows through an empty slot and is covered by a creative.
        SlotPlaceholder(controller.state)
        AdViewHost(
            bannerView = controller.bannerView,
            slotHeight = slotHeight,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun SlotPlaceholder(state: AdSlotState) {
    val message = when (state) {
        AdSlotState.Idle -> "Waiting for the ads SDK…"
        AdSlotState.Loading -> "Requesting ad…"
        is AdSlotState.Failed -> "No ad — ${state.message ?: "unknown error"}"
        is AdSlotState.Loaded -> return
    }
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = PLACEHOLDER_TEXT_COLOR,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

/**
 * Hosts the slot's long-lived [BannerView] inside a stable container.
 *
 * `AndroidView`'s factory runs again for each new composition, so returning the banner from it would
 * hand Compose a view that still belongs to the previous tab's holder. Creating a throwaway
 * container instead and re-parenting the banner into it keeps a single ad view — and so a single OM
 * session — across tab switches.
 *
 * The banner is nested one level deeper than it needs to be because Nativo's renderer picks its
 * height strategy from `bannerView.parent.layoutParams.height`: an indefinite height makes it fall
 * back to the bid's minimum and clip anything taller. Compose owns the layout params of the view its
 * factory returns, so the intermediate slot view — whose height is set here in pixels — is what
 * gives the renderer a definite height to expand into.
 */
@Composable
private fun AdViewHost(
    bannerView: BannerView?,
    slotHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val slotHeightPx = with(LocalDensity.current) { slotHeight.roundToPx() }
    AndroidView(
        factory = { context ->
            FrameLayout(context).apply {
                addView(
                    FrameLayout(context).also { it.id = SLOT_VIEW_ID },
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, slotHeightPx),
                )
            }
        },
        update = { container ->
            val slotView = container.findViewById<FrameLayout>(SLOT_VIEW_ID)
            when {
                bannerView == null -> slotView.removeAllViews()
                bannerView.parent !== slotView -> {
                    (bannerView.parent as? ViewGroup)?.removeView(bannerView)
                    slotView.removeAllViews()
                    slotView.addView(
                        bannerView,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        ),
                    )
                }
            }
        },
        modifier = modifier,
    )
}

private val SLOT_VIEW_ID = View.generateViewId()

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun SlotPlaceholderPreview() {
    Life360AdsDemoTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DemoAdFormat.BANNER.slotHeight)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            SlotPlaceholder(AdSlotState.Loading)
        }
    }
}
