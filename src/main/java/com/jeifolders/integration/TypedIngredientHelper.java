package com.jeifolders.integration;

import com.jeifolders.data.FolderDataManager;
import com.jeifolders.data.FolderDataRepresentation;
import com.jeifolders.gui.bookmarks.UnifiedFolderContentsDisplay;
import com.jeifolders.util.ModLogger;
import mezz.jei.api.ingredients.ITypedIngredient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
            UnifiedFolderContentsDisplay bookmarkDisplay,
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
    
    /**
     * Wraps JEI ITypedIngredient objects in BookmarkIngredient wrappers.
     * Consolidated from BookmarkDisplayHelper.
     * 
     * @param ingredients The JEI ingredients to wrap
     * @return A list of BookmarkIngredient wrappers
     */
    public static List<BookmarkIngredient> wrapJeiIngredients(List<ITypedIngredient<?>> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return Collections.emptyList();
        }
        
        return ingredients.stream()
            .filter(i -> i != null)
            .map(BookmarkIngredient::new)
            .collect(Collectors.toList());
    }
    
    /**
     * Unwraps BookmarkIngredient objects to JEI ITypedIngredient objects.
     * Consolidated from BookmarkDisplayHelper.
     * 
     * @param bookmarkIngredients The wrappers to unwrap
     * @return A list of JEI ITypedIngredient objects
     */
    public static List<ITypedIngredient<?>> unwrapToJeiIngredients(List<BookmarkIngredient> bookmarkIngredients) {
        if (bookmarkIngredients == null || bookmarkIngredients.isEmpty()) {
            return Collections.emptyList();
        }
        
        return bookmarkIngredients.stream()
            .filter(i -> i != null && i.getTypedIngredient() != null)
            .map(BookmarkIngredient::getTypedIngredient)
            .collect(Collectors.toList());
    }
    
    /**
     * Convert generic objects to BookmarkIngredient wrappers.
     * Consolidated from BookmarkDisplayHelper and expanded to handle more types.
     * 
     * @param objects The objects to convert
     * @return A list of BookmarkIngredient wrappers
     */
    @SuppressWarnings("unchecked")
    public static List<BookmarkIngredient> convertObjectsToBookmarkIngredients(List<Object> objects) {
        List<BookmarkIngredient> result = new ArrayList<>();
        
        if (objects == null || objects.isEmpty()) {
            return result;
        }
        
        for (Object obj : objects) {
            if (obj instanceof ITypedIngredient<?>) {
                result.add(new BookmarkIngredient((ITypedIngredient<?>)obj));
            } else if (obj instanceof BookmarkIngredient) {
                result.add((BookmarkIngredient)obj);
            } else if (obj instanceof TypedIngredient) {
                TypedIngredient typedIngredient = (TypedIngredient)obj;
                Object wrappedObj = typedIngredient.getWrappedIngredient();
                if (wrappedObj instanceof ITypedIngredient<?>) {
                    result.add(new BookmarkIngredient((ITypedIngredient<?>)wrappedObj));
                } else {
                    ModLogger.warn("Unable to convert TypedIngredient with non-ITypedIngredient content: {}", 
                        wrappedObj != null ? wrappedObj.getClass().getName() : "null");
                }
            } else {
                ModLogger.warn("Skipping non-supported object: {}", 
                    obj != null ? obj.getClass().getName() : "null");
            }
        }
        
        return result;
    }
}