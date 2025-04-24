package com.jeifolders.gui.folderButtons;

import com.jeifolders.util.ModLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;

/**
 * Centralizes all layout calculations for the folder button system.
 * This class handles positioning of folders, buttons, and UI elements.
 */
public class FolderLayoutManager {
    // Layout constants
    private static final int PADDING_X = 10;
    private static final int PADDING_Y = 10;
    private static final int ICON_WIDTH = FolderButtonTextures.ICON_WIDTH;
    private static final int ICON_HEIGHT = FolderButtonTextures.ICON_HEIGHT;
    private static final int FOLDER_SPACING_Y = 30;  
    private static final int FOLDER_SPACING_X = 2;
    private static final int EXCLUSION_PADDING = 10;
    
    // Cache for layout calculations
    private int cachedScreenWidth = -1;
    private int foldersPerRow = 1;
    private long lastCalculationTime = 0;
    private static final int LAYOUT_RECALC_INTERVAL_MS = 1000;
    
    // Calculated positions
    private int calculatedNameY = -1;
    private int calculatedBookmarkDisplayY = -1;
    private int nameYOffset = 0;
    
    // Exclusion zone
    private Rect2i exclusionZone = new Rect2i(0, 0, 0, 0);
    
    // Constructor
    public FolderLayoutManager() {
        calculateInitialLayout();
    }
    
    /**
     * Performs initial layout calculations
     */
    private void calculateInitialLayout() {
        calculateFoldersPerRow();
    }
    
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

        int currentWidth = minecraft.getWindow().getGuiScaledWidth();
        long currentTime = System.currentTimeMillis();

        // Only recalculate if screen width changed or interval passed
        if (currentWidth == cachedScreenWidth &&
            currentTime - lastCalculationTime < LAYOUT_RECALC_INTERVAL_MS) {
            return;
        }

        cachedScreenWidth = currentWidth;
        lastCalculationTime = currentTime;

        // Calculate available width and determine folders per row
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int guiWidth = 176; // Standard GUI width
        int guiLeft = (screenWidth - guiWidth) / 2;

        int availableWidth = Math.max(1, guiLeft - PADDING_X);
        int folderWidth = ICON_WIDTH + (2 * FOLDER_SPACING_X);
        foldersPerRow = Math.max(1, availableWidth / folderWidth);
        
        ModLogger.debug("Layout calculation: screen width: {}, available width: {}, folders per row: {}", 
                      screenWidth, availableWidth, foldersPerRow);
    }
    
    /**
     * Calculates folder button position based on its index in the grid
     * 
     * @param index The index of the folder (0 is for the add button)
     * @return int[] array with [x, y] coordinates
     */
    public int[] calculateFolderPosition(int index) {
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
     * Updates vertical positions for folder names and bookmark display
     * 
     * @param folderCount Number of folders (excluding the add button)
     */
    public void updateLayoutPositions(int folderCount) {
        // Calculate how many rows we need, including the Add button
        int effectiveButtonCount = folderCount + 1;
        int rows = (int)Math.ceil((double)effectiveButtonCount / foldersPerRow);
        
        nameYOffset = rows * FOLDER_SPACING_Y;
        calculatedNameY = PADDING_Y + nameYOffset + 5;
        calculatedBookmarkDisplayY = calculatedNameY + 10;
        
        ModLogger.debug("Updated layout positions: nameY={}, bookmarkDisplayY={}", 
                      calculatedNameY, calculatedBookmarkDisplayY);
    }
    
    /**
     * Calculates the delete button position based on screen dimensions
     * 
     * @return int[] array with [x, y] coordinates
     */
    public int[] calculateDeleteButtonPosition() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null) {
            return new int[] {-1, -1};
        }
        
        // First calculate the exclusion zone width properly
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int guiWidth = 176;
        int guiLeft = (screenWidth - guiWidth) / 2;
        int maxExclusionWidth = Math.max(50, guiLeft - 5);
        maxExclusionWidth = Math.max(40, maxExclusionWidth - 10);
        
        int exclusionWidth = Math.min(maxExclusionWidth, ICON_WIDTH + (EXCLUSION_PADDING * 2));
        if (foldersPerRow > 1) {
            int gridWidth = foldersPerRow * (ICON_WIDTH + (2 * FOLDER_SPACING_X));
            exclusionWidth = Math.min(maxExclusionWidth, 
                                    Math.max(exclusionWidth, gridWidth + (EXCLUSION_PADDING * 2)));
        }
        
        // Position the delete button at the right edge of the exclusion zone
        int deleteX = 5 + exclusionWidth - 16 - 5; // 5 is exclusion zone X, then subtract button width + padding
        int deleteY = calculatedNameY - 4;
        
        return new int[] {deleteX, deleteY};
    }
    
    /**
     * Updates the exclusion zone dimensions based on current state
     * 
     * @param folderCount Number of folders
     * @param foldersVisible Whether folders are currently visible
     * @param hasActiveFolder Whether a folder is currently active
     * @param bookmarkDisplayHeight Height of bookmark display, or 0 if none
     * @return Updated exclusion zone
     */
    public Rect2i updateExclusionZone(int folderCount, boolean foldersVisible, 
                                     boolean hasActiveFolder, int bookmarkDisplayHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null) {
            return new Rect2i(0, 0, 0, 0);
        }

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int guiWidth = 176; // Standard GUI width
        int guiLeft = (screenWidth - guiWidth) / 2;
        
        // Calculate maximum allowed width without constraining too much
        int maxExclusionWidth = Math.max(50, guiLeft);
        
        // Calculate width of the exclusion zone
        int exclusionWidth;
        
        if (foldersVisible && folderCount > 0 && foldersPerRow > 1) {
            // For multiple folders, calculate based on the grid width
            int gridWidth = foldersPerRow * (ICON_WIDTH + (2 * FOLDER_SPACING_X));
            
            // Add padding to the calculated width
            exclusionWidth = Math.min(maxExclusionWidth, 
                                    gridWidth + (2 * EXCLUSION_PADDING));
        } else {
            // For single column or no folders, just use the button width plus padding
            exclusionWidth = Math.min(maxExclusionWidth, 
                                    ICON_WIDTH + (2 * EXCLUSION_PADDING));
        }

        // Calculate the height of the exclusion zone
        int exclusionHeight;
        
        if (foldersVisible && folderCount > 0) {
            // Calculate total number of buttons including the add button
            int totalButtons = folderCount + 1;
            int rows = (int)Math.ceil((double)totalButtons / foldersPerRow);
            
            // Height should include icon height + text height + padding
            int folderWithNameHeight = ICON_HEIGHT + 10 + 5;
            exclusionHeight = rows * FOLDER_SPACING_Y;
            
            // If we have only one row, just use the folder height + name height + padding
            if (rows == 1) {
                exclusionHeight = folderWithNameHeight;
            }
            
            exclusionHeight += 10;
        } else {
            // If no folders visible, just include the add button height
            exclusionHeight = ICON_HEIGHT + (EXCLUSION_PADDING * 2);
        }

        // Extend for bookmark display if active
        if (hasActiveFolder && bookmarkDisplayHeight > 0) {
            int totalHeight = calculatedBookmarkDisplayY - PADDING_Y + bookmarkDisplayHeight + 5;
            exclusionHeight = Math.max(exclusionHeight, totalHeight);
        }

        int exclusionX = 5;
        int exclusionY = Math.max(0, PADDING_Y - EXCLUSION_PADDING);

        exclusionZone = new Rect2i(exclusionX, exclusionY, exclusionWidth, exclusionHeight);
        return exclusionZone;
    }
    
    // Getters and setters
    
    public int getPaddingX() {
        return PADDING_X;
    }
    
    public int getPaddingY() {
        return PADDING_Y;
    }
    
    public int getIconWidth() {
        return ICON_WIDTH;
    }
    
    public int getIconHeight() {
        return ICON_HEIGHT;
    }
    
    public int getFolderSpacingX() {
        return FOLDER_SPACING_X;
    }
    
    public int getFolderSpacingY() {
        return FOLDER_SPACING_Y;
    }
    
    public int getExclusionPadding() {
        return EXCLUSION_PADDING;
    }
    
    public int getFoldersPerRow() {
        return foldersPerRow;
    }
    
    public int getCalculatedNameY() {
        return calculatedNameY;
    }
    
    public int getCalculatedBookmarkDisplayY() {
        return calculatedBookmarkDisplayY;
    }
    
    public Rect2i getExclusionZone() {
        return exclusionZone;
    }
}