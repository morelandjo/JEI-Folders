package com.jeifolders.ui.interaction;

import java.util.List;

/**
 * Interface for a button that can handle ingredient drops
 */
public interface IngredientDropTarget {
    /**
     * Handle an ingredient being dropped on this button
     * @param mouseX The x position of the mouse
     * @param mouseY The y position of the mouse
     * @param ingredient The ingredient being dropped
     * @return true if the drop was handled
     */
    boolean handleIngredientDrop(double mouseX, double mouseY, Object ingredient);
    
    /**
     * Handle an ingredient being dragged over this button
     * @param ingredient The ingredient being dragged
     */
    default void handleIngredientDragRender(Object ingredient) {
        // Optional method for visual feedback during drag operations
    }
    
    /**
     * Get the list of folder row buttons
     */
    List<?> getFolderButtons();
    
    /**
     * Check if the bookmark area is available for drag and drop
     */
    boolean isBookmarkAreaAvailable();
    
    /**
     * Get the bookmark display area
     */
    net.minecraft.client.renderer.Rect2i getBookmarkDisplayArea();
}
