package org.prebid.mobile.admob;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.ads.mediation.Adapter;
import com.google.android.gms.ads.mediation.InitializationCompleteCallback;
import com.google.android.gms.ads.mediation.MediationAdConfiguration;
import com.google.android.gms.ads.mediation.MediationConfiguration;
import com.google.android.gms.ads.VersionInfo;

import com.life360.ads.Life360Ads;

import org.prebid.mobile.LogUtil;
import org.prebid.mobile.ParametersMatcher;
import org.prebid.mobile.PrebidMobile;
import org.prebid.mobile.rendering.bidding.display.BidResponseCache;

import java.util.HashMap;
import java.util.List;

/**
 * Base Prebid adapter for all ad types.
 */
public abstract class PrebidBaseAdapter extends Adapter {

    private final VersionInfo sdkVersionInfo = parseSdkVersionInfo();
    protected static final String TAG = "PrebidAdapter";

    @Override
    public void initialize(
            @NonNull Context context,
            @NonNull InitializationCompleteCallback callback,
            @NonNull List<MediationConfiguration> list
    ) {
        if (PrebidMobile.isSdkInitialized()) {
            callback.onInitializationSucceeded();
        } else {
            callback.onInitializationFailed("Please initialize Prebid Mobile SDK");
        }
    }

    @NonNull
    @Override
    public VersionInfo getVersionInfo() {
        return sdkVersionInfo;
    }

    @NonNull
    @Override
    public VersionInfo getSDKVersionInfo() {
        return sdkVersionInfo;
    }

    @Nullable
    protected String getResponseIdAndCheckParameters(
            @NonNull MediationAdConfiguration configuration,
            @NonNull String extraResponseIdKey,
            @NonNull OnLoadFailure onLoadFailure
    ) {
        Bundle serverParameters = configuration.getServerParameters();
        String adMobParameters = serverParameters.getString(MediationConfiguration.CUSTOM_EVENT_SERVER_PARAMETER_FIELD);

        String responseId = configuration.getMediationExtras().getString(extraResponseIdKey);
        if (responseId == null) {
            onLoadFailure.run(AdErrors.emptyResponseId());
            return null;
        }

        HashMap<String, String> prebidParameters = BidResponseCache.getInstance().getKeywords(responseId);
        if (prebidParameters == null) {
            onLoadFailure.run(AdErrors.emptyPrebidKeywords());
            return null;
        }

        if (!ParametersMatcher.doParametersMatch(adMobParameters, prebidParameters)) {
            onLoadFailure.run(AdErrors.notMatchedParameters());
            return null;
        }
        LogUtil.verbose(TAG, "Parameters are matched! (" + serverParameters + ")");

        return responseId;
    }

    private VersionInfo parseSdkVersionInfo() {
        int[] versions = new int[]{0, 0, 0};
        try {
            // Report the wrapped Life360 Ads SDK product version to GMA, not the Prebid version.
            String[] versionStrings = Life360Ads.version.split("\\.");
            if (versionStrings.length >= 3) {
                for (int i = 0; i < 3; i++) {
                    versions[i] = Integer.parseInt(versionStrings[i]);
                }
            }
        } catch (NumberFormatException ignore) {
        }
        return new VersionInfo(versions[0], versions[1], versions[2]);
    }

}
