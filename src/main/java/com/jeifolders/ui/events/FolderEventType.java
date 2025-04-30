package com.jeifolders.ui.events;

/**
 * Defines the types of folder events in the system.
 */
public enum FolderEventType {
    // Folder UI events
    FOLDER_CLICKED,
    FOLDER_ACTIVATED,
    FOLDER_DEACTIVATED,
    FOLDER_CREATED,
    FOLDER_DELETED,
    ADD_BUTTON_CLICKED,
    DELETE_BUTTON_CLICKED,
    BOOKMARK_CLICKED,
    INGREDIENT_DROPPED,
    
    // Bookmark operation events
    BOOKMARK_ADDED,
    BOOKMARK_REMOVED,
    BOOKMARKS_CLEARED,
    FOLDER_CONTENTS_CHANGED,
    DISPLAY_REFRESHED
}