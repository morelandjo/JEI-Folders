package com.jeifolders.gui;

import com.jeifolders.data.FolderDataRepresentation;
import com.jeifolders.integration.JEIIntegrationFactory;
import com.jeifolders.integration.BookmarkService;
import com.jeifolders.integration.IngredientService;
import com.jeifolders.util.ModLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A specialized bookmark list for folders that integrates with JEI
 */
public class FolderBookmarkList {
    private FolderDataRepresentation folder;
    private final List<Object> bookmarks = new ArrayList<>();
    private final List<Object> listeners = new ArrayList<>();
    
    // Access services
    private final BookmarkService bookmarkService = JEIIntegrationFactory.getBookmarkService();
    private final IngredientService ingredientService = JEIIntegrationFactory.getIngredientService();

    public void setFolder(FolderDataRepresentation folder) {
        this.folder = folder;
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
                
                // Use ingredient service to get ingredients for bookmark keys
                bookmarks.addAll(ingredientService.getIngredientsForKeys(keys));
            } else {
                ModLogger.info("Folder {} has no bookmarks", folder.getName());
            }
        }
        
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
    public void notifyListenersOfChange() {
        bookmarkService.notifySourceListChanged(listeners);
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
                String key = ingredientService.getKeyForIngredient(ingredient);
                if (key != null && !key.isEmpty() && !folder.containsBookmark(key)) {
                    folder.addBookmarkKey(key);
                }
            }
            
            // Notify listeners of the change
            notifyListenersOfChange();
        }
    }
    
    /**
     * Removes a bookmark directly
     */
    public void removeBookmark(Object ingredient) {
        if (bookmarks.remove(ingredient)) {
            // Also remove from folder if we have one
            if (folder != null) {
                String key = ingredientService.getKeyForIngredient(ingredient);
                if (key != null && !key.isEmpty()) {
                    folder.removeBookmark(key);
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
    
    /**
     * Gets all bookmarks and casts them to BookmarkIngredients
     */
    @SuppressWarnings("unchecked")
    public List<com.jeifolders.integration.BookmarkIngredient> getIngredients() {
        List<com.jeifolders.integration.BookmarkIngredient> result = new ArrayList<>();
        for (Object bookmark : bookmarks) {
            if (bookmark instanceof com.jeifolders.integration.BookmarkIngredient) {
                result.add((com.jeifolders.integration.BookmarkIngredient) bookmark);
            }
        }
        return result;
    }
    
    /**
     * Sets all ingredients in the bookmark list
     */
    public void setIngredients(List<com.jeifolders.integration.BookmarkIngredient> ingredients) {
        bookmarks.clear();
        bookmarks.addAll(ingredients);
        
        // Update folder bookmarks if we have a folder
        if (folder != null) {
            // Clear existing bookmarks
            folder.clearBookmarks();
            
            // Add new bookmarks
            for (Object ingredient : ingredients) {
                String key = ingredientService.getKeyForIngredient(ingredient);
                if (key != null && !key.isEmpty()) {
                    folder.addBookmarkKey(key);
                }
            }
        }
        
        // Notify listeners of the change
        notifyListenersOfChange();
    }
}
