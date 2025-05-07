// filepath: /Users/josh/IdeaProjects/JEI-Folders/JEI-Folders/src/main/java/com/jeifolders/integration/api/JEIIntegrationAPI.java
package com.jeifolders.integration.api;

import com.jeifolders.integration.core.JEIRuntime;
import com.jeifolders.integration.core.IngredientServiceImpl;
import com.jeifolders.util.ModLogger;

/**
 * Main access point for JEI integration API services.
 * This class provides static methods to access the various API services.
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
}