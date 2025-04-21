package com.jeifolders.integration;

import mezz.jei.api.ingredients.ITypedIngredient;
import java.util.List;

/**
 * Service interface for JEI bookmark functionality.
 * Centralizes bookmark-related operations that were previously spread across multiple classes.
 */
public interface BookmarkService {
    /**
     * Adds a bookmark ingredient to a folder
     */
    void addBookmarkToFolder(int folderId, Object ingredient);
    
    /**
     * Removes a bookmark ingredient from a folder
     */
    void removeBookmarkFromFolder(int folderId, Object ingredient);
    
    /**
     * Gets all bookmark ingredients for a folder
     */
    List<ITypedIngredient<?>> getBookmarksForFolder(int folderId);
    
    /**
     * Gets all bookmark keys for a folder
     */
    List<String> getBookmarkKeysForFolder(int folderId);
    
    /**
     * Checks if a folder contains a specific bookmark
     */
    boolean folderContainsBookmark(int folderId, Object ingredient);
    
    /**
     * Notifies listeners that a source list has changed
     */
    void notifySourceListChanged(List<?> listeners);
}