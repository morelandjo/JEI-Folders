package com.jeifolders.integration;

import com.jeifolders.integration.impl.JEIIngredientService;
import com.jeifolders.util.ModLogger;
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
 * Implementation of JEIService that centralizes JEI runtime management
 */
public class JEIServiceImpl implements JEIService {
    private static final JEIServiceImpl INSTANCE = new JEIServiceImpl();
    
    private IJeiRuntime jeiRuntime;
    private IIngredientManager ingredientManager;
    private final List<Consumer<Object>> runtimeCallbacks = new CopyOnWriteArrayList<>();
    private ITypedIngredient<?> currentDraggedIngredient;
    private boolean isActuallyDragging = false;
    
    private JEIServiceImpl() {
        // Private constructor for singleton
    }
    
    /**
     * Gets the singleton instance
     */
    public static JEIService getInstance() {
        return INSTANCE;
    }
    
    @Override
    public Optional<Object> getJeiRuntime() {
        return Optional.ofNullable(jeiRuntime);
    }
    
    @Override
    public Optional<Object> getIngredientManager() {
        if (ingredientManager != null) {
            return Optional.of(ingredientManager);
        }
        
        if (jeiRuntime != null) {
            ingredientManager = jeiRuntime.getIngredientManager();
            return Optional.ofNullable(ingredientManager);
        }
        
        return Optional.empty();
    }
    
    @Override
    public void setJeiRuntime(Object runtime) {
        if (runtime instanceof IJeiRuntime ijeiRuntime) {
            this.jeiRuntime = ijeiRuntime;
            this.ingredientManager = ijeiRuntime.getIngredientManager();
            
            // Initialize JEIIngredientService with the ingredient manager and helpers
            IngredientService ingredientService = JEIIntegrationFactory.getIngredientService();
            if (ingredientService instanceof JEIIngredientService jeiIngredientService) {
                jeiIngredientService.setIngredientManager(ingredientManager);
                
                // Also set the JEI helpers to access the codecHelper for ingredient serialization
                IJeiHelpers jeiHelpers = ijeiRuntime.getJeiHelpers();
                jeiIngredientService.setJeiHelpers(jeiHelpers);
            }
            
            // Notify all callbacks
            List<Consumer<Object>> callbacksCopy = new ArrayList<>(runtimeCallbacks);
            runtimeCallbacks.clear();
            
            for (Consumer<Object> callback : callbacksCopy) {
                try {
                    callback.accept(runtime);
                } catch (Exception e) {
                    ModLogger.error("Error in JEI runtime callback: {}", e.getMessage(), e);
                }
            }
        } else {
            ModLogger.error("Invalid runtime object provided, expected IJeiRuntime but got: {}", 
                runtime != null ? runtime.getClass().getName() : "null");
        }
    }
    
    @Override
    public void registerRuntimeCallback(Consumer<Object> callback) {
        if (jeiRuntime != null) {
            // Runtime already available, call immediately
            callback.accept(jeiRuntime);
        } else {
            // Queue for later
            runtimeCallbacks.add(callback);
        }
    }
    
    @Override
    public Optional<TypedIngredient> getDraggedIngredient() {
        // Only return the dragged ingredient if we're actually dragging
        if (isActuallyDragging && currentDraggedIngredient != null) {
            return Optional.of(new TypedIngredient(currentDraggedIngredient));
        }
        return Optional.empty();
    }
    
    @Override
    public void setDraggedIngredient(Object ingredient) {
        if (ingredient instanceof ITypedIngredient<?> typedIngredient) {
            this.currentDraggedIngredient = typedIngredient;
            ModLogger.debug("Dragged ingredient set: {}", ingredient);
        } else {
            ModLogger.error("Invalid ingredient type: {}", 
                ingredient != null ? ingredient.getClass().getName() : "null");
        }
    }
    
    @Override
    public void clearDraggedIngredient() {
        this.currentDraggedIngredient = null;
        this.isActuallyDragging = false;
        ModLogger.debug("Dragged ingredient cleared");
    }
    
    @Override
    public void setActuallyDragging(boolean dragging) {
        this.isActuallyDragging = dragging;
        ModLogger.debug("Actually dragging set to: {}", dragging);
    }
    
    @Override
    public boolean isActuallyDragging() {
        return this.isActuallyDragging;
    }
}