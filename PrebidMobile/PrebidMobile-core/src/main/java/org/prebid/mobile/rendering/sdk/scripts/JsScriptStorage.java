package org.prebid.mobile.rendering.sdk.scripts;

import java.io.File;

/**
 * Not used at runtime: we load the bundled on-device mraid.js asset instead (see JSLibraryManager).
 * Kept so runtime downloading can be restored if we ever need it.
 */
public interface JsScriptStorage {

    public File getInnerFile(String path);

    public boolean isFileAlreadyDownloaded(File file, String preferencesKey);

    public void createParentFolders(File file);

    public void markFileAsDownloadedCompletely(String path);

    public void fileDownloadingFailed(String path);

}
