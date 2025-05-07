package com.jeifolders.ui.util;

import com.jeifolders.util.ModLogger;
import com.jeifolders.integration.IngredientDragHandler;
import com.jeifolders.integration.api.IIngredient;
import com.jeifolders.integration.ingredient.IngredientManager;
import mezz.jei.api.ingredients.ITypedIngredient;

import java.util.Optional;

/**
 * Provides compatibility interface between JEI runtime and the folder system.
 * Note: Primary drag handling now happens through FolderGhostIngredientHandler.
 */
public class IngredientDragManager {
    private static IngredientDragManager instance;
    
    // Delegate functionality to the handler in the integration package
    private final IngredientDragHandler ingredientDragHandler = new IngredientDragHandler();

    private IngredientDragManager() {
        // Simplified initialization
    }

    public static IngredientDragManager getInstance() {
        if (instance == null) {
            instance = new IngredientDragManager();
        }
        return instance;
    }

    /**
     * Sets the JEI runtime for ingredient operations
     * @param jeiRuntime The JEI runtime object
     */
    public void setJeiRuntime(Object jeiRuntime) {
        // Pass the runtime to the ingredient drag handler
        try {
            this.ingredientDragHandler.setJeiRuntime(jeiRuntime);
            ModLogger.debug("JEI runtime set in IngredientDragManager");
        } catch (Exception e) {
            ModLogger.error("Failed to initialize drag handler with JEI runtime: {}", e.getMessage());
        }
    }

    /**
     * Gets the currently dragged ingredient, if any
     * @return Optional containing the unified IIngredient, or empty if none
     */
    public Optional<IIngredient> getDraggedIngredient() {
        // Get the dragged ingredient as ITypedIngredient<?> from the handler
        Optional<ITypedIngredient<?>> jeiIngredient = ingredientDragHandler.getDraggedIngredient();
        
        // If no ingredient is being dragged, return empty
        if (jeiIngredient.isEmpty()) {
            return Optional.empty();
        }
        
        // Convert from JEI's ITypedIngredient to our unified IIngredient
        return Optional.of(IngredientManager.getInstance().createIngredient(jeiIngredient.get()));
    }
    
    /**
     * Checks if an ingredient is currently being dragged
     * @return true if an ingredient is being dragged
     */
    public boolean isDragging() {
        // Only return true if we have an ingredient and JEI's dragging flag is set
        return ingredientDragHandler.getDraggedIngredient().isPresent() && 
               ingredientDragHandler.isActuallyDragging();
    }
}