package org.prebid.mobile.rendering.sdk.scripts;

import org.prebid.mobile.rendering.loading.FileDownloadListener;

/**
 * Not used at runtime: we load the bundled on-device mraid.js asset instead (see JSLibraryManager).
 * Kept so runtime downloading can be restored if we ever need it.
 */
public interface DownloadListenerCreator {

    FileDownloadListener create(String filePath);

}