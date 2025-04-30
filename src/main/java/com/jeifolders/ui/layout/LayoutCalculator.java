package com.jeifolders.ui.layout;

import com.jeifolders.ui.components.buttons.FolderButtonTextures;
import com.jeifolders.ui.util.LayoutConstants;
import com.jeifolders.util.ModLogger;

import net.minecraft.client.Minecraft;

/**
 * Handles core layout calculations for UI positioning.
 * Responsible for calculating grid positions, dimensions and spacing.
 */
public class LayoutCalculator {
    // Layout constants 
    private static final int PADDING_X = 10;
    private static final int PADDING_Y = 10;
    private static final int ICON_WIDTH = FolderButtonTextures.ICON_WIDTH;
    private static final int ICON_HEIGHT = FolderButtonTextures.ICON_HEIGHT;
    private static final int FOLDER_SPACING_Y = 30;
    private static final int FOLDER_SPACING_X = 2;
    private static final int EXCLUSION_PADDING = 10;

    // Layout calculations
    private int foldersPerRow = 1;
    private int screenWidth = -1;
    private int screenHeight = -1;
    private int maxExclusionWidth = -1;
    
    /**
     * Calculates the maximum number of folders that can fit in a row
     * based on the current screen width
     */
    public void calculateFoldersPerRow() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null) {
            foldersPerRow = 1;
            return;
        }

        // Get current screen dimensions
        screenWidth = minecraft.getWindow().getGuiScaledWidth();
        screenHeight = minecraft.getWindow().getGuiScaledHeight();

        // Calculate available width and determine folders per row
        int guiLeft = LayoutConstants.calculateGuiLeft(screenWidth);
        int availableWidth = Math.max(1, guiLeft - PADDING_X);
        int folderWidth = ICON_WIDTH + (2 * FOLDER_SPACING_X);
        
        foldersPerRow = Math.max(1, availableWidth / folderWidth);
        
        // Calculate and store maxExclusionWidth
        maxExclusionWidth = LayoutConstants.calculateMaxWidthBeforeGui(screenWidth);
        
        ModLogger.debug("Layout calculation: screen width: {}, available width: {}, folders per row: {}", 
                      screenWidth, availableWidth, foldersPerRow);
    }
    
    /**
     * Gets the current number of folders per row
     * 
     * @return The current number of folders that can fit in a row
     */
    public int getFoldersPerRow() {
        return foldersPerRow;
    }
    
    /**
     * Calculates folder button position based on its index in the grid.
     * 
     * @param index The index of the folder (0 is for the add button)
     * @return int[] array with [x, y] coordinates
     */
    public int[] calculateFolderPosition(int index) {
        // Calculate position
        int row = index / foldersPerRow;
        int col = index % foldersPerRow;
        
        int x = PADDING_X + col * (ICON_WIDTH + (2 * FOLDER_SPACING_X));
        int y = PADDING_Y + row * FOLDER_SPACING_Y;
        
        return new int[] {x, y};
    }
    
    /**
     * Calculates the position for the add button
     * 
     * @return int[] array with [x, y] coordinates
     */
    public int[] calculateAddButtonPosition() {
        // Add button is always at position 0
        return calculateFolderPosition(0);
    }
    
    /**
     * Calculates the number of rows needed for the given folder count
     * 
     * @param folderCount Number of folders (excluding the add button)
     * @return Number of rows including the add button
     */
    public int calculateRows(int folderCount) {
        // Calculate how many rows we need, including the Add button
        int effectiveButtonCount = folderCount + 1;
        return (int)Math.ceil((double)effectiveButtonCount / foldersPerRow);
    }
    
    /**
     * Calculate the grid width based on current folders per row
     * 
     * @return Width of the folder grid
     */
    public int calculateGridWidth() {
        return foldersPerRow * (ICON_WIDTH + (2 * FOLDER_SPACING_X));
    }
    
    /**
     * Calculates the Y position for folder names
     * 
     * @param rows The number of rows of buttons
     * @return The Y coordinate for the folder name
     */
    public int calculateFolderNameY(int rows) {
        int nameYOffset = rows * FOLDER_SPACING_Y;
        return PADDING_Y + nameYOffset + 5;
    }
    
    /**
     * Calculates the Y position for the bookmark display
     * 
     * @param folderNameY The Y position of the folder name
     * @return The Y position for the bookmark display
     */
    public int calculateBookmarkDisplayY(int folderNameY) {
        return folderNameY + 20;
    }
    
    /**
     * Calculates the delete button position based on screen dimensions
     * 
     * @param nameY The Y position of the folder name
     * @param gridWidth The width of the folder grid
     * @return int[] array with [x, y] coordinates
     */
    public int[] calculateDeleteButtonPosition(int nameY, int gridWidth) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null) {
            return new int[] {-1, -1};
        }
        
        // Calculate max width for the exclusion zone
        int maxWidth = maxExclusionWidth > 0 ? 
            maxExclusionWidth : LayoutConstants.calculateMaxWidthBeforeGui(minecraft.getWindow().getGuiScaledWidth());
            
        maxWidth = Math.max(40, maxWidth - 10);
        
        // Calculate exclusion width
        int exclusionWidth = Math.min(maxWidth, ICON_WIDTH + (EXCLUSION_PADDING * 2));
        if (foldersPerRow > 1) {
            exclusionWidth = Math.min(maxWidth, 
                                    Math.max(exclusionWidth, gridWidth + (EXCLUSION_PADDING * 2)));
        }
        
        // Position the delete button at the right edge of the exclusion zone
        return new int[] {5 + exclusionWidth - 16 - 5, nameY - 4};
    }
    
    /**
     * Returns the screen width used in calculations
     */
    public int getScreenWidth() {
        return screenWidth;
    }
    
    /**
     * Returns the screen height used in calculations
     */
    public int getScreenHeight() {
        return screenHeight;
    }
    
    /**
     * Returns the maximum exclusion width
     */
    public int getMaxExclusionWidth() {
        return maxExclusionWidth;
    }
    
    /**
     * Returns the padding X value
     */
    public int getPaddingX() {
        return PADDING_X;
    }
    
    /**
     * Returns the padding Y value
     */
    public int getPaddingY() {
        return PADDING_Y;
    }
    
    /**
     * Returns the icon width
     */
    public int getIconWidth() {
        return ICON_WIDTH;
    }
    
    /**
     * Returns the icon height
     */
    public int getIconHeight() {
        return ICON_HEIGHT;
    }
    
    /**
     * Returns the folder spacing Y value
     */
    public int getFolderSpacingY() {
        return FOLDER_SPACING_Y;
    }
    
    /**
     * Returns the folder spacing X value
     */
    public int getFolderSpacingX() {
        return FOLDER_SPACING_X;
    }
    
    /**
     * Returns the exclusion padding value
     */
    public int getExclusionPadding() {
        return EXCLUSION_PADDING;
    }
}