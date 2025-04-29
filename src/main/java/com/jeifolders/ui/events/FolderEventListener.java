package com.jeifolders.ui.events;

import java.util.EventListener;

/**
 * Standard interface for folder event listeners.
 * Follows the Java EventListener pattern.
 */
public interface FolderEventListener extends EventListener {
    /**
     * Called when a folder event occurs.
     * 
     * @param event the event that occurred
     */
    void onFolderEvent(FolderEvent event);
}