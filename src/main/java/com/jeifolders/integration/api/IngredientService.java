// filepath: /Users/josh/IdeaProjects/JEI-Folders/JEI-Folders/src/main/java/com/jeifolders/integration/api/IngredientService.java
package com.jeifolders.integration.api;

import mezz.jei.api.ingredients.ITypedIngredient;

import java.util.List;
import java.util.Optional;

/**
 * Public API for the JEI ingredient system.
 * This interface provides access to the ingredient operations that are needed by other components.
 */
public interface IngredientService {
    
    /**
     * Gets the ingredient for a specific bookmark key
     * 
     * @param key The bookmark key
     * @return An Optional containing the ingredient, if found
     */
    Optional<IIngredient> getIngredientForKey(String key);
    
    /**
     * Gets the unique key for an ingredient
     * 
     * @param ingredient The ingredient to get a key for
     * @return A string key that uniquely identifies the ingredient
     */
    String getKeyForIngredient(Object ingredient);
    
    /**
     * Gets all cached ingredients for a specific folder
     * 
     * @param folderId The folder ID
     * @return A list of ingredients in the folder
     */
    List<IIngredient> getCachedIngredientsForFolder(int folderId);
    
    /**
     * Invalidates the ingredient cache for a specific folder
     * 
     * @param folderId The folder ID
     */
    void invalidateIngredientsCache(int folderId);
    
    /**
     * Adds an ingredient to a folder
     * 
     * @param folderId The folder ID
     * @param ingredient The ingredient to add
     * @return true if the ingredient was added, false if it was already in the folder
     */
    boolean addIngredientToFolder(int folderId, IIngredient ingredient);
    
    /**
     * Removes an ingredient from a folder
     * 
     * @param folderId The folder ID
     * @param ingredient The ingredient to remove
     * @return true if the ingredient was removed, false if it wasn't in the folder
     */
    boolean removeIngredientFromFolder(int folderId, IIngredient ingredient);
    
    /**
     * Checks if an ingredient is in a folder
     * 
     * @param folderId The folder ID
     * @param ingredient The ingredient to check
     * @return true if the ingredient is in the folder, false otherwise
     */
    boolean isIngredientInFolder(int folderId, IIngredient ingredient);
    
    /**
     * Creates a unified ingredient from a JEI ITypedIngredient
     * 
     * @param typedIngredient The JEI typed ingredient
     * @return The unified ingredient
     */
    IIngredient createIngredient(ITypedIngredient<?> typedIngredient);
    
    /**
     * Creates a unified ingredient from a raw object
     * 
     * @param ingredient The raw ingredient object
     * @return The unified ingredient
     */
    IIngredient createIngredient(Object ingredient);
    
    /**
     * Gets the display name for an ingredient
     * 
     * @param ingredient The ingredient
     * @return The display name
     */
    String getDisplayName(IIngredient ingredient);
}