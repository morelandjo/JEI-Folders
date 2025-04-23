package com.jeifolders.integration;

import mezz.jei.api.ingredients.ITypedIngredient;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for ingredient-related operations.
 * Centralizes functionality that was previously spread across multiple classes.
 */
public interface IngredientService {
    /**
     * Gets a key for an ingredient that can be used for storage and retrieval
     */
    String getKeyForIngredient(Object ingredient);
    
    /**
     * Gets an ingredient for a bookmark key
     */
    Optional<ITypedIngredient<?>> getIngredientForKey(String bookmarkKey);
    
    /**
     * Gets ingredients for a list of bookmark keys
     */
    List<ITypedIngredient<?>> getIngredientsForKeys(List<String> bookmarkKeys);
    
    /**
     * Converts a generic Object to an ITypedIngredient if possible
     */
    Optional<ITypedIngredient<?>> getTypedIngredientFromObject(Object ingredient);
    
    /**
     * Gets cached ingredients for a folder
     */
    List<ITypedIngredient<?>> getCachedIngredientsForFolder(int folderId);
    
    /**
     * Gets cached ingredients for a folder as BookmarkIngredient objects
     */
    default List<BookmarkIngredient> getCachedBookmarkIngredientsForFolder(int folderId) {
        List<ITypedIngredient<?>> ingredients = getCachedIngredientsForFolder(folderId);
        return TypedIngredientHelper.wrapJeiIngredients(ingredients);
    }
    
    /**
     * Invalidates the ingredient cache for a folder
     */
    void invalidateIngredientsCache(int folderId);
    
    /**
     * Clears the ingredient cache
     */
    void clearCache();
}