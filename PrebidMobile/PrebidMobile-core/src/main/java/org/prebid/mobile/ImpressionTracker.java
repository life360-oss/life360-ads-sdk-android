/*
 *    Copyright 2020-2021 Prebid.org, Inc.
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

package org.prebid.mobile;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.Nullable;

import org.prebid.mobile.http.HTTPGet;
import org.prebid.mobile.http.HTTPResponse;

/**
 * Fires one of a native ad's impression trackers, queueing the URL for a later attempt when the device is
 * offline rather than dropping it.
 * <p>
 * Deciding <em>when</em> to fire is not this class's job — that is the viewability tracker's, which knows
 * each tracker's declared event type and its threshold. This mirrors {@link ClickTracker}, whose shape it
 * shares for the same reason.
 */
class ImpressionTracker {

    private final String url;
    private final Context context;
    @Nullable
    private final ImpressionTrackerListener impressionTrackerListener;
    private boolean fired = false;

    static ImpressionTracker createAndFire(
            String url,
            Context context,
            @Nullable ImpressionTrackerListener impressionTrackerListener
    ) {
        ImpressionTracker impressionTracker = new ImpressionTracker(url, context, impressionTrackerListener);
        impressionTracker.fire();
        return impressionTracker;
    }

    private ImpressionTracker(
            String url,
            Context context,
            @Nullable ImpressionTrackerListener impressionTrackerListener
    ) {
        this.url = url;
        this.context = context.getApplicationContext();
        this.impressionTrackerListener = impressionTrackerListener;
    }

    private synchronized void fire() {
        if (fired) {
            return;
        }
        fired = true;

        SharedNetworkManager nm = SharedNetworkManager.getInstance(context);
        if (nm.isConnected(context)) {
            @SuppressLint("StaticFieldLeak") HTTPGet asyncTask = new HTTPGet() {
                @Override
                protected void onPostExecute(HTTPResponse response) {
                    notifyFired();
                }

                @Override
                protected String getUrl() {
                    return url;
                }
            };
            asyncTask.execute();
        } else {
            nm.addURL(url, context, this::notifyFired);
        }
    }

    private void notifyFired() {
        if (impressionTrackerListener != null) {
            impressionTrackerListener.onImpressionTrackerFired();
        }
    }

    String getUrl() {
        return url;
    }

}
