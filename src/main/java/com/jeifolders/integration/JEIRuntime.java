package com.jeifolders.integration;

import com.jeifolders.util.ModLogger;
import com.jeifolders.integration.ingredient.Ingredient;
import com.jeifolders.integration.ingredient.IngredientManager;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.helpers.IJeiHelpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Centralized access point for JEI runtime functionality.
 * Consolidates functionality previously spread across multiple service classes.
 */
public class JEIRuntime {
    private static final JEIRuntime INSTANCE = new JEIRuntime();
    
    // JEI runtime components
    private IJeiRuntime jeiRuntime;
    private IIngredientManager ingredientManager;
    
    // Callback management
    private final List<Consumer<IJeiRuntime>> runtimeCallbacks = new CopyOnWriteArrayList<>();
    
    // Dragging state management
    private ITypedIngredient<?> currentDraggedIngredient;
    private boolean isActuallyDragging = false;
    
    /**
     * Private constructor for singleton pattern
     */
    private JEIRuntime() {
        // Private constructor for singleton
    }
    
    /**
     * Gets the singleton instance
     */
    public static JEIRuntime getInstance() {
        return INSTANCE;
    }
    
    /**
     * Gets the JEI runtime instance
     * 
     * @return Optional containing the JEI runtime instance, if available
     */
    public Optional<IJeiRuntime> getJeiRuntime() {
        return Optional.ofNullable(jeiRuntime);
    }
    
    /**
     * Gets the JEI ingredient manager
     * 
     * @return Optional containing the ingredient manager, if available
     */
    public Optional<IIngredientManager> getIngredientManager() {
        if (ingredientManager != null) {
            return Optional.of(ingredientManager);
        }
        
        if (jeiRuntime != null) {
            ingredientManager = jeiRuntime.getIngredientManager();
            return Optional.ofNullable(ingredientManager);
        }
        
        return Optional.empty();
    }
    
    /**
     * Gets the JEI helpers
     * 
     * @return Optional containing JEI helpers, if available
     */
    public Optional<IJeiHelpers> getJeiHelpers() {
        if (jeiRuntime != null) {
            return Optional.of(jeiRuntime.getJeiHelpers());
        }
        return Optional.empty();
    }
    
    /**
     * Sets the JEI runtime and initializes related components
     * 
     * @param runtime The JEI runtime instance
     */
    public void setJeiRuntime(IJeiRuntime runtime) {
        if (runtime == null) {
            ModLogger.error("Null runtime provided to JEIRuntime");
            return;
        }
        
        this.jeiRuntime = runtime;
        this.ingredientManager = runtime.getIngredientManager();
        
        // Initialize the IngredientManager with JEI's ingredient manager
        IngredientManager.getInstance().initialize(ingredientManager);
        
        // Notify all callbacks
        List<Consumer<IJeiRuntime>> callbacksCopy = new ArrayList<>(runtimeCallbacks);
        runtimeCallbacks.clear();
        
        for (Consumer<IJeiRuntime> callback : callbacksCopy) {
            try {
                callback.accept(runtime);
            } catch (Exception e) {
                ModLogger.error("Error in JEI runtime callback: {}", e.getMessage(), e);
            }
        }
        
        ModLogger.debug("JEI runtime initialized successfully");
    }
    
    /**
     * Registers a callback to be executed when JEI runtime becomes available
     * 
     * @param callback The callback to register
     */
    public void registerRuntimeCallback(Consumer<IJeiRuntime> callback) {
        if (callback == null) {
            return;
        }
        
        if (jeiRuntime != null) {
            // Runtime already available, call immediately
            callback.accept(jeiRuntime);
        } else {
            // Queue for later
            runtimeCallbacks.add(callback);
        }
    }
    
    /**
     * Gets the currently dragged ingredient as a JEI ITypedIngredient
     * 
     * @return Optional containing the dragged ITypedIngredient, if any
     */
    public Optional<ITypedIngredient<?>> getDraggedITypedIngredient() {
        // Only return the dragged ingredient if we're actually dragging
        if (isActuallyDragging && currentDraggedIngredient != null) {
            return Optional.of(currentDraggedIngredient);
        }
        return Optional.empty();
    }
    
    /**
     * Gets the currently dragged ingredient, if any
     * 
     * @return Optional containing the dragged ingredient, if any
     */
    public Optional<Ingredient> getDraggedIngredient() {
        // Only return the dragged ingredient if we're actually dragging
        if (isActuallyDragging && currentDraggedIngredient != null) {
            // Convert from JEI's ITypedIngredient to our unified Ingredient
            return Optional.of(IngredientManager.getInstance().createIngredient(currentDraggedIngredient));
        }
        return Optional.empty();
    }
    
    /**
     * Sets the current dragged ingredient
     * 
     * @param ingredient The ingredient being dragged
     */
    public void setDraggedIngredient(ITypedIngredient<?> ingredient) {
        this.currentDraggedIngredient = ingredient;
        ModLogger.debug("Dragged ingredient set: {}", ingredient);
    }
    
    /**
     * Clears the current dragged ingredient
     */
    public void clearDraggedIngredient() {
        this.currentDraggedIngredient = null;
        this.isActuallyDragging = false;
        ModLogger.debug("Dragged ingredient cleared");
    }
    
    /**
     * Sets whether an ingredient is actually being dragged
     * 
     * @param dragging Whether an ingredient is being dragged
     */
    public void setActuallyDragging(boolean dragging) {
        this.isActuallyDragging = dragging;
        ModLogger.debug("Actually dragging set to: {}", dragging);
    }
    
    /**
     * Checks if an ingredient is actually being dragged
     * 
     * @return true if an ingredient is being actively dragged
     */
    public boolean isActuallyDragging() {
        return this.isActuallyDragging;
    }
}