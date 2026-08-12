/*
 *    Copyright 2026 Life360, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.prebid.mobile.api.rendering.listeners;

import org.prebid.mobile.api.rendering.BannerView;

/**
 * Opt-in extension of {@link BannerViewListener}. A publisher implements this — rather than plain
 * BannerViewListener — to receive {@link #onLife360AdLoaded} and enable Life360's full-container
 * render path.
 */
public interface Life360BannerViewListener extends BannerViewListener {
    /**
     * Called instead of {@link BannerViewListener#onAdLoaded} when an ad is served and rendered by
     * Life360. The Life360 renderer attempts to break out of its fixed ad size and serve into the
     * full size of its container.
     *
     * @param bannerView view of the corresponding event.
     */
    void onLife360AdLoaded(BannerView bannerView);
}
