package com.jeifolders.integration;

import com.jeifolders.data.FolderDataManager;
import com.jeifolders.data.FolderDataRepresentation;
import com.jeifolders.gui.FolderBookmarkContentsDisplay;
import com.jeifolders.util.ModLogger;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper utility for working with typed ingredients without direct JEI dependencies
 */
public class TypedIngredientHelper {
    
    /**
     * Converts our TypedIngredient wrappers to BookmarkIngredient objects
     */
    public static List<BookmarkIngredient> convertToBookmarkIngredients(List<TypedIngredient> ingredients) {
        List<BookmarkIngredient> result = new ArrayList<>();
        
        if (ingredients == null || ingredients.isEmpty()) {
            return result;
        }
        
        for (TypedIngredient ingredient : ingredients) {
            if (ingredient != null) {
                result.add(new BookmarkIngredient(ingredient.getWrappedIngredient()));
            }
        }
        
        return result;
    }
    
    /**
     * Converts generic Objects to TypedIngredient wrappers
     */
    public static List<TypedIngredient> wrapIngredients(List<Object> rawIngredients) {
        List<TypedIngredient> result = new ArrayList<>();
        
        if (rawIngredients == null || rawIngredients.isEmpty()) {
            return result;
        }
        
        for (Object obj : rawIngredients) {
            if (obj != null) {
                result.add(new TypedIngredient(obj));
            } else {
                ModLogger.warn("Skipping null object in wrapIngredients");
            }
        }
        
        return result;
    }
    
    /**
     * Extracts TypedIngredient objects from BookmarkIngredient objects
     */
    public static List<TypedIngredient> extractFromBookmarkIngredients(List<BookmarkIngredient> bookmarkIngredients) {
        List<TypedIngredient> result = new ArrayList<>();
        
        if (bookmarkIngredients == null || bookmarkIngredients.isEmpty()) {
            return result;
        }
        
        for (BookmarkIngredient ingredient : bookmarkIngredients) {
            if (ingredient != null && ingredient.getTypedIngredient() != null) {
                result.add(new TypedIngredient(ingredient.getTypedIngredient()));
            }
        }
        
        return result;
    }
    
    /**
     * Loads bookmarks from a folder and returns them as TypedIngredient objects.
     * Centralizes folder bookmark loading logic to avoid duplication.
     *
     * @param folderManager The folder data manager to use
     * @param folderId The ID of the folder to load bookmarks from
     * @param invalidateCache Whether to invalidate the cache before loading
     * @return A list of TypedIngredient objects representing the folder's bookmarks
     */
    public static List<TypedIngredient> loadBookmarksFromFolder(FolderDataManager folderManager, int folderId, boolean invalidateCache) {
        try {
            // First invalidate the cache if requested
            if (invalidateCache) {
                folderManager.invalidateIngredientsCache(folderId);
            }
            
            // Get bookmark keys to log the count
            List<String> bookmarkKeys = folderManager.getFolderBookmarkKeys(folderId);
            ModLogger.info("Loading {} bookmarks from folder ID {}", bookmarkKeys.size(), folderId);
            
            // Get fresh ingredients from cache
            List<Object> rawIngredients = folderManager.getCachedIngredientsForFolder(folderId);
            return wrapIngredients(rawIngredients);
        } catch (Exception e) {
            ModLogger.error("Error loading bookmarks from folder {}: {}", folderId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Updates a bookmark display with fresh ingredients from a folder.
     * Centralizes the display update logic.
     *
     * @param bookmarkDisplay The display to update
     * @param folder The folder to load ingredients from
     * @param folderManager The folder data manager
     * @return The list of TypedIngredient objects that were loaded and set
     */
    public static List<TypedIngredient> refreshBookmarkDisplay(
            FolderBookmarkContentsDisplay bookmarkDisplay,
            FolderDataRepresentation folder,
            FolderDataManager folderManager) {
        
        if (bookmarkDisplay == null || folder == null) {
            return new ArrayList<>();
        }
        
        try {
            // Load bookmarks, invalidating cache
            List<TypedIngredient> ingredients = loadBookmarksFromFolder(folderManager, folder.getId(), true);
            
            // Set active folder
            bookmarkDisplay.setActiveFolder(folder);
            
            // Convert to bookmark ingredients and update display
            List<BookmarkIngredient> bookmarkIngredients = convertToBookmarkIngredients(ingredients);
            bookmarkDisplay.setIngredients(bookmarkIngredients);
            
            return ingredients;
        } catch (Exception e) {
            ModLogger.error("Error refreshing bookmark display: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
}