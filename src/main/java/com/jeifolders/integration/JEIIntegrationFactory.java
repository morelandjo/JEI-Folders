package com.jeifolders.integration;

import com.jeifolders.integration.impl.JEIIngredientService;

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
     * Gets the JEI service for runtime management
     */
    public static JEIService getJEIService() {
        return JEIServiceImpl.getInstance();
    }
    
    /**
     * Gets the ingredient service for ingredient operations
     */
    public static IngredientService getIngredientService() {
        return ingredientService;
    }
    
    /**
     * Gets the bookmark service for bookmark operations
     */
    public static BookmarkService getBookmarkService() {
        return BookmarkServiceImpl.getInstance();
    }
}