package com.jeifolders.gui;

import com.jeifolders.data.Folder;
import com.jeifolders.integration.JEIBookmarkManager;
import com.jeifolders.integration.JEIIngredientManager;
import com.jeifolders.util.ModLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A specialized bookmark list for folders that integrates with JEI
 */
public class FolderBookmarkList {
    private Folder folder;
    private final List<Object> bookmarks = new ArrayList<>();
    private final List<Object> listeners = new ArrayList<>();

    public void setFolder(Folder folder) {
        this.folder = folder;
        // When folder changes, we should update our bookmarks
        refreshBookmarks();
    }

    /**
     * Refreshes the bookmarks based on the current folder
     */
    private void refreshBookmarks() {
        bookmarks.clear();
        
        if (folder != null) {
            List<String> keys = folder.getBookmarkKeys();
            if (!keys.isEmpty()) {
                ModLogger.info("Refreshing bookmarks for folder {} with {} keys", folder.getName(), keys.size());
                
                // Use JEIIngredientManager instead of JEIIntegration
                List<Object> ingredients = JEIIngredientManager.getIngredientsForBookmarkKeys(keys);
                bookmarks.addAll(ingredients);
            } else {
                ModLogger.info("Folder {} has no bookmarks", folder.getName());
            }
        }
        
        // Notify listeners of the change
        notifyListenersOfChange();
    }


    /**
     * Add a listener that will be notified when the source list changes
     */
    public void addSourceListChangedListener(Object listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Notify listeners that the bookmark list has changed
     */
    private void notifyListenersOfChange() {
        JEIBookmarkManager.notifySourceListChanged(listeners);
    }

    /**
     * Gets all bookmarks in this list
     */
    public List<Object> getAllBookmarks() {
        return Collections.unmodifiableList(bookmarks);
    }

    /**
     * Adds a bookmark directly
     */
    public void addBookmark(Object ingredient) {
        if (!bookmarks.contains(ingredient)) {
            bookmarks.add(ingredient);
            
            // Also add to folder if we have one
            if (folder != null) {
                String key = JEIIngredientManager.getKeyForIngredient(ingredient);
                if (key != null && !key.isEmpty() && !folder.containsBookmark(key)) {
                    folder.addBookmarkKey(key);
                }
            }
            
            // Notify listeners of the change
            notifyListenersOfChange();
        }
    }

    /**
     * Clears all bookmarks
     */
    public void clearBookmarks() {
        if (!bookmarks.isEmpty()) {
            bookmarks.clear();
            notifyListenersOfChange();
        }
    }

}
