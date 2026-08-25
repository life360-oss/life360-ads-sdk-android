package org.prebid.mobile.rendering.models;

import android.content.Context;

import androidx.annotation.Nullable;

import org.prebid.mobile.rendering.models.internal.VisibilityTrackerResult;



public interface VisibilityTracker {
    interface VisibilityTrackerListener {
        void onVisibilityChanged(VisibilityTrackerResult result);
    }

    void setVisibilityTrackerListener(@Nullable VisibilityTrackerListener visibilityTracker );
    void startVisibilityCheck(Context context);
    void stopVisibilityCheck();
    void restartVisibilityCheck();
}

