// filepath: /Users/josh/IdeaProjects/JEI-Folders/JEI-Folders/src/main/java/com/jeifolders/integration/api/JEIIntegrationAPI.java
package com.jeifolders.integration.api;

import com.jeifolders.integration.core.JEIRuntime;
import com.jeifolders.integration.core.IngredientServiceImpl;
import com.jeifolders.integration.ingredient.IngredientManager;
import com.jeifolders.util.ModLogger;

import java.util.List;
import java.util.Optional;
import mezz.jei.api.ingredients.ITypedIngredient;

/**
 * Main access point for JEI integration API services.
 * This class provides static methods to access the various API services and
 * convenient shortcuts for common operations.
 */
public class JEIIntegrationAPI {
    
    private static IngredientService ingredientService;
    private static JEIIntegrationService integrationService;
    private static DragDropService dragDropService;
    
    // Private constructor to prevent instantiation
    private JEIIntegrationAPI() {}
    
    /**
     * Gets the ingredient service, which provides operations for working with ingredients
     * 
     * @return The ingredient service
     */
    public static IngredientService getIngredientService() {
        if (ingredientService == null) {
            ingredientService = IngredientServiceImpl.getInstance();
        }
        return ingredientService;
    }
    
    /**
     * Gets the JEI integration service, which provides access to JEI runtime functionality
     * 
     * @return The JEI integration service
     */
    public static JEIIntegrationService getIntegrationService() {
        if (integrationService == null) {
            // Use JEIRuntime singleton as the implementation
            integrationService = JEIRuntime.getInstance();
        }
        return integrationService;
    }
    
    /**
     * Gets the drag drop service, which manages ingredient drag operations
     * 
     * @return The drag drop service
     */
    public static DragDropService getDragDropService() {
        if (dragDropService == null) {
            // JEIRuntime also implements drag drop functionality
            dragDropService = JEIRuntime.getInstance();
        }
        return dragDropService;
    }
    
    /**
     * Gets the ingredient manager directly.
     * This is a convenience method for accessing the low-level ingredient management.
     * 
     * @return The ingredient manager instance
     */
    public static IngredientManager getIngredientManager() {
        return IngredientManager.getInstance();
    }
    
    /**
     * Checks if JEI is available
     * 
     * @return true if JEI is available, false otherwise
     */
    public static boolean isJEIAvailable() {
        return getIntegrationService().isJeiRuntimeAvailable();
    }
    
    /**
     * Sets custom service implementations for testing or extension purposes.
     * This is primarily intended for unit testing and should not be used in production code.
     * 
     * @param ingredientServiceImpl Custom ingredient service implementation
     * @param integrationServiceImpl Custom integration service implementation
     * @param dragDropServiceImpl Custom drag drop service implementation
     */
    public static void setServiceImplementations(
            IngredientService ingredientServiceImpl,
            JEIIntegrationService integrationServiceImpl,
            DragDropService dragDropServiceImpl) {
        
        ModLogger.debug("Setting custom service implementations");
        ingredientService = ingredientServiceImpl;
        integrationService = integrationServiceImpl;
        dragDropService = dragDropServiceImpl;
    }
    
    // Convenience methods for common operations
    
    /**
     * Gets an ingredient for a specific bookmark key.
     * This is a shortcut to the IngredientService method.
     * 
     * @param key The bookmark key to look up
     * @return The ingredient for the key, or empty if not found
     */
    public static Optional<IIngredient> getIngredientForKey(String key) {
        return getIngredientService().getIngredientForKey(key);
    }
    
    /**
     * Generates a unique key for an ingredient.
     * This is a shortcut to the IngredientManager method.
     * 
     * @param ingredient The ingredient to get the key for
     * @return The key string or empty if unable to determine
     */
    public static String getKeyForIngredient(Object ingredient) {
        return getIngredientManager().getKeyForIngredient(ingredient);
    }
    
    /**
     * Shows recipes for a specific ingredient.
     * This is a shortcut to the IntegrationService method.
     * 
     * @param ingredient The ingredient to show recipes for
     * @return true if recipes were shown, false otherwise
     */
    public static boolean showRecipes(Object ingredient) {
        return getIntegrationService().showRecipes(ingredient);
    }
    
    /**
     * Shows uses for a specific ingredient.
     * This is a shortcut to the IntegrationService method.
     * 
     * @param ingredient The ingredient to show uses for
     * @return true if uses were shown, false otherwise
     */
    public static boolean showUses(Object ingredient) {
        return getIntegrationService().showUses(ingredient);
    }
    
    /**
     * Gets all ingredients of a specific type.
     * This is a shortcut to the IngredientManager method.
     * 
     * @param ingredientType The type of ingredient to get
     * @return A list of all ingredients of the specified type
     */
    public static List<IIngredient> getAllIngredientsOfType(com.jeifolders.integration.ingredient.IngredientType ingredientType) {
        return getIngredientManager().getAllIngredientsOfType(ingredientType);
    }
    
    /**
     * Create an unified ingredient from a JEI typed ingredient.
     * This is a shortcut to the IngredientManager method.
     * 
     * @param ingredient The JEI typed ingredient
     * @return The unified ingredient
     */
    public static IIngredient createIngredient(ITypedIngredient<?> ingredient) {
        return getIngredientManager().createIngredient(ingredient);
    }
    
    /**
     * Create an unified ingredient from a raw object.
     * This is a shortcut to the IngredientManager method.
     * 
     * @param ingredient The raw ingredient object
     * @return The unified ingredient
     */
    public static IIngredient createIngredient(Object ingredient) {
        return getIngredientManager().createIngredient(ingredient);
    }
}