package com.jeifolders.ingredient;

import mezz.jei.api.ingredients.ITypedIngredient;
import java.util.Objects;
import java.util.Optional;

/**
 * A unified ingredient class that wraps JEI's ITypedIngredient.
 * This abstraction allows for better maintainability and testing
 * by decoupling our code from JEI's specific implementation.
 */
public class Ingredient {
    private final ITypedIngredient<?> typedIngredient;
    private final String key;
    
    /**
     * Creates a new Ingredient instance.
     * 
     * @param typedIngredient The JEI typed ingredient
     * @param key The ingredient key used for storage/retrieval
     */
    public Ingredient(ITypedIngredient<?> typedIngredient, String key) {
        this.typedIngredient = Objects.requireNonNull(typedIngredient, "typedIngredient cannot be null");
        this.key = Objects.requireNonNull(key, "key cannot be null");
    }
    
    /**
     * Gets the wrapped JEI typed ingredient.
     */
    public ITypedIngredient<?> getTypedIngredient() {
        return typedIngredient;
    }
    
    /**
     * Gets the raw ingredient object.
     */
    public Object getIngredient() {
        return typedIngredient.getIngredient();
    }
    
    /**
     * Gets the ingredient key used for storage and retrieval.
     */
    public String getKey() {
        return key;
    }
    
    /**
     * Creates an optional Ingredient from an ITypedIngredient and key.
     * Returns empty if either parameter is null.
     */
    public static Optional<Ingredient> create(ITypedIngredient<?> typedIngredient, String key) {
        if (typedIngredient == null || key == null || key.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Ingredient(typedIngredient, key));
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Ingredient that = (Ingredient) o;
        return key.equals(that.key);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(key);
    }
    
    @Override
    public String toString() {
        return "Ingredient{key='" + key + "'}";
    }
}