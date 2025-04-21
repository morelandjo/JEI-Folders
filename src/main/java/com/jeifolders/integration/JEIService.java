package com.jeifolders.integration;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Central service interface for JEI runtime and integration functionality.
 * This centralizes all core JEI operations that were previously spread across
 * multiple integration classes.
 */
public interface JEIService {
    /**
     * Gets the JEI runtime, if available
     */
    Optional<Object> getJeiRuntime();
    
    /**
     * Gets the ingredient manager, if available
     */
    Optional<Object> getIngredientManager();
    
    /**
     * Sets the JEI runtime
     */
    void setJeiRuntime(Object runtime);
    
    /**
     * Registers a callback to be executed when JEI runtime becomes available
     */
    void registerRuntimeCallback(Consumer<Object> callback);
    
    /**
     * Gets the ingredient being dragged on screen, if any
     * @return An Optional containing the TypedIngredient being dragged, or empty if none
     */
    Optional<TypedIngredient> getDraggedIngredient();
    
    /**
     * Sets the current dragged ingredient
     */
    void setDraggedIngredient(Object ingredient);
    
    /**
     * Clears the current dragged ingredient
     */
    void clearDraggedIngredient();
    
    /**
     * Sets whether an ingredient is actually being dragged
     */
    void setActuallyDragging(boolean dragging);
}