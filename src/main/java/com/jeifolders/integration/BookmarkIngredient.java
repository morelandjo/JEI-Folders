package com.jeifolders.integration;

import mezz.jei.api.ingredients.ITypedIngredient;

/**
 * Wrapper class for ITypedIngredient that keeps JEI dependencies isolated to the integration package.
 * This allows the GUI layer to work with ingredients without direct JEI dependencies.
 */
public class BookmarkIngredient {
    private final ITypedIngredient<?> typedIngredient;
    
    /**
     * Create a new BookmarkIngredient wrapper.
     * 
     * @param typedIngredient The JEI typed ingredient to wrap
     */
    public BookmarkIngredient(ITypedIngredient<?> typedIngredient) {
        this.typedIngredient = typedIngredient;
    }
    
    /**
     * Create a new BookmarkIngredient from an Object that represents a JEI typed ingredient.
     * This constructor is used when working with our JEI integration layer to convert without
     * exposing direct JEI classes outside the integration package.
     * 
     * @param ingredient The object representing a JEI typed ingredient
     */
    @SuppressWarnings("unchecked")
    public BookmarkIngredient(Object ingredient) {
        if (ingredient instanceof ITypedIngredient) {
            this.typedIngredient = (ITypedIngredient<?>) ingredient;
        } else {
            throw new IllegalArgumentException("Cannot create BookmarkIngredient from: " + 
                (ingredient != null ? ingredient.getClass().getName() : "null"));
        }
    }
    
    /**
     * Get the wrapped JEI typed ingredient.
     * This method should only be used within the integration package.
     * 
     * @return The wrapped JEI typed ingredient
     */
    public ITypedIngredient<?> getTypedIngredient() {
        return typedIngredient;
    }
    
    /**
     * Get the raw ingredient object contained within the typed ingredient.
     * This provides direct access to the underlying Minecraft item/fluid/etc.
     * 
     * @return The raw ingredient object
     */
    public Object getWrappedIngredient() {
        if (typedIngredient == null) {
            return null;
        }
        return typedIngredient.getIngredient();
    }
    
    /**
     * Get a simple string representation of this ingredient.
     * 
     * @return A string representation
     */
    @Override
    public String toString() {
        if (typedIngredient == null) {
            return "Empty BookmarkIngredient";
        }
        return "BookmarkIngredient[" + typedIngredient.toString() + "]";
    }
}