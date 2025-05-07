// filepath: /Users/josh/IdeaProjects/JEI-Folders/JEI-Folders/src/main/java/com/jeifolders/integration/api/IIngredient.java
package com.jeifolders.integration.api;

import com.jeifolders.integration.ingredient.IngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;

/**
 * Interface defining the contract for all ingredient implementations.
 * This provides consistent access to ingredient data across the mod.
 */
public interface IIngredient {
    
    /**
     * Get the raw ingredient object
     * 
     * @return The raw ingredient object
     */
    Object getRawIngredient();
    
    /**
     * Get the JEI typed ingredient if available
     * 
     * @return The JEI typed ingredient or null if not available
     */
    ITypedIngredient<?> getTypedIngredient();
    
    /**
     * Get the ingredient type
     * 
     * @return The ingredient type
     */
    IngredientType getType();
    
    /**
     * Get the string key that uniquely identifies this ingredient
     * 
     * @return The key string for this ingredient
     */
    String getKey();
}