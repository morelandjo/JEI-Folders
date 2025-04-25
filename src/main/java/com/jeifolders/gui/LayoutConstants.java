package com.jeifolders.gui;

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