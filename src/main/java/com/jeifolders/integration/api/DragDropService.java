// filepath: /Users/josh/IdeaProjects/JEI-Folders/JEI-Folders/src/main/java/com/jeifolders/integration/api/DragDropService.java
package com.jeifolders.integration.api;

import mezz.jei.api.ingredients.ITypedIngredient;

import java.util.Optional;

/**
 * Service for managing ingredient drag and drop operations.
 * This interface provides methods for tracking and controlling ingredient drag operations.
 */
public interface DragDropService {
    
    /**
     * Gets the currently dragged ingredient as a JEI ITypedIngredient
     * 
     * @return Optional containing the dragged ITypedIngredient, if any
     */
    Optional<ITypedIngredient<?>> getDraggedITypedIngredient();
    
    /**
     * Gets the currently dragged ingredient, if any
     * 
     * @return Optional containing the dragged ingredient, if any
     */
    Optional<IIngredient> getDraggedIngredient();
    
    /**
     * Sets the current dragged ingredient
     * 
     * @param ingredient The ingredient being dragged
     */
    void setDraggedIngredient(ITypedIngredient<?> ingredient);
    
    /**
     * Clears the current dragged ingredient
     */
    void clearDraggedIngredient();
    
    /**
     * Sets whether an ingredient is actually being dragged
     * 
     * @param dragging Whether an ingredient is being dragged
     */
    void setActuallyDragging(boolean dragging);
    
    /**
     * Checks if an ingredient is actually being dragged
     * 
     * @return true if an ingredient is being actively dragged
     */
    boolean isActuallyDragging();
}