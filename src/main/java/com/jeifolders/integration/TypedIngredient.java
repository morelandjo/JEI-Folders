package com.jeifolders.integration;

/**
 * Wrapper for JEI's ITypedIngredient to avoid direct dependency.
 * This provides a type-safe way to work with JEI ingredients without directly depending on JEI.
 */
public class TypedIngredient {
    private final Object wrappedIngredient;
    
    /**
     * Creates a new TypedIngredient that wraps the JEI ITypedIngredient object
     * @param ingredient The JEI ITypedIngredient to wrap
     */
    public TypedIngredient(Object ingredient) {
        this.wrappedIngredient = ingredient;
    }
    
    /**
     * Gets the raw ingredient object
     * @return The wrapped ingredient
     */
    public Object getIngredient() {
        return wrappedIngredient;
    }
    
    /**
     * Gets the unwrapped ITypedIngredient for use in integration code only
     * @return The raw ITypedIngredient object
     */
    public Object getWrappedIngredient() {
        return wrappedIngredient;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TypedIngredient)) return false;
        
        TypedIngredient other = (TypedIngredient) obj;
        if (wrappedIngredient == null) {
            return other.wrappedIngredient == null;
        }
        
        return wrappedIngredient.equals(other.wrappedIngredient);
    }
    
    @Override
    public int hashCode() {
        return wrappedIngredient != null ? wrappedIngredient.hashCode() : 0;
    }
    
    @Override
    public String toString() {
        return wrappedIngredient != null ? wrappedIngredient.toString() : "null";
    }
}