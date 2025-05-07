package com.jeifolders.integration.core;

import com.jeifolders.util.ModLogger;
import com.jeifolders.integration.api.IIngredient;
import com.jeifolders.integration.api.JEIIntegrationService;
import com.jeifolders.integration.api.DragDropService;
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
public class JEIRuntime implements JEIIntegrationService, DragDropService {
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
    @Override
    public Optional<IJeiRuntime> getJeiRuntime() {
        return Optional.ofNullable(jeiRuntime);
    }
    
    /**
     * Gets the JEI ingredient manager
     * 
     * @return Optional containing the ingredient manager, if available
     */
    @Override
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
    @Override
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
    @Override
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
    @Override
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
     * Checks if the JEI runtime is currently available
     * 
     * @return true if JEI runtime is available, false otherwise
     */
    @Override
    public boolean isJeiRuntimeAvailable() {
        return jeiRuntime != null;
    }
    
    /**
     * Gets the currently dragged ingredient as a JEI ITypedIngredient
     * 
     * @return Optional containing the dragged ITypedIngredient, if any
     */
    @Override
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
    @Override
    public Optional<IIngredient> getDraggedIngredient() {
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
    @Override
    public void setDraggedIngredient(ITypedIngredient<?> ingredient) {
        if (ingredient != null) {
            this.currentDraggedIngredient = ingredient;
            ModLogger.debug("Dragged ingredient set: {}", ingredient);
        }
    }
    
    /**
     * Clears the current dragged ingredient
     */
    @Override
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
    @Override
    public void setActuallyDragging(boolean dragging) {
        this.isActuallyDragging = dragging;
        ModLogger.debug("Actually dragging set to: {}", dragging);
    }
    
    /**
     * Checks if an ingredient is actually being dragged
     * 
     * @return true if an ingredient is being actively dragged
     */
    @Override
    public boolean isActuallyDragging() {
        return this.isActuallyDragging;
    }
    
    /**
     * Shows an ingredient in JEI (focuses on it in the ingredient list)
     * 
     * @param ingredient The ingredient to show
     * @return true if the ingredient was shown successfully
     */
    @Override
    public boolean showIngredientInJEI(IIngredient ingredient) {
        if (ingredient == null || jeiRuntime == null) {
            return false;
        }
        
        try {
            // Get the typed ingredient from our unified ingredient
            ITypedIngredient<?> typedIngredient = ingredient.getTypedIngredient();
            
            if (typedIngredient != null) {
                // Get the JEI RecipesGui
                var recipesGui = jeiRuntime.getRecipesGui();
                
                // Get the JEI helpers to access the focus factory
                var jeiHelpers = jeiRuntime.getJeiHelpers();
                var focusFactory = jeiHelpers.getFocusFactory();
                
                // Create a focus using the proper factory method with INPUT role
                var focus = focusFactory.createFocus(
                    mezz.jei.api.recipe.RecipeIngredientRole.INPUT, 
                    typedIngredient
                );
                
                // Show the ingredient in JEI
                recipesGui.show(focus);
                return true;
            }
        } catch (Exception e) {
            ModLogger.error("Error showing ingredient in JEI: {}", e.getMessage(), e);
        }
        
        return false;
    }
}