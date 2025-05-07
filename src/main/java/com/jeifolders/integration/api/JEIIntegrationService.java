// filepath: /Users/josh/IdeaProjects/JEI-Folders/JEI-Folders/src/main/java/com/jeifolders/integration/api/JEIIntegrationService.java
package com.jeifolders.integration.api;

import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Main API for JEI integration services.
 * This interface provides access to the JEI runtime and related functionality.
 */
public interface JEIIntegrationService {
    
    /**
     * Gets the JEI runtime instance, if available
     * 
     * @return Optional containing the JEI runtime, or empty if not available
     */
    Optional<IJeiRuntime> getJeiRuntime();
    
    /**
     * Gets the JEI ingredient manager, if available
     * 
     * @return Optional containing the ingredient manager, or empty if not available
     */
    Optional<IIngredientManager> getIngredientManager();
    
    /**
     * Gets the JEI helpers, if available
     * 
     * @return Optional containing the JEI helpers, or empty if not available
     */
    Optional<IJeiHelpers> getJeiHelpers();
    
    /**
     * Registers a callback to be executed when the JEI runtime becomes available.
     * If the runtime is already available, the callback is executed immediately.
     * 
     * @param callback The callback to register
     */
    void registerRuntimeCallback(Consumer<IJeiRuntime> callback);
    
    /**
     * Checks if the JEI runtime is currently available
     * 
     * @return true if JEI runtime is available, false otherwise
     */
    boolean isJeiRuntimeAvailable();

    /**
     * Sets the JEI runtime instance
     * 
     * @param runtime The JEI runtime to set
     */
    void setJeiRuntime(IJeiRuntime runtime);

    /**
     * Shows an ingredient in JEI (focuses on it in the ingredient list)
     * 
     * @param ingredient The ingredient to show
     * @return true if the ingredient was shown successfully
     */
    boolean showIngredientInJEI(IIngredient ingredient);
}