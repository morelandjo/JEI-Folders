package com.jeifolders.integration;

import com.jeifolders.integration.ingredient.Ingredient;
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
    Optional<Ingredient> getIngredientForKey(String bookmarkKey);
    
    /**
     * Gets ingredients for a list of bookmark keys
     */
    List<Ingredient> getIngredientsForKeys(List<String> bookmarkKeys);
    
    /**
     * Converts a generic Object to an Ingredient if possible
     */
    Optional<Ingredient> getIngredientFromObject(Object ingredientObj);
    
    /**
     * Gets the underlying ITypedIngredient for compatibility with JEI
     */
    Optional<ITypedIngredient<?>> getTypedIngredientFromObject(Object ingredient);
    
    /**
     * Gets cached ingredients for a folder
     */
    List<Ingredient> getCachedIngredientsForFolder(int folderId);
    
    /**
     * Invalidates the ingredient cache for a folder
     */
    void invalidateIngredientsCache(int folderId);
    
    /**
     * Clears the ingredient cache
     */
    void clearCache();
}