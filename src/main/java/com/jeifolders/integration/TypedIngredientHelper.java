package com.jeifolders.integration;

import com.jeifolders.data.FolderDataService;
import com.jeifolders.data.FolderDataRepresentation;
import com.jeifolders.gui.bookmarks.UnifiedFolderContentsDisplay;
import com.jeifolders.util.ModLogger;
import mezz.jei.api.ingredients.ITypedIngredient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Helper utility for working with typed ingredients without direct JEI dependencies.
 * This class centralizes all ingredient conversion operations to reduce code duplication.
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
     * Converts a single TypedIngredient to a BookmarkIngredient
     */
    public static BookmarkIngredient convertToBookmarkIngredient(TypedIngredient ingredient) {
        if (ingredient == null) {
            return null;
        }
        return new BookmarkIngredient(ingredient.getWrappedIngredient());
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
     * Wraps a single object in a TypedIngredient
     * 
     * @param ingredient The raw ingredient object to wrap
     * @return A TypedIngredient wrapper for the object
     */
    public static TypedIngredient wrapIngredient(Object ingredient) {
        if (ingredient == null) {
            ModLogger.warn("Attempted to wrap null ingredient");
            return null;
        }
        
        try {
            // If it's already a TypedIngredient, just return it
            if (ingredient instanceof TypedIngredient) {
                return (TypedIngredient) ingredient;
            }
            
            // If it's a BookmarkIngredient, extract the TypedIngredient from it
            if (ingredient instanceof BookmarkIngredient) {
                BookmarkIngredient bookmarkIngredient = (BookmarkIngredient) ingredient;
                Object typedIngredient = bookmarkIngredient.getTypedIngredient();
                if (typedIngredient != null) {
                    return new TypedIngredient(typedIngredient);
                }
            }
            
            // Otherwise, wrap the raw object
            return new TypedIngredient(ingredient);
        } catch (Exception e) {
            ModLogger.error("Error wrapping ingredient: {}", e.getMessage(), e);
            return null;
        }
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
     * Extracts a single TypedIngredient from a BookmarkIngredient
     */
    public static TypedIngredient extractFromBookmarkIngredient(BookmarkIngredient bookmarkIngredient) {
        if (bookmarkIngredient == null || bookmarkIngredient.getTypedIngredient() == null) {
            return null;
        }
        return new TypedIngredient(bookmarkIngredient.getTypedIngredient());
    }
    
    /**
     * Loads bookmarks from a folder and returns them as TypedIngredient objects.
     * Centralizes folder bookmark loading logic to avoid duplication.
     *
     * @param folderService The folder data service to use
     * @param folderId The ID of the folder to load bookmarks from
     * @param invalidateCache Whether to invalidate the cache before loading
     * @return A list of TypedIngredient objects representing the folder's bookmarks
     */
    public static List<TypedIngredient> loadBookmarksFromFolder(FolderDataService folderService, int folderId, boolean invalidateCache) {
        try {
            // First invalidate the cache if requested
            if (invalidateCache) {
                folderService.invalidateIngredientsCache(folderId);
            }
            
            // Get bookmark keys to log the count
            List<String> bookmarkKeys = folderService.getFolderBookmarkKeys(folderId);
            ModLogger.info("Loading {} bookmarks from folder ID {}", bookmarkKeys.size(), folderId);
            
            // Get fresh ingredients from cache
            List<Object> rawIngredients = folderService.getCachedIngredientsForFolder(folderId);
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
     * @param folderService The folder data service
     * @return The list of TypedIngredient objects that were loaded and set
     */
    public static List<TypedIngredient> refreshBookmarkDisplay(
            UnifiedFolderContentsDisplay bookmarkDisplay,
            FolderDataRepresentation folder,
            FolderDataService folderService) {
        
        if (bookmarkDisplay == null || folder == null) {
            return new ArrayList<>();
        }
        
        try {
            // Load bookmarks, invalidating cache
            List<TypedIngredient> ingredients = loadBookmarksFromFolder(folderService, folder.getId(), true);
            
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
     * Sets ingredients on a display from a list of TypedIngredient objects.
     * Centralizes the conversion and setting process.
     *
     * @param display The display to update
     * @param ingredients The TypedIngredient objects to set
     * @return true if successful, false otherwise
     */
    public static boolean setIngredientsOnDisplay(UnifiedFolderContentsDisplay display, List<TypedIngredient> ingredients) {
        if (display == null) {
            return false;
        }
        
        try {
            List<BookmarkIngredient> bookmarkIngredients = convertToBookmarkIngredients(ingredients);
            display.setIngredients(bookmarkIngredients);
            return true;
        } catch (Exception e) {
            ModLogger.error("Error setting ingredients on display: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Gets ingredients from a display as TypedIngredient objects.
     * Centralizes the extraction process.
     *
     * @param display The display to get ingredients from
     * @return The ingredients as TypedIngredient objects
     */
    public static List<TypedIngredient> getIngredientsFromDisplay(UnifiedFolderContentsDisplay display) {
        if (display == null) {
            return new ArrayList<>();
        }
        
        try {
            List<BookmarkIngredient> bookmarkIngredients = display.getIngredients();
            return extractFromBookmarkIngredients(bookmarkIngredients);
        } catch (Exception e) {
            ModLogger.error("Error getting ingredients from display: {}", e.getMessage(), e);
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
    
    /**
     * Gets the ingredient key from various ingredient types
     * 
     * @param ingredient The ingredient to get the key for
     * @return The key string or empty if unable to determine
     */
    public static String getKeyForIngredient(Object ingredient) {
        if (ingredient == null) {
            return "";
        }
        
        try {
            IngredientService ingredientService = JEIIntegrationFactory.getIngredientService();
            
            if (ingredient instanceof BookmarkIngredient) {
                return ingredientService.getKeyForIngredient(((BookmarkIngredient)ingredient).getTypedIngredient());
            } else if (ingredient instanceof TypedIngredient) {
                return ingredientService.getKeyForIngredient(((TypedIngredient)ingredient).getWrappedIngredient());
            } else {
                return ingredientService.getKeyForIngredient(ingredient);
            }
        } catch (Exception e) {
            ModLogger.error("Error getting key for ingredient: {}", e.getMessage(), e);
            return "";
        }
    }
    
    /**
     * Compare two ingredients for equality based on their keys
     * 
     * @param a First ingredient
     * @param b Second ingredient
     * @return true if both ingredients have the same key
     */
    public static boolean areIngredientsEqual(Object a, Object b) {
        if (a == null || b == null) {
            return false;
        }
        
        if (a == b) {
            return true;
        }
        
        String keyA = getKeyForIngredient(a);
        String keyB = getKeyForIngredient(b);
        
        return !keyA.isEmpty() && !keyB.isEmpty() && keyA.equals(keyB);
    }
    
    /**
     * Find an ingredient in a list based on key matching
     * 
     * @param ingredient The ingredient to find
     * @param list The list to search in
     * @return The found ingredient or empty if not found
     */
    public static <T> Optional<T> findIngredient(Object ingredient, List<T> list) {
        if (ingredient == null || list == null || list.isEmpty()) {
            return Optional.empty();
        }
        
        String key = getKeyForIngredient(ingredient);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        
        return list.stream()
            .filter(Objects::nonNull)
            .filter(item -> {
                String itemKey = getKeyForIngredient(item);
                return !itemKey.isEmpty() && itemKey.equals(key);
            })
            .findFirst();
    }
    
    /**
     * Creates a combined, deduplicated list of ingredients
     * 
     * @param listA First list
     * @param listB Second list
     * @return Combined list with no duplicates
     */
    public static <T> List<T> combineIngredientLists(List<T> listA, List<T> listB) {
        if (listA == null || listA.isEmpty()) {
            return listB != null ? new ArrayList<>(listB) : new ArrayList<>();
        }
        
        if (listB == null || listB.isEmpty()) {
            return new ArrayList<>(listA);
        }
        
        List<T> result = new ArrayList<>(listA);
        
        for (T item : listB) {
            if (item == null) {
                continue;
            }
            
            // Check if item already exists in result by ingredient key
            boolean exists = result.stream()
                .anyMatch(existingItem -> areIngredientsEqual(item, existingItem));
                
            if (!exists) {
                result.add(item);
            }
        }
        
        return result;
    }
}