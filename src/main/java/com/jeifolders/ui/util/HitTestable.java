package com.jeifolders.ui.util;

/**
 * Interface for UI components that can be hit-tested with mouse coordinates.
 * This standardizes mouse hit detection across all UI components.
 */
public interface HitTestable {
    
    /**
     * Determines if the mouse coordinates are over this component.
     * 
     * @param mouseX X coordinate of the mouse
     * @param mouseY Y coordinate of the mouse
     * @return true if the mouse is over this component
     */
    boolean isMouseOver(double mouseX, double mouseY);
    
    /**
     * Optional method to determine if the mouse coordinates are over this component
     * when considering an extended hit area (useful for drag and drop).
     * Default implementation delegates to standard isMouseOver.
     * 
     * @param mouseX X coordinate of the mouse
     * @param mouseY Y coordinate of the mouse
     * @return true if the mouse is over the extended hit area of this component
     */
    default boolean isMouseOverExtended(double mouseX, double mouseY) {
        return isMouseOver(mouseX, mouseY);
    }
}