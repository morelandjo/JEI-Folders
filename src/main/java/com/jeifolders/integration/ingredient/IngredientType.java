package com.jeifolders.integration.ingredient;

/**
 * Enum representing the different types of ingredients supported by JEI.
 * This helps categorize ingredients and determine how to handle them.
 */
public enum IngredientType {
    /** Represents Minecraft item-based ingredients */
    ITEM,
    
    /** Represents Minecraft fluid-based ingredients */
    FLUID,
    
    /** Represents other known ingredient types added by other mods */
    OTHER,
    
    /** Used when the type cannot be determined */
    UNKNOWN
}