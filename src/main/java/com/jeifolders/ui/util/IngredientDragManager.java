package com.jeifolders.ui.util;

import com.jeifolders.util.ModLogger;
import com.jeifolders.integration.IngredientDragHandler;
import com.jeifolders.integration.TypedIngredient;

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
     * @return Optional containing the dragged ingredient, or empty if none
     */
    public Optional<TypedIngredient> getDraggedIngredient() {
        return ingredientDragHandler.getDraggedIngredient();
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