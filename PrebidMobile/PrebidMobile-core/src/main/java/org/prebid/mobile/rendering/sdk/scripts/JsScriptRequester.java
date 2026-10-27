package org.prebid.mobile.rendering.sdk.scripts;

import java.io.File;

/**
 * Not used at runtime: we load the bundled on-device mraid.js asset instead (see JSLibraryManager).
 * Kept so runtime downloading can be restored if we ever need it.
 */
public interface JsScriptRequester {

    public void download(
            File saveToFile,
            JsScriptData data,
            DownloadListenerCreator downloadListener
    );

}
