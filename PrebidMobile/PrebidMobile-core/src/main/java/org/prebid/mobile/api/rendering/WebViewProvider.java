package org.prebid.mobile.api.rendering;

import androidx.annotation.Nullable;
import org.prebid.mobile.rendering.views.webview.WebViewBase;

/**
 * Hands out the rendered WebView by reference so callers do not have to walk the view hierarchy
 * looking for it.
 */
public interface WebViewProvider {

    /**
     * @return the WebView the current creative loaded into, available before it is added to the ad
     * view, or null until the creative resolves and for creatives that are not HTML.
     */
    @Nullable
    WebViewBase getRenderedWebView();

}
