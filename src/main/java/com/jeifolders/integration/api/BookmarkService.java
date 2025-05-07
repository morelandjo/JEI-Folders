// filepath: /Users/josh/IdeaProjects/JEI-Folders/JEI-Folders/src/main/java/com/jeifolders/integration/api/BookmarkService.java
package com.jeifolders.integration.api;

import mezz.jei.api.ingredients.ITypedIngredient;
import java.util.List;

/**
 * Service interface for managing bookmarks in folders.
 * Provides methods for adding, removing, and retrieving bookmarked ingredients.
 */
public interface BookmarkService {
    
    /**
     * Adds an ingredient as a bookmark to the specified folder.
     *
     * @param folderId The ID of the folder to add the bookmark to
     * @param ingredient The ingredient to bookmark
     */
    void addBookmarkToFolder(int folderId, Object ingredient);
    
    /**
     * Removes an ingredient bookmark from the specified folder.
     *
     * @param folderId The ID of the folder to remove the bookmark from
     * @param ingredient The ingredient to remove
     */
    void removeBookmarkFromFolder(int folderId, Object ingredient);
    
    /**
     * Gets all bookmarks for a folder as typed ingredients.
     *
     * @param folderId The ID of the folder to get bookmarks for
     * @return A list of typed ingredients in the folder
     */
    List<ITypedIngredient<?>> getBookmarksForFolder(int folderId);
    
    /**
     * Gets all bookmark keys for a folder.
     *
     * @param folderId The ID of the folder to get bookmark keys for
     * @return A list of bookmark keys
     */
    List<String> getBookmarkKeysForFolder(int folderId);
    
    /**
     * Checks if a folder contains a specific bookmark.
     *
     * @param folderId The ID of the folder to check
     * @param ingredient The ingredient to check for
     * @return true if the folder contains the bookmark
     */
    boolean folderContainsBookmark(int folderId, Object ingredient);
    
    /**
     * Notifies listeners that a bookmark list has changed.
     *
     * @param listeners The listeners to notify
     */
    void notifySourceListChanged(List<?> listeners);
}