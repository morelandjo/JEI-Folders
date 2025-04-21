package com.jeifolders.integration;

/**
 * A listener interface for bookmark changes.
 * Used to notify components when the bookmark list changes.
 */
public interface BookmarkListener {
    /**
     * Called when the bookmark list has been modified in some way.
     */
    void onBookmarkListChanged();
}