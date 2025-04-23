package com.jeifolders.gui.folderButtons;

/**
 * Interface for listeners that need to be notified when folder contents change
 */
public interface FolderChangeListener {
    /**
     * Called when folder contents have changed
     * @param folderId The ID of the folder that changed
     */
    void onFolderContentsChanged(int folderId);
}