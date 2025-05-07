package com.jeifolders.integration.ingredient;

import mezz.jei.api.ingredients.ITypedIngredient;
import com.jeifolders.util.ModLogger;
import com.jeifolders.integration.core.IngredientServiceImpl;
import com.jeifolders.integration.api.IIngredient;

import java.util.Objects;

/**
 * Unified ingredient class that replaces both TypedIngredient and BookmarkIngredient.
 * This class provides a consistent way to work with ingredients across the mod.
 */
public class Ingredient implements IIngredient {
    private final Object rawIngredient;
    private final ITypedIngredient<?> typedIngredient;
    private final IngredientType type;
    private String key;
    
    /**
     * Create an ingredient from a JEI typed ingredient
     * 
     * @param typedIngredient The JEI typed ingredient
     */
    public Ingredient(ITypedIngredient<?> typedIngredient) {
        this.typedIngredient = typedIngredient;
        this.rawIngredient = typedIngredient.getIngredient();
        this.type = determineType(typedIngredient);
        this.key = null; // Key will be generated when needed
    }
    
    /**
     * Create an ingredient from a JEI typed ingredient with a specified key
     * 
     * @param typedIngredient The JEI typed ingredient
     * @param key The key that uniquely identifies this ingredient
     */
    public Ingredient(ITypedIngredient<?> typedIngredient, String key) {
        this.typedIngredient = typedIngredient;
        this.rawIngredient = typedIngredient.getIngredient();
        this.type = determineType(typedIngredient);
        this.key = key;
    }
    
    /**
     * Create an ingredient from a raw object
     * 
     * @param rawIngredient The raw ingredient object
     */
    public Ingredient(Object rawIngredient) {
        this.rawIngredient = rawIngredient;
        this.typedIngredient = null;
        this.type = IngredientType.UNKNOWN;
    }
    
    /**
     * Create an ingredient with all properties specified
     * 
     * @param rawIngredient The raw ingredient object
     * @param typedIngredient The JEI typed ingredient
     * @param type The ingredient type
     */
    public Ingredient(Object rawIngredient, ITypedIngredient<?> typedIngredient, IngredientType type) {
        this.rawIngredient = rawIngredient;
        this.typedIngredient = typedIngredient;
        this.type = type;
    }
    
    /**
     * Get the raw ingredient object
     * 
     * @return The raw ingredient object
     */
    public Object getRawIngredient() {
        return rawIngredient;
    }
    
    /**
     * Get the JEI typed ingredient
     * 
     * @return The JEI typed ingredient
     */
    public ITypedIngredient<?> getTypedIngredient() {
        return typedIngredient;
    }
    
    /**
     * Get the ingredient type
     * 
     * @return The ingredient type
     */
    public IngredientType getType() {
        return type;
    }
    
    /**
     * Determine the ingredient type from a JEI typed ingredient
     * 
     * @param typedIngredient The JEI typed ingredient
     * @return The ingredient type
     */
    private IngredientType determineType(ITypedIngredient<?> typedIngredient) {
        String ingredientClassName = typedIngredient.getIngredient().getClass().getSimpleName();
        
        if (ingredientClassName.equals("ItemStack")) {
            return IngredientType.ITEM;
        } else if (ingredientClassName.equals("FluidStack")) {
            return IngredientType.FLUID;
        } else {
            ModLogger.debug("Unknown ingredient class: {}", ingredientClassName);
            return IngredientType.OTHER;
        }
    }
    
    /**
     * Get the string key that uniquely identifies this ingredient
     * 
     * @return The key string for this ingredient
     */
    public String getKey() {
        // Use the IngredientManager to generate a key if we have a typed ingredient
        if (key != null) {
            return key;
        }
        if (typedIngredient != null) {
            // Try to get the key from the IngredientServiceImpl directly
            try {
                return IngredientServiceImpl.getInstance().getKeyForIngredient(typedIngredient);
            } catch (Exception e) {
                ModLogger.warn("Failed to get key for ingredient: {}", e.getMessage());
                // Fall back to toString if there's an error
                return typedIngredient.toString();
            }
        }
        
        // Fall back to object's toString for ingredients without a typed ingredient
        return rawIngredient != null ? rawIngredient.toString() : "";
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Ingredient that = (Ingredient) o;
        
        if (typedIngredient != null && that.typedIngredient != null) {
            // If both have typed ingredients, compare them
            return Objects.equals(typedIngredient, that.typedIngredient);
        } else {
            // Otherwise compare raw ingredients
            return Objects.equals(rawIngredient, that.rawIngredient);
        }
    }
    
    @Override
    public int hashCode() {
        // Use typed ingredient for hash if available, otherwise use raw ingredient
        return typedIngredient != null ? typedIngredient.hashCode() : (rawIngredient != null ? rawIngredient.hashCode() : 0);
    }
    
    @Override
    public String toString() {
        if (typedIngredient != null) {
            return "Ingredient{type=" + type + ", typedIngredient=" + typedIngredient + "}";
        } else {
            return "Ingredient{type=" + type + ", rawIngredient=" + rawIngredient + "}";
        }
    }
}