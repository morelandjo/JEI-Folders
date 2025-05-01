package com.jeifolders.integration;

import com.jeifolders.integration.impl.JEIIngredientService;
import com.jeifolders.integration.ingredient.IngredientManager;
import com.jeifolders.util.ModLogger;

/**
 * Factory for accessing JEI integration services.
 * Provides centralized access to all JEI integration components.
 */
public class JEIIntegrationFactory {
    
    // Single instance of the ingredient service
    private static final IngredientService ingredientService = new JEIIngredientService();
    
    private JEIIntegrationFactory() {
        // Private constructor to prevent instantiation
    }
    
    /**
     * Gets the JEI runtime for direct access to JEI functionality
     */
    public static JEIRuntime getJEIRuntime() {
        return JEIRuntime.getInstance();
    }
    
    /**
     * Gets the ingredient service for ingredient operations
     */
    public static IngredientService getIngredientService() {
        return ingredientService;
    }
    
    /**
     * Gets the ingredient manager for unified ingredient handling
     */
    public static IngredientManager getIngredientManager() {
        return IngredientManager.getInstance();
    }
}