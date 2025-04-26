package com.jeifolders.gui.common;

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
     * Makes it easier to drop items onto folders.
     */
    public static final int DRAG_DROP_HORIZONTAL_MARGIN = 15;
    
    /**
     * Extended margin for drag and drop operations (vertical).
     * Makes it easier to drop items onto folders.
     */
    public static final int DRAG_DROP_VERTICAL_MARGIN = 25;
    
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
}