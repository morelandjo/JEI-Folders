package com.jeifolders.ui.util;

/**
 * Centralized constants for UI layout calculations.
 */
public final class LayoutConstants {
    
    // Prevent instantiation
    private LayoutConstants() {}
    
    /**
     * Standard Minecraft GUI width in pixels.
     * This is the width of the standard inventory container GUI.
     */
    public static final int STANDARD_GUI_WIDTH = 176;
    
    /**
     * Default safety margin in pixels to use when calculating bounds near a GUI edge.
     */
    public static final int SAFETY_MARGIN = 10;
    
    /**
     * Extended margin for drag and drop operations (horizontal).
     */
    public static final int DRAG_DROP_HORIZONTAL_MARGIN = 15;
    
    /**
     * Extended margin for drag and drop operations (vertical).
     */
    public static final int DRAG_DROP_VERTICAL_MARGIN = 25;

        
    /**
     * Height of the folder name display area in pixels.
     */
    public static final int FOLDER_NAME_HEIGHT = 5;
    
    /**
     * Default height for ingredient grid when a folder is active.
     */
    public static final int INGREDIENT_GRID_HEIGHT = 80;
    
    /**
     * Extra padding to add below the ingredient grid.
     */
    public static final int GRID_BOTTOM_PADDING = 5;
    
    /**
     * Calculates the left position of a standard GUI.
     * 
     * @param screenWidth The current screen width in pixels
     * @return The X coordinate where the standard GUI starts
     */
    public static int calculateGuiLeft(int screenWidth) {
        return (screenWidth - STANDARD_GUI_WIDTH) / 2;
    }
    
    /**
     * Calculates the maximum width available before the standard GUI.
     * 
     * @param screenWidth The current screen width in pixels
     * @return The maximum width available before the GUI
     */
    public static int calculateMaxWidthBeforeGui(int screenWidth) {
        return Math.max(50, calculateGuiLeft(screenWidth) - SAFETY_MARGIN);
    }
    
    /**
     * Calculates the total height needed for the ingredient grid area,
     * including the folder name and padding.
     * 
     * @param displayHeight The height of the bookmark display if available (0 otherwise)
     * @return The total height needed for the ingredient grid area
     */
    public static int calculateIngredientAreaHeight(int displayHeight) {
        // Use the larger of the minimum grid height or the actual display height
        int effectiveGridHeight = Math.max(INGREDIENT_GRID_HEIGHT, displayHeight);
        
        // Add folder name height and bottom padding
        return FOLDER_NAME_HEIGHT + effectiveGridHeight + GRID_BOTTOM_PADDING;
    }
}